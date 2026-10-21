# Jarvis AI Background Remover host

The phone app calls **`POST /remove-bg`** on this host and expects a transparent PNG back.

## 1. Install (once, on the machine that will do the work)

```bash
python -m venv .venv && . .venv/bin/activate
pip install "rembg[cpu]" pillow
```

## 2. Run

```bash
python remove_bg_server.py --host 127.0.0.1 --port 7000
```

* `127.0.0.1` = this same machine. From the phone that only works when the host runs **on the phone**.
* For a host on your Wi-Fi/LAN use `--host 0.0.0.0` and give Jarvis the machine's address. The app refuses plain `http` for non-loopback addresses, so put it behind **https** (for example a reverse proxy with a TLS certificate).
* `http://127.0.0.1:7000` is the only plain-http address Jarvis accepts.

## 3. Check it

```bash
curl -s http://127.0.0.1:7000/health
curl -s -X POST --data-binary @photo.jpg -H "Content-Type: image/jpeg" http://127.0.0.1:7000/remove-bg -o out.png
```

`out.png` must open with a transparent background. If `/health` says `"ready": false`, rembg did not import - the real error is in that JSON, nothing is faked.

## Model files

`rembg[cpu]` downloads its model (`u2net`, about 176 MB) into `~/.u2net` the first time it runs. Keep that folder to stay offline afterwards.

## Running on the phone itself

The in-app terminal is an Alpine Linux runtime behind `proot`. `pip install rembg onnxruntime` there usually fails because there are no musl/aarch64 wheels. Jarvis tells you this from a real probe instead of pretending it works - run the host on a computer, or use `127.0.0.1` when the server really does run on the phone.
