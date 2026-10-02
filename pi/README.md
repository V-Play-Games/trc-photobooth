# TRC Photo Booth — Pi Camera & WebSocket Streaming Server

The core streaming server for the TRC Photo Booth. Runs on the Raspberry Pi 4 (or on any development PC using built-in synthetic mock camera simulation) and provides low-latency live JPEG frame streaming over WebSocket.

---

## Features

- **Flexible Camera Backends**:
  - `picamera2`: Native hardware-accelerated capture on Raspberry Pi with official Camera Module.
  - `MockCamera` (Laptop Webcam): Live capture from your physical laptop webcam via OpenCV (`cv2.VideoCapture`), automatically mirrored for natural photo booth framing.
  - `MockCamera` (Synthetic Fallback): Generates an animated test feed with live timestamp, frame counter, crosshair guides, and SMPTE color bars if no webcam is connected.
- **Zero-Bloat WebSocket Hub**:
  - Drops lagging frames on congested WiFi networks using a bounded per-client queue, keeping latency at real-time speeds (<50ms).
- **FastAPI Async Engine**:
  - WebSocket at `/ws/feed` for continuous binary JPEG frames.
  - REST endpoints at `/api/status`, `/api/health`, `/api/config`, `/api/snapshot`.
- **Integrated Live Monitor**:
  - Serves a sleek dark-mode HTML5 canvas monitor at `http://<pi-ip>:8000/` and `http://<pi-ip>:8000/test.html`.

---

## Quick Start (Dev Machine / Off-Pi)

```bash
# 1. Enter the pi folder
cd pi

# 2. Run the dev script (automatically sets up .venv and starts the server)
./scripts/dev.sh
```

Open `http://localhost:8000/` in your browser. You will see the live simulated test stream!

---

## Running on Raspberry Pi 4

```bash
cd pi
./scripts/install.sh
./scripts/dev.sh
```

---

## API & WebSocket Endpoints

| Protocol | Endpoint | Description |
|---|---|---|
| `WS` | `/ws/feed` | Binary JPEG frames streamed at target FPS (e.g. 15fps) |
| `GET` | `/api/status` | Camera backend, live FPS, active client count, uptime |
| `GET` | `/api/health` | Simple health check `{"status": "ok"}` |
| `GET` | `/api/config` | View active resolution, fps, quality |
| `POST` | `/api/config` | Dynamically update preview fps or JPEG quality |
| `GET` | `/api/snapshot` | Returns instant still JPEG from current camera buffer |
| `GET` | `/` | Phase 1 live feed monitor |
| `GET` | `/test.html` | Phase 1 live feed monitor alias |

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PHOTOBOOTH_HOST` | `0.0.0.0` | Bind IP |
| `PHOTOBOOTH_PORT` | `8000` | Port |
| `PHOTOBOOTH_MOCK_CAMERA` | `false` | Force dev/mock mode even if picamera2 is available |
| `PHOTOBOOTH_WEBCAM_DEVICE` | `0` | Laptop/USB webcam device index (e.g. 0 for `/dev/video0`) |
| `PHOTOBOOTH_USE_SYNTHETIC` | `false` | Force synthetic animated test pattern instead of physical webcam |
| `PHOTOBOOTH_PREVIEW_FPS` | `15` | Target streaming FPS |
| `PHOTOBOOTH_PREVIEW_QUALITY` | `70` | JPEG compression quality (1-100) |
| `PHOTOBOOTH_PREVIEW_WIDTH` | `640` | Frame width |
| `PHOTOBOOTH_PREVIEW_HEIGHT` | `480` | Frame height |
