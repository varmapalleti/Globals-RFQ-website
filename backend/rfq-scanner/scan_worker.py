"""Poll RFQ Service's private scan queue and report ClamAV results."""

from __future__ import annotations

import hashlib
import json
import logging
import os
import re
import socket
import struct
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


LOG = logging.getLogger("rfq.attachment-scanner")
CHUNK_SIZE = 64 * 1024
MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024
ATTACHMENT_ID = re.compile(r"^[0-9a-fA-F-]{36}$")
SHA256 = re.compile(r"^[0-9a-fA-F]{64}$")


def env_int(name: str, default: int, minimum: int = 1) -> int:
    try:
        return max(minimum, int(os.environ.get(name, str(default))))
    except ValueError:
        return default


BASE_URL = os.environ.get("RFQ_SCANNER_BASE_URL", "http://localhost:8082").rstrip("/")
TOKEN = os.environ.get("RFQ_ATTACHMENT_SCANNER_TOKEN", "")
CLAMAV_HOST = os.environ.get("CLAMAV_HOST", "127.0.0.1")
CLAMAV_PORT = env_int("CLAMAV_PORT", 3310)
POLL_INTERVAL = env_int("SCAN_POLL_INTERVAL_SECONDS", 5)
SCANNER_NAME = "ClamAV"


def request(path: str, method: str = "GET", body: bytes | None = None) -> tuple[bytes, str]:
    if not TOKEN or len(TOKEN) < 32:
        raise RuntimeError("RFQ_ATTACHMENT_SCANNER_TOKEN must contain at least 32 characters")
    req = Request(
        BASE_URL + path,
        data=body,
        method=method,
        headers={
            "X-Attachment-Scanner-Token": TOKEN,
            "Accept": "application/json, application/octet-stream",
            **({"Content-Type": "application/json"} if body is not None else {}),
        },
    )
    with urlopen(req, timeout=30) as response:
        return response.read(), response.headers.get("Content-Type", "")


def pending_attachments() -> list[dict]:
    payload, _ = request("/internal/attachment-scanner/pending")
    document = json.loads(payload)
    entries = document.get("data")
    if not isinstance(entries, list):
        raise ValueError("RFQ Service returned an invalid pending-scan response")
    return entries


def stage_attachment(attachment: dict) -> str:
    attachment_id = attachment.get("id", "")
    sha256 = attachment.get("sha256", "")
    size = attachment.get("sizeBytes")
    download_path = attachment.get("downloadPath", "")
    if not ATTACHMENT_ID.fullmatch(attachment_id) or not SHA256.fullmatch(sha256):
        raise ValueError("Pending attachment has an invalid identifier or SHA-256 digest")
    if not isinstance(size, int) or size < 0 or size > MAX_ATTACHMENT_BYTES:
        raise ValueError("Pending attachment size is outside the scanner's configured limit")
    expected_path = f"/internal/attachment-scanner/attachments/{attachment_id}/content"
    if download_path != expected_path:
        raise ValueError("Pending attachment download path did not match the expected private endpoint")

    digest = hashlib.sha256()
    size_read = 0
    staged_path = None
    try:
        with tempfile.NamedTemporaryFile(prefix="rfq-scan-", delete=False) as staged:
            staged_path = staged.name
            req = Request(BASE_URL + expected_path, headers={"X-Attachment-Scanner-Token": TOKEN})
            with urlopen(req, timeout=60) as response:
                while chunk := response.read(CHUNK_SIZE):
                    size_read += len(chunk)
                    if size_read > MAX_ATTACHMENT_BYTES:
                        raise ValueError("Attachment exceeded the scanner's configured size limit")
                    digest.update(chunk)
                    staged.write(chunk)
        if size_read != size or digest.hexdigest().lower() != sha256.lower():
            raise ValueError("Downloaded attachment size or SHA-256 did not match its stored metadata")
        return staged_path
    except Exception:
        if staged_path:
            try:
                os.unlink(staged_path)
            except FileNotFoundError:
                pass
        raise


def clamav_scan(path: str) -> tuple[str, str]:
    with socket.create_connection((CLAMAV_HOST, CLAMAV_PORT), timeout=30) as sock:
        sock.settimeout(120)
        sock.sendall(b"zINSTREAM\0")
        with open(path, "rb") as staged:
            while chunk := staged.read(CHUNK_SIZE):
                sock.sendall(struct.pack("!I", len(chunk)))
                sock.sendall(chunk)
        sock.sendall(struct.pack("!I", 0))
        result = bytearray()
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            result.extend(chunk)
            if b"\0" in chunk or b"\n" in chunk:
                break
    response = bytes(result).decode("utf-8", errors="replace").strip("\0\r\n ")
    if response.endswith(": OK"):
        return "CLEAN", response
    if response.endswith(" FOUND"):
        return "INFECTED", response
    raise RuntimeError(f"ClamAV returned an inconclusive scan response: {response[:200]}")


def report(attachment_id: str, state: str, signature: str) -> None:
    scan_reference = f"clamav-{hashlib.sha256(signature.encode()).hexdigest()[:24]}"
    body = json.dumps(
        {"state": state, "scannerName": SCANNER_NAME, "scanReference": scan_reference}
    ).encode()
    request(
        f"/internal/attachment-scanner/attachments/{attachment_id}/result",
        method="POST",
        body=body,
    )


def scan_one(attachment: dict) -> None:
    attachment_id = attachment.get("id", "")
    staged_path = stage_attachment(attachment)
    try:
        state, signature = clamav_scan(staged_path)
        report(attachment_id, state, signature)
        LOG.info("Scan complete attachment=%s result=%s", attachment_id, state)
    finally:
        try:
            os.unlink(staged_path)
        except FileNotFoundError:
            pass


def main() -> None:
    if not TOKEN or len(TOKEN) < 32:
        raise SystemExit("RFQ_ATTACHMENT_SCANNER_TOKEN must contain at least 32 characters")
    LOG.info("Scanner worker started; RFQ Service=%s, ClamAV=%s:%s", BASE_URL, CLAMAV_HOST, CLAMAV_PORT)
    while True:
        try:
            queue = pending_attachments()
            for attachment in queue:
                try:
                    scan_one(attachment)
                except (HTTPError, URLError, OSError, ValueError, RuntimeError) as error:
                    LOG.warning("Attachment scan deferred id=%s reason=%s", attachment.get("id"), error)
        except (HTTPError, URLError, OSError, ValueError, json.JSONDecodeError, RuntimeError) as error:
            LOG.warning("Could not poll RFQ scan queue: %s", error)
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    main()
