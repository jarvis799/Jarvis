#!/usr/bin/env python3
"""Jarvis AI Background Remover host.

POST /remove-bg  with the image bytes -> transparent PNG.
GET  /health     -> JSON status.

Run it on a computer (or a phone with Termux) and keep it running:
    pip install "rembg[cpu]" pillow
    python remove_bg_server.py --host 127.0.0.1 --port 7000

Nothing is uploaded anywhere else, and the server never invents a result:
if rembg is missing it answers 503 with the real import error.
"""

import argparse
import io
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 20 * 1024 * 1024
ALLOWED = ("image/png", "image/jpeg", "image/webp")

try:
    from rembg import remove as rembg_remove
    IMPORT_ERROR = None
except Exception as exc:
    rembg_remove = None
    IMPORT_ERROR = str(exc)


class Handler(BaseHTTPRequestHandler):
    server_version = "JarvisRemBg/1.0"

    def _send(self, status, body, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _json(self, status, payload):
        self._send(status, json.dumps(payload).encode("utf-8"), "application/json")

    def do_GET(self):
        if self.path.rstrip("/") in ("", "/health"):
            self._json(200, {
                "service": "jarvis-remove-bg",
                "ready": rembg_remove is not None,
                "python": sys.version.split()[0],
                "rembg": "installed" if rembg_remove is not None else "missing",
                "import_error": IMPORT_ERROR,
                "endpoint": "POST /remove-bg",
            })
            return
        self._json(404, {"error": "unknown path", "endpoint": "POST /remove-bg"})

    def do_POST(self):
        if self.path.rstrip("/") != "/remove-bg":
            self._json(404, {"error": "unknown path", "endpoint": "POST /remove-bg"})
            return
        if rembg_remove is None:
            self._json(503, {"error": "rembg is not installed on this host", "detail": IMPORT_ERROR})
            return
        content_type = (self.headers.get("Content-Type") or "").split(";")[0].strip().lower()
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            self._json(400, {"error": "empty body"})
            return
        if length > MAX_BODY:
            self._json(413, {"error": "image larger than 20 MB"})
            return
        if content_type not in ALLOWED:
            self._json(415, {"error": "unsupported type", "allowed": list(ALLOWED)})
            return
        data = self.rfile.read(length)
        try:
            result = rembg_remove(data)
        except Exception as exc:
            self._json(500, {"error": "removal failed", "detail": str(exc)})
            return
        if not isinstance(result, (bytes, bytearray)):
            self._json(500, {"error": "rembg returned no image bytes"})
            return
        self._send(200, bytes(result), "image/png")

    def log_message(self, fmt, *args):
        sys.stderr.write("[jarvis-host] " + fmt % args + "
")


def main():
    parser = argparse.ArgumentParser(description="Jarvis background removal host")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7000)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    state = "rembg ready" if rembg_remove is not None else "rembg MISSING: " + str(IMPORT_ERROR)
    print("Jarvis host listening on http://" + args.host + ":" + str(args.port) + " (" + state + ")")
    print("In Jarvis: paste that URL, tap Test connection, then Remove background.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()


if __name__ == "__main__":
    main()
