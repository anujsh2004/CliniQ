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

import io
import numpy as np
import soundfile as sf
import torch
import torchaudio
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("asr")

MODEL_ID: Final = os.environ.get("ASR_MODEL_ID", "ai4bharat/indic-conformer-600m-multilingual")
TARGET_RATE: Final = 16_000

# The languages this service will recognise.
#
# English is deliberately absent. IndicConformer covers 22 *Indian* languages
# and English is not among them — asking it for "en" fails inside the model,
# which surfaced as a 500 rather than as anything informative. Patients can
# still type English; the microphone is Hindi and Tamil only.
#
# The checkpoint supports twenty more than this. Only these two have been
# tested here, and claiming the rest without testing them would be dishonest.
SUPPORTED: Final = {"hi": "Hindi", "ta": "Tamil", "en": "English"}

# English is recognised by Whisper, not by IndicConformer.
#
# IndicConformer covers 22 Indian languages and English is not among them —
# asking it for "en" fails inside the model. Whisper is MIT licensed, ungated,
# and strong on English, so the two recognisers sit side by side and the
# language decides which one runs.
WHISPER_MODEL_ID: Final = os.environ.get("WHISPER_MODEL_ID", "openai/whisper-small")
WHISPER_LANGUAGES: Final = {"en"}

# Text to speech is Meta's MMS-TTS, one small VITS model per language.
#
# Chosen for a reason worth recording, because it is a temporary choice:
#
#   * AI4Bharat IndicF5 (MIT) is the best of the three, but its custom model
#     code predates PyTorch 2.14's meta-device initialisation and fails to load
#     on every transformers version tried.
#   * AI4Bharat Indic Parler-TTS (Apache 2.0) is the right licence, but it
#     generates audio autoregressively and needs a GPU. On this CPU it did not
#     finish loading in twenty minutes.
#   * MMS-TTS generates a whole waveform in one pass, so it runs on a CPU in
#     about a second, and it loads without a fight.
#
# The catch, and it is a real one: MMS-TTS is CC-BY-NC 4.0, meaning
# non-commercial use only. It is fine for a demo and must be replaced before
# CliniQ is sold. Tracked as decision D28 in docs/open-decisions.md.
TTS_MODELS: Final = {
    "hi": os.environ.get("TTS_MODEL_HI", "facebook/mms-tts-hin"),
    "ta": os.environ.get("TTS_MODEL_TA", "facebook/mms-tts-tam"),
    "en": os.environ.get("TTS_MODEL_EN", "facebook/mms-tts-eng"),
}

TTS_LANGUAGES: Final = set(TTS_MODELS)

# A spoken clinic answer is a few sentences. The cap stops a bug upstream from
# asking the GPU to voice an entire page.
MAX_TTS_CHARS: Final = 600

# RNNT is the more accurate of the two decoders the checkpoint ships and is
# what the model card recommends; CTC is kept available because it is faster
# and useful when comparing output quality.
DEFAULT_DECODER: Final = "rnnt"

# Longer than this and it is not a booking request. The cap protects the GPU
# from a stuck recorder uploading minutes of silence.
# Below this peak amplitude the clip is silence rather than quiet speech.
# Normal speech through a laptop microphone peaks well above 0.01.
SILENCE_PEAK: Final = 0.005

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
    englishModel: str
    englishLoaded: bool
    ttsModels: dict[str, str]
    ttsLoaded: list[str]
    ttsFailed: dict[str, str]


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


