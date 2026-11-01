# The ML service with its trained models baked in, so it starts without
# downloading datasets or retraining. The versions in requirements.txt are the
# ones the committed models were saved with; do not loosen them (see README).
FROM python:3.12-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TZ=Asia/Kolkata

# pytest and httpx in requirements.txt are only for the tests; the service
# itself needs the rest. Installing the file as it is keeps one source of truth.
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app.py conditions.py features.py ./
COPY models ./models

# Render supplies PORT (10000 by default); locally it falls back to 8002.
EXPOSE 10000
CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8002}"]
