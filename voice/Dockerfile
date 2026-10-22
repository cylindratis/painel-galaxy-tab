FROM python:3.11-slim-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends espeak-ng libgomp1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py .
ENV HF_HOME=/models/huggingface OWW_DIR=/models/wake WHISPER_MODEL=small PYTHONUNBUFFERED=1
EXPOSE 8765
CMD ["python", "server.py"]
