# Voice service

Speech in and speech out for CliniQ, in **Hindi, Tamil and English**. Two
AI4Bharat models, both MIT licensed, both running entirely on this machine:

| Stage | Model | Size | Languages |
|---|---|---|---|
| Speech to text | [IndicConformer](https://github.com/AI4Bharat/IndicConformerASR) | 600M | 22 Indian languages + English |
| Text to speech | [IndicF5](https://huggingface.co/ai4bharat/IndicF5) | 0.4B | 11 Indian languages |

IndicF5 does not cover English, so English replies are spoken by the browser
instead — its English voices are good, instant and free, and paying a GPU to do
worse would be waste.

## Hugging Face access, first

**Both models are gated.** Gating is automatic — there is no approval queue —
but you must be signed in and have clicked through once:

1. Create a free account at <https://huggingface.co/join>.
2. Open each model page and accept the terms:
   - <https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual>
   - <https://huggingface.co/ai4bharat/IndicF5>
3. Create a **read** token at <https://huggingface.co/settings/tokens>.
4. Sign in locally, once:

   ```bash
   uv run huggingface-cli login
   ```

   Paste the token when prompted. It is stored outside the repository and is
   never committed.

Without this the service exits at startup with `gated repo` / 401.

## Why it is a separate service

Spring Boot cannot host a PyTorch model, so the model lives here and the
backend calls it over HTTP. Keeping it separate also means the API starts in
seconds whether or not the model is loaded — voice is an optional feature, and
the rest of the product must not wait on a 2.5 GB download.

## Why self-hosted rather than a speech API

A patient describing a symptom is health information. Self-hosting means the
audio never leaves the clinic's own hardware, which is the reason this was
chosen over a hosted speech API even though a hosted one would be less work.

## Setup, once

Requires Python 3.11 and ffmpeg. Python 3.14 will not work — PyTorch does not
publish wheels for it.

```bash
cd asr-service

# Python 3.11 in a local virtualenv
uv python install 3.11
uv venv --python 3.11

# PyTorch from its own index, so the CUDA build is used rather than the
# CPU-only wheel on PyPI. This is the slow step: about 2.5 GB.
uv pip install torch torchaudio --index-url https://download.pytorch.org/whl/cu124

# Everything else
uv pip install -r requirements.txt
```

ffmpeg, for decoding what the browser records:

```bash
winget install Gyan.FFmpeg          # Windows
sudo apt install ffmpeg             # Debian/Ubuntu
```

The model itself downloads on first run, to the Hugging Face cache. Expect a
few minutes and about 2.5 GB. It is cached after that.

## Running it

```bash
cd asr-service
uv run uvicorn app:app --port 8001
```

Wait for `model ready in …s` before sending anything. On a CPU-only machine the
first transcription is slow but it does work; a GPU is a convenience, not a
requirement.

Check it is up:

```bash
curl http://localhost:8001/health
```

`status` is `LOADING` until the model finishes, then `UP`.

## Transcribing something

```bash
curl -X POST http://localhost:8001/transcribe \
  -F "audio=@sample.wav" \
  -F "language=ta"
```

Any format ffmpeg can read is accepted — the browser sends WebM/Opus — and it
is converted to the 16 kHz mono the model requires.

```json
{
  "text": "நாளைக்கு அப்பாயிண்ட்மென்ட் வேணும்",
  "language": "ta",
  "decoder": "rnnt",
  "durationSeconds": 2.4,
  "elapsedMs": 812
}
```

## Turning it on in the backend

The backend ignores this service unless told otherwise:

```bash
CLINIC_SPEECH_ENABLED=true ./mvnw spring-boot:run
```

Without that flag the `/api/v1/voice/**` endpoints do not exist at all, which
is deliberate: an endpoint that is present but always fails is worse than one
that is honestly absent.

## Decoders

The checkpoint ships two. `rnnt` is the default and the more accurate;
`ctc` is faster and useful for comparing output quality:

```bash
curl -X POST http://localhost:8001/transcribe \
  -F "audio=@sample.wav" -F "language=ta" -F "decoder=ctc"
```

## Limits

- **30 seconds** per recording, and 10 MB. A booking question is a sentence;
  anything longer is a recorder someone forgot to stop.
- **Three languages** are enabled (`hi`, `ta`, `en`). The recogniser supports
  22, but only these three have been tested here, and claiming the rest without
  testing them would be dishonest.
- **English is understood but not spoken here** — the browser speaks it.
- **Spoken replies are capped at 600 characters.** A clinic answer is a few
  sentences; anything longer is a bug upstream.
- **No speaker separation and no punctuation.** The model returns a plain
  stream of words.