class _English:
    """
    English speech recognition, via Whisper.

    Separate from the Indic recogniser rather than folded into it, because the
    two take completely different inputs: Whisper wants log-mel features from a
    processor, IndicConformer wants the raw waveform and a language id. Loaded
    lazily so a clinic that never offers English never pays the download.
    """

    def __init__(self) -> None:
        self.model = None
        self.processor = None
        self.error: str | None = None

    def load(self) -> None:
        try:
            from transformers import WhisperForConditionalGeneration, WhisperProcessor

            started = time.perf_counter()
            log.info("loading %s for English", WHISPER_MODEL_ID)
            self.processor = WhisperProcessor.from_pretrained(WHISPER_MODEL_ID)
            model = WhisperForConditionalGeneration.from_pretrained(WHISPER_MODEL_ID)
            model.eval()
            self.model = model
            log.info("English recogniser ready in %.1fs", time.perf_counter() - started)
        except Exception as exc:  # noqa: BLE001 - English failing is not fatal
            self.error = str(exc)
            log.warning("English recognition unavailable: %s", exc)

    def transcribe(self, wav: torch.Tensor) -> str:
        if self.model is None or self.processor is None:
            raise RuntimeError("the English recogniser is not loaded")

        # Whisper is trained on 16 kHz mono, which is what the caller already
        # produced, and wants a flat array rather than a channel dimension.
        features = self.processor(
            wav.squeeze().numpy(), sampling_rate=TARGET_RATE, return_tensors="pt"
        ).input_features

        with torch.no_grad():
            # The language is pinned. Left to itself Whisper will happily
            # detect Hindi and transcribe in Devanagari, which is the other
            # recogniser's job and would make the reply language unpredictable.
            ids = self.model.generate(features, language="en", task="transcribe")

        return self.processor.batch_decode(ids, skip_special_tokens=True)[0].strip()


class _Voice:
    """
    Holds the text-to-speech model.

    Loaded separately from the recogniser and allowed to fail on its own: a
    clinic that can hear a patient but not answer aloud is still useful, so a
    TTS that will not load degrades to on-screen text rather than taking the
    whole service down.
    """

    def __init__(self) -> None:
        # One model per language rather than one polyglot model, because that
        # is how MMS ships. Each is small enough (~140 MB) that holding both
        # costs less than the recogniser alone.
        self.models: dict[str, object] = {}
        self.tokenizers: dict[str, object] = {}
        self.errors: dict[str, str] = {}

    def load(self) -> None:
        from transformers import AutoTokenizer, VitsModel

        for language, repo in TTS_MODELS.items():
            try:
                started = time.perf_counter()
                log.info("loading %s for %s", repo, language)
                model = VitsModel.from_pretrained(repo)
                model.eval()
                self.models[language] = model
                self.tokenizers[language] = AutoTokenizer.from_pretrained(repo)
                log.info("%s voice ready in %.1fs", language, time.perf_counter() - started)
            except Exception as exc:  # noqa: BLE001 - one language failing is not fatal
                # Deliberately per-language: Tamil failing must not cost a
                # Hindi speaker their audio.
                self.errors[language] = str(exc)
                log.warning("%s text to speech unavailable: %s", language, exc)

    def loaded(self, language: str) -> bool:
        return language in self.models

    def sample_rate(self, language: str) -> int:
        return self.models[language].config.sampling_rate

    def speak(self, text: str, language: str) -> bytes:
        model = self.models.get(language)
        tokenizer = self.tokenizers.get(language)
        if model is None or tokenizer is None:
            raise RuntimeError(f"no voice model loaded for {language}")

        inputs = tokenizer(text, return_tensors="pt")
        input_ids = inputs["input_ids"]

        # MMS tokenizers are per-language and character-based: anything outside
        # that language's script is silently dropped, so text in the wrong
        # script tokenises to nothing at all. Caught here because the failure
        # is otherwise a baffling tensor error deep inside the model, and
        # because the commonest cause is a caller sending the wrong language.
        if input_ids.shape[-1] == 0:
            raise ValueError(
                f"none of that text is writable in {language}. "
                "Check the language matches the script."
            )
        log.info("speaking %d tokens of %s", input_ids.shape[-1], language)

        with torch.no_grad():
            waveform = model(input_ids=input_ids,
                             attention_mask=inputs["attention_mask"]).waveform
        audio = waveform.squeeze().cpu().numpy()
        # The model returns int16 in some builds and float in others. Normalise
        # rather than trusting one shape, because the wrong assumption here is
        # silent: it produces audible noise instead of an error.
        if audio.dtype == np.int16:
            audio = audio.astype(np.float32) / 32768.0
        audio = audio.astype(np.float32)

        buffer = io.BytesIO()
        sf.write(buffer, audio, samplerate=model.config.sampling_rate, format="WAV")
        return buffer.getvalue()


