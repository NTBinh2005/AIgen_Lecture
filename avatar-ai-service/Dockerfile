FROM python:3.12-slim

WORKDIR /app

# The default container uses the lightweight animated-3D fallback. A host GPU
# installation can opt into SadTalker through SADTALKER_PYTHON.
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py ./app.py
COPY assets ./assets

RUN mkdir -p outputs temp

EXPOSE 5000

ENV PORT=5000
ENV PYTHONUNBUFFERED=1
ENV AVATAR_ENGINE=fallback

CMD ["python", "app.py"]
