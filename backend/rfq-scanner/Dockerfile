FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /app
COPY scan_worker.py ./scan_worker.py
USER 10001:10001
CMD ["python", "-u", "scan_worker.py"]
