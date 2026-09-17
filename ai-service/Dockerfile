# P12: ai-service image (FastAPI on :8000). Model artifacts and DATABASE_URL
# stay outside the image: mount/provide them at runtime. Not built on
# machines without Docker; CI builds every image.
FROM python:3.12-slim
WORKDIR /app
COPY ai-service/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY ai-service/app.py ./app.py
COPY ai-service/classifier ./classifier
COPY ai-service/rag ./rag
COPY ai-service/agent ./agent
COPY ai-service/tools ./tools
COPY ai-service/data ./data
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
