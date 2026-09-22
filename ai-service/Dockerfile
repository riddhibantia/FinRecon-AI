# P12: ai-service image (FastAPI on :8000). Model artifacts and DATABASE_URL
# stay outside the image: mount/provide them at runtime. Not built on
# machines without Docker; CI builds every image.
FROM python:3.12-slim
WORKDIR /app
COPY ai-service/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m appuser && chown -R appuser:appuser /app
COPY ai-service/app.py ./app.py
COPY ai-service/classifier ./classifier
COPY ai-service/rag ./rag
COPY ai-service/agent ./agent
COPY ai-service/tools ./tools
COPY ai-service/data ./data
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD curl -f http://localhost:8000/health || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--workers", "1"]
