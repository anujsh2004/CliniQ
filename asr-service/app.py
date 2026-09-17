"""
Tamil speech recognition for CliniQ.

Spring Boot cannot host a PyTorch model, so the model lives here and the
backend calls this service over HTTP. Keeping it separate also means the API
starts in seconds whether or not the model is loaded, which matters because the
model takes a while to warm up and the rest of the product must not wait for it.

The model is AI4Bharat's IndicConformer 600M multilingual checkpoint, MIT
licensed and running entirely on this machine. Nothing a patient says leaves
the building, which is the reason for choosing a self-hosted model over a
hosted speech API for clinical audio.

Run it with:

    uv run uvicorn app:app --port 8001
"""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
import tempfile
import time
from contextlib import asynccontextmanager
from typing import Final

import torch
import torchaudio
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("asr")

MODEL_ID: Final = os.environ.get("ASR_MODEL_ID", "ai4bharat/indic-conformer-600m-multilingual")
TARGET_RATE: Final = 16_000

# The languages this service will accept. The checkpoint supports 22; the
# clinic has only been tested in these, and claiming the rest without testing
# them would be dishonest.
SUPPORTED: Final = {"ta": "Tamil", "hi": "Hindi", "kn": "Kannada", "en": "English"}

# RNNT is the more accurate of the two decoders the checkpoint ships and is
# what the model card recommends; CTC is kept available because it is faster
# and useful when comparing output quality.
DEFAULT_DECODER: Final = "rnnt"

# Longer than this and it is not a booking request. The cap protects the GPU
# from a stuck recorder uploading minutes of silence.
MAX_SECONDS: Final = 30.0
MAX_UPLOAD_BYTES: Final = 10 * 1024 * 1024


class Transcript(BaseModel):
    text: str
    language: str
    decoder: str
    durationSeconds: float
    elapsedMs: int


class Health(BaseModel):
    status: str
    model: str
    device: str
    modelLoaded: bool
    languages: dict[str, str]


class _Engine:
    """Holds the model. Loaded once, reused for every request."""

    def __init__(self) -> None:
        self.model = None
        self.device = "cuda" if torch.cuda.is_available() else "cpu"

    def load(self) -> None:
        from transformers import AutoModel

        started = time.perf_counter()
        log.info("loading %s onto %s", MODEL_ID, self.device)
        model = AutoModel.from_pretrained(MODEL_ID, trust_remote_code=True)
        model.eval()
        # .to() is deliberately guarded: the custom model class does not always
        # implement it, and a CPU fallback is far better than failing to start.
        try:
            model = model.to(self.device)
        except (AttributeError, NotImplementedError, RuntimeError) as exc:
            log.warning("could not move model to %s (%s); staying on CPU", self.device, exc)
            self.device = "cpu"
        self.model = model
        log.info("model ready in %.1fs", time.perf_counter() - started)

    def transcribe(self, wav: torch.Tensor, language: str, decoder: str) -> str:
        if self.model is None:
            raise RuntimeError("model is not loaded")
        with torch.no_grad():
            result = self.model(wav, language, decoder)
        # The checkpoint returns a bare string for a single utterance and a
        # one-item list in some versions; accept both rather than pinning to a
        # version's incidental shape.
        if isinstance(result, (list, tuple)):
            result = result[0] if result else ""
        return str(result).strip()


engine = _Engine()


@asynccontextmanager
async def lifespan(_: FastAPI):
    engine.load()
    yield


app = FastAPI(title="CliniQ speech recognition", version="1.0.0", lifespan=lifespan)


def _to_wav_16k_mono(raw: bytes, suffix: str) -> tuple[torch.Tensor, float]:
    """
    Decodes an upload to 16 kHz mono, which is the only format the model accepts.

    Browsers record WebM/Opus, which torchaudio cannot read, so ffmpeg does the
    decoding and resampling in one pass.
    """
    if shutil.which("ffmpeg") is None:
        raise HTTPException(
            status_code=503,
            detail="ffmpeg is not installed, so uploaded audio cannot be decoded.",
        )

    with tempfile.TemporaryDirectory() as work:
        source = os.path.join(work, f"upload{suffix}")
        target = os.path.join(work, "ready.wav")
        with open(source, "wb") as handle:
            handle.write(raw)

        completed = subprocess.run(
            ["ffmpeg", "-nostdin", "-loglevel", "error", "-i", source,
             "-ac", "1", "-ar", str(TARGET_RATE), "-f", "wav", target],
            capture_output=True,
            timeout=60,
        )
        if completed.returncode != 0 or not os.path.exists(target):
            detail = completed.stderr.decode("utf-8", "replace")[:400]
            raise HTTPException(status_code=422, detail=f"Could not decode the audio: {detail}")

        wav, rate = torchaudio.load(target)

    if wav.shape[0] > 1:
        wav = torch.mean(wav, dim=0, keepdim=True)
    if rate != TARGET_RATE:  # ffmpeg already resampled; belt and braces
        wav = torchaudio.transforms.Resample(orig_freq=rate, new_freq=TARGET_RATE)(wav)

    return wav, wav.shape[1] / TARGET_RATE


@app.get("/health", response_model=Health)
def health() -> Health:
    return Health(
        status="UP" if engine.model is not None else "LOADING",
        model=MODEL_ID,
        device=engine.device,
        modelLoaded=engine.model is not None,
        languages=SUPPORTED,
    )


@app.post("/transcribe", response_model=Transcript)
async def transcribe(
    audio: UploadFile = File(...),
    language: str = Form("ta"),
    decoder: str = Form(DEFAULT_DECODER),
) -> Transcript:
    if language not in SUPPORTED:
        raise HTTPException(
            status_code=422,
            detail=f"Language '{language}' is not enabled. Supported: {', '.join(SUPPORTED)}.",
        )
    if decoder not in ("rnnt", "ctc"):
        raise HTTPException(status_code=422, detail="Decoder must be 'rnnt' or 'ctc'.")
    if engine.model is None:
        raise HTTPException(status_code=503, detail="The speech model is still loading.")

    raw = await audio.read()
    if not raw:
        raise HTTPException(status_code=422, detail="The audio file is empty.")
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="That recording is too large.")

    suffix = os.path.splitext(audio.filename or "")[1] or ".webm"
    wav, seconds = _to_wav_16k_mono(raw, suffix)

    if seconds > MAX_SECONDS:
        raise HTTPException(
            status_code=422,
            detail=f"Recordings are limited to {MAX_SECONDS:.0f} seconds.",
        )

    started = time.perf_counter()
    text = engine.transcribe(wav, language, decoder)
    elapsed = int((time.perf_counter() - started) * 1000)
    log.info("transcribed %.1fs of %s in %dms: %r", seconds, language, elapsed, text)

    return Transcript(
        text=text,
        language=language,
        decoder=decoder,
        durationSeconds=round(seconds, 2),
        elapsedMs=elapsed,
    )