engine = _Engine()
english = _English()
voice = _Voice()


@asynccontextmanager
async def lifespan(_: FastAPI):
    engine.load()
    english.load()
    voice.load()
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

        # Read with soundfile, not torchaudio.load: torchaudio now delegates
        # decoding to torchcodec, which needs FFmpeg's shared libraries on the
        # DLL search path and fails on Windows with only the ffmpeg executable
        # installed. soundfile bundles libsndfile and ffmpeg has already done
        # the hard part, so this reads a plain wav with no external deps.
        samples, rate = sf.read(target, dtype="float32", always_2d=True)

    # soundfile gives (frames, channels); torch wants (channels, frames).
    wav = torch.from_numpy(samples.T)

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
        englishModel=WHISPER_MODEL_ID,
        englishLoaded=english.model is not None,
        ttsModels=TTS_MODELS,
        ttsLoaded=sorted(voice.models),
        ttsFailed=voice.errors,
    )


@app.post("/speak")
def speak(text: str = Form(...), language: str = Form("hi")) -> Response:
    """
    Voices a reply, returning a wav.

    <p>English is deliberately not handled here. IndicF5 does not cover it, and
    the browser's own English voices are good and free — so the caller speaks
    English itself rather than this returning something worse.
    """
    if language not in TTS_LANGUAGES:
        raise HTTPException(
            status_code=422,
            detail=f"'{language}' is not spoken here. Available: {', '.join(sorted(TTS_LANGUAGES))}.",
        )
    if not text.strip():
        raise HTTPException(status_code=422, detail="There is nothing to say.")
    if len(text) > MAX_TTS_CHARS:
        raise HTTPException(
            status_code=422, detail=f"Replies are limited to {MAX_TTS_CHARS} characters."
        )
    if not voice.loaded(language):
        raise HTTPException(
            status_code=503,
            detail=voice.errors.get(language, "The voice model is still loading."),
        )

    started = time.perf_counter()
    try:
        wav = voice.speak(text.strip(), language)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    elapsed = int((time.perf_counter() - started) * 1000)
    log.info("voiced %d characters of %s in %dms", len(text), language, elapsed)

    return Response(
        content=wav,
        media_type="audio/wav",
        # Read by the browser so the demo can show real numbers rather than a
        # claim about how fast this is.
        headers={"X-Elapsed-Ms": str(elapsed)},
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

    # Peak level, logged on every request. Without it, an empty transcript is
    # ambiguous between "the microphone recorded silence" and "the model could
    # not read this audio" — two problems with nothing in common, and guessing
    # between them wastes a lot of time.
    peak = float(np.abs(wav.numpy()).max()) if wav.numel() else 0.0

    started = time.perf_counter()
    if language in WHISPER_LANGUAGES:
        if english.model is None:
            raise HTTPException(
                status_code=503,
                detail=english.error or "The English recogniser is still loading.",
            )
        text = english.transcribe(wav)
    else:
        text = engine.transcribe(wav, language, decoder)
    elapsed = int((time.perf_counter() - started) * 1000)
    log.info("transcribed %.1fs of %s in %dms (peak %.4f): %r",
             seconds, language, elapsed, peak, text)

    if not text and peak < SILENCE_PEAK:
        # Near-silent input. Said plainly, because the fix is the patient's
        # microphone rather than anything on the server.
        raise HTTPException(
            status_code=422,
            detail=f"That recording is silent (peak level {peak:.4f}). "
                   "Check the microphone your browser is using.",
        )

    return Transcript(
        text=text,
        language=language,
        decoder=decoder,
        durationSeconds=round(seconds, 2),
        elapsedMs=elapsed,
    )
