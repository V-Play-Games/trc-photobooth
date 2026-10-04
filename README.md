# TRC Photo Booth 📸

A modern, low-latency DIY photo booth system powered by a **Raspberry Pi 4/5** backend and a polished **React + TypeScript** web client. Designed for events, parties, and permanent installations over local Wi-Fi.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Python](https://img.shields.io/badge/Python-3.11%20%7C%203.12%20%7C%203.13-blue.svg)
![FastAPI](https://img.shields.io/badge/FastAPI-0.115+-009688.svg)
![React](https://img.shields.io/badge/React-19-61dafb.svg)
![TypeScript](https://img.shields.io/badge/TypeScript-5.8+-3178c6.svg)
![Vite](https://img.shields.io/badge/Vite-6.x-646cff.svg)
![Tests](https://img.shields.io/badge/Tests-25%20Passing-brightgreen.svg)

---

## 🌟 System Overview

TRC Photo Booth captures live frames from a Raspberry Pi camera module, streams them in real-time over WebSocket with zero buffer bloat, and provides an interactive photo booth experience with vintage filters, countdown animations, strobe flashes, GIF burst recording, and photo downloads.

```
                           ┌───────────────────────────────────────────────┐
                           │            Raspberry Pi Server (pi/)          │
                           │                                               │
┌───────────────────────┐  │  ┌──────────────┐      ┌──────────────────┐  │
│  Camera Module v2/v3  │──┼─▶│ picamera2 /  │─────▶│  Streamer Hub    │  │
│  (or Laptop / Webcam) │  │  │  MockCamera  │      │  (Bounded Queue) │  │
└───────────────────────┘  │  └──────────────┘      └────────┬─────────┘  │
                           │         │                       │             │
                           │         ▼                       │ WS          │
                           │  ┌──────────────┐               ▼ /ws/feed    │
                           │  │Capture Engine│       Binary JPEG Frames    │
                           │  │(Photo / GIF) │       & Telemetry JSON      │
                           │  └──────────────┘                             │
                           └─────────┬───────────────────────┬─────────────┘
                                     │ HTTP REST             │
                                     │ /api/captures         │
                                     ▼                       ▼
                           ┌───────────────────────────────────────────────┐
                           │          Web Client (web/ -> static/)         │
                           │                                               │
                           │  • Responsive Live Preview Canvas             │
                           │  • 10 CSS & Canvas Filters (Vintage, B&W...)  │
                           │  • Shutter Button (Photo / GIF Burst)         │
                           │  • 3-2-1 Fullscreen Countdown & Strobe Flash  │
                           │  • Gallery Drawer & In-Browser Filter Baking  │
                           │  • Pi Hardware Health Telemetry (CPU/RAM/Temp)│
                           └───────────────────────────────────────────────┘
```

---

## 🚀 Key Features

- **Multi-Backend Camera Engine**:
  - **Raspberry Pi Native**: Hardware-accelerated capture using `picamera2` on official Camera Modules.
  - **Laptop / USB Webcam**: Auto-detection and mirroring via OpenCV (`cv2.VideoCapture`) for rapid off-Pi development.
  - **Synthetic Test Pattern**: Generates SMPTE color bars, animated millisecond timestamps, frame counters, and crosshairs when no camera is present.
- **Zero-Latency WebSocket Stream**:
  - Drops stale frames automatically on congested Wi-Fi networks using a bounded capacity-1 queue to maintain `<50ms` latency.
- **Remote Shutter & Multi-Frame GIFs**:
  - High-resolution JPEG still captures (up to 2592×1944 / 4608×2592).
  - Multi-frame burst capture assembled into animated GIFs using Pillow & imageio.
- **Live Client-Side Filter Suite**:
  - 10 aesthetic presets: *Normal*, *Monochrome (B&W)*, *Sepia*, *Vintage '70s*, *Cool Tone*, *Golden Hour*, *Dramatic Noir*, *Vignette*, *Film Grain*, and *Polaroid*.
  - Procedural radial vignette and analog film grain rendering.
  - In-browser canvas filter baking: downloads photos with the selected filter and Polaroid border frame baked in.
- **Interactive Photo Booth UX**:
  - Tactile shutter button with glowing pulse ring and mobile haptic feedback.
  - <kbd>Spacebar</kbd> keyboard shortcut to snap photos.
  - Fullscreen 3-2-1 countdown overlay with strobe camera flash effect.
  - Celebration confetti animations on capture.
  - Slide-out gallery drawer with instant download and native Web Share API support.
- **Pi Hardware Diagnostics**:
  - Live CPU utilization gauge, RAM usage metrics, and SoC temperature monitoring.
  - Fallback developer diagnostic tool preserved at `/test.html`.

---

## 📂 Repository Structure

```
trc-photobooth/
├── pi/                         # Raspberry Pi Server (Python + FastAPI)
│   ├── src/
│   │   ├── camera.py           # Picamera2 wrapper + Laptop webcam + Synthetic mock
│   │   ├── capture.py          # High-res still & animated GIF capture engine
│   │   ├── streamer.py         # Fan-out WebSocket hub with frame-dropping
│   │   ├── config.py           # Pydantic configuration settings
│   │   ├── models.py           # Pydantic models & resolution parsers
│   │   ├── system_info.py      # Pi CPU, RAM, and temperature telemetry
│   │   ├── main.py             # FastAPI lifespan, REST routes, and static mounts
│   │   ├── routes/             # API and WebSocket endpoints
│   │   └── static/             # Production static assets served by FastAPI
│   │       ├── index.html      # React Photo Booth web client
│   │       ├── test.html       # Diagnostic live monitor
│   │       └── assets/         # Bundled JS and CSS from Vite
│   ├── tests/                  # Pytest test suite (25 automated tests)
│   └── scripts/
│       ├── dev.sh              # Local development server launcher
│       └── install.sh          # Pi environment setup script
├── web/                        # React Web Client (Vite + TypeScript + Vanilla CSS)
│   ├── src/
│   │   ├── components/         # Header, LivePreview, FilterBar, CaptureControls,
│   │   │                       # CountdownOverlay, GalleryDrawer, LightboxModal, SettingsModal
│   │   ├── filters/            # 10 presets & Canvas filter baking engine
│   │   ├── hooks/              # useWebSocket (binary stream) & useCaptures (REST)
│   │   ├── types.ts            # Shared TypeScript contracts
│   │   ├── App.tsx             # Main photo booth UI coordinator
│   │   └── index.css           # Glassmorphism dark-theme design system
│   ├── vite.config.ts          # Build configuration targeting pi/src/static
│   └── package.json
├── app/                        # Native Android Client (Kotlin + Jetpack Compose, 4-Quadrant Photobooth & Admin UI)
└── roadmap.md                  # Prototype phases and development roadmap
```

---

## 🚦 Getting Started

### Prerequisites

- **Python**: 3.11+
- **Node.js**: 20+ (with npm)
- **Raspberry Pi OS** (Bookworm 64-bit recommended on hardware) or Linux/macOS/Windows for development.

---

### Quick Start (Dev Machine / Laptop)

#### 1. Start the Raspberry Pi Server

```bash
cd pi
./scripts/dev.sh
```

The script will automatically set up the virtual environment (`.venv`), install dependencies, detect your laptop webcam (or use synthetic mock patterns), and start Uvicorn on `http://0.0.0.0:8000/`.

#### 2. Access the Application

- **Photo Booth Web Client**: [http://localhost:8000/](http://localhost:8000/)
- **Diagnostic Monitor**: [http://localhost:8000/test.html](http://localhost:8000/test.html)
- **Interactive API Docs (Swagger)**: [http://localhost:8000/docs](http://localhost:8000/docs)

---

### Frontend Web Development (`web/`)

When modifying the React application:

```bash
cd web

# 1. Install dependencies
npm install

# 2. Run Vite dev server with proxy to Pi backend
npm run dev

# 3. Build and deploy to Pi static directory
npm run build
```

The build command automatically bundles optimized assets into `pi/src/static/` with relative asset links (`./assets/...`).

---

## 🧪 Testing

The project includes unit and integration tests covering the FastAPI endpoints, camera backends, capture storage, WebSocket contracts, and static asset serving:

```bash
cd pi
.venv/bin/pytest
```

Output:
```
======================== 25 passed in 4.15s ========================
```

To run TypeScript verification and linter on the web client:

```bash
cd web
npm run lint
npm run build
```

To run unit tests and build the Android client APK:

```bash
cd app
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

---

## 📱 Android Client (`app/`)

The native Android app provides two distinct interfaces:

### 1. Admin Studio Workspace
The full-featured technician interface with live FPS/latency HUD, mirror flipping, guide overlays, 10 filter presets, manual high-res photo and burst GIF triggers, capture gallery with lightbox sharing, and mDNS auto-discovery.
- Launch the booth from the **BOOTH** button in the header bar.

### 2. Dedicated 4-Quadrant Photobooth Interface
An interactive, automated photobooth experience tailored for event guests:
- **Sequential 2×2 Grid**: Captures 4 photos sequentially:
  - Quadrant 1 (Top-Left) → Quadrant 2 (Top-Right) → Quadrant 3 (Bottom-Left) → Quadrant 4 (Bottom-Right)
- **Live Preview First in Quadrant 1**: The live camera stream appears immediately in the top-left quadrant with the active filter.
- **Filters Including 🎲 RANDOM**: Guests can choose their favorite style, or pick **RANDOM** which applies a different random concrete filter for each of the 4 shots.
- **Configurable Timer**: Choose 3s, 5s, or 10s delay between shots with haptic ticks and animated countdown ring.
- **Unstoppable Capture Sequence**: Once the guest taps **START BOOTH**, photos start getting clicked automatically without intervention; the sequence cannot be cancelled or stopped mid-session.
- **Session Saving**: Automatically saves all 4 images to `Pictures/TRCPhotoBooth/sessions/<timestamp>/`:
  ```
  photo_1_<filter>.jpg
  photo_2_<filter>.jpg
  photo_3_<filter>.jpg
  photo_4_<filter>.jpg
  ```

---

## ☁️ Cloudinary Configuration (1×4 Photo Strip Upload & QR Code)

Both the **Native Android App** and **Web Client** integrate with Cloudinary to automatically host composite photos and generate instant download QR codes:

```
┌──────────────────┐     ┌───────────────────────┐     ┌──────────────────────┐     ┌──────────────────┐
│  4 Photos Taken  │ ──▶ │  1×4 Vertical Strip   │ ──▶ │ Upload to Cloudinary │ ──▶ │ QR Code Displayed│
│ (Sequential 1×4) │     │ (210 mm × 74.25 mm)   │     │  (Direct HTTPS POST) │     │ (Scan to Download)
└──────────────────┘     └───────────────────────┘     └──────────────────────┘     └──────────────────┘
```

When a 4-photo session completes:
1. The app stitches the 4 photos into a 210 mm × 74.25 mm **1×4 vertical photo strip** (877×2480 px at 300 DPI) with clean white padding around and between each photo.
2. The photo strip is uploaded directly to Cloudinary.
3. A **QR code** pointing to the Cloudinary URL is generated and displayed on screen so guests can scan and download their photo strip immediately.

---

### Step 1: Get Cloudinary Credentials

1. Create a free account at [cloudinary.com](https://cloudinary.com/) (or log in).
2. On your Cloudinary **Dashboard**, locate your **Cloud Name** (e.g. `dxy8abc12`).

---

### Step 2: Create an Unsigned Upload Preset (Recommended)

An **Unsigned Upload Preset** allows mobile and web clients to upload directly to Cloudinary securely without exposing your API Secret in client builds:

1. In the Cloudinary Console, click the **Settings** (gear icon) in the bottom-left.
2. Select the **Upload** tab.
3. Scroll down to **Upload presets** and click **Add upload preset**.
4. Change **Signing Mode** from *Signed* to **Unsigned** (⚠️ critical).
5. (Optional) Set the **Preset name** (e.g. `trc_photobooth` or leave the generated name).
6. (Optional) Set **Folder** to `trc-photobooth/sessions`.
7. Click **Save** in the top right.

---

### Step 3: Configure Android App (`app/`)

Choose any of the three options below to provide your Cloudinary credentials at build time:

#### Option A: In `app/gradle.properties` (Recommended for Local Dev)
Open [`app/gradle.properties`](file:///data/Projects/trc-photobooth/app/gradle.properties) and set:

```properties
CLOUDINARY_CLOUD_NAME=your_cloud_name_here
CLOUDINARY_UPLOAD_PRESET=your_unsigned_preset_here
# Optional (only needed if using signed uploads instead of upload preset):
CLOUDINARY_API_KEY=your_api_key_here
CLOUDINARY_API_SECRET=your_api_secret_here
```

#### Option B: Environment Variables
Export variables in your terminal before building:

```bash
export CLOUDINARY_CLOUD_NAME="your_cloud_name_here"
export CLOUDINARY_UPLOAD_PRESET="your_unsigned_preset_here"

cd app
./gradlew assembleDebug
```

#### Option C: Command Line Arguments
Pass parameters directly to Gradle:

```bash
cd app
./gradlew assembleDebug \
  -PCLOUDINARY_CLOUD_NAME="your_cloud_name_here" \
  -PCLOUDINARY_UPLOAD_PRESET="your_unsigned_preset_here"
```

The APK will be generated at `app/app/build/outputs/apk/debug/app-debug.apk`.

---

### Step 4: Configure Web Client (`web/`)

For the React Web client:

1. Copy the example configuration:
   ```bash
   cp web/.env.example web/.env
   ```
2. Edit `web/.env`:
   ```bash
   VITE_CLOUDINARY_CLOUD_NAME=your_cloud_name_here
   VITE_CLOUDINARY_UPLOAD_PRESET=your_unsigned_preset_here
   ```
3. Rebuild the static bundle:
   ```bash
   cd web
   npm run build
   ```
   *(Assets are automatically compiled to `pi/src/static/` to be served by the Pi backend).*

---

### Step 5: Verify & Test

1. Launch Photobooth mode on Android or Web.
2. Tap **START BOOTH** and complete the 4-photo sequence.
3. Upon completion, the screen will show **"Uploading 1×4 photo strip to Cloudinary..."** followed by the generated **QR Code** and photo strip preview.
4. Scan the QR code using any smartphone camera to open and download the high-resolution composite photo!

---

## 📡 API & WebSocket Reference

### WebSocket Stream

- **`WS /ws/feed`**: Binary WebSocket delivering real-time JPEG frames and bidirectional JSON control messages (`ping`/`pong`, `trigger_capture`, `trigger_gif`, `countdown_tick`, `set_fps`, `set_quality`, `get_system_stats`).

### REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Serves the React Photo Booth web application |
| `GET` | `/test.html` | Serves the Phase 1/2 developer diagnostic monitor |
| `GET` | `/api/status` | Camera backend status, live FPS, active clients, resolution |
| `GET` | `/api/health` | Lightweight watchdog health check `{"status": "ok"}` |
| `GET` | `/api/config` | Current camera preview & capture configuration |
| `POST` | `/api/config` | Dynamically update preview FPS, JPEG quality, resolution, or flip |
| `GET` | `/api/captures` | List recent photos and GIFs in temporary storage |
| `GET` | `/api/captures/{id}` | Serve captured high-res JPEG photo or animated GIF |
| `POST` | `/api/captures/photo` | Trigger photo capture with optional countdown |
| `POST` | `/api/captures/gif` | Trigger GIF burst capture with configurable frames/interval |
| `DELETE` | `/api/captures/{id}` | Delete a capture from memory store |
| `GET` | `/api/system/stats` | On-demand Raspberry Pi CPU %, RAM usage, and SoC temperature |

---

## ⚙️ Configuration

Settings can be customized using environment variables or a `.env` file in the `pi/` directory:

| Environment Variable | Default | Description |
|---|---|---|
| `PHOTOBOOTH_HOST` | `0.0.0.0` | Server bind host address |
| `PHOTOBOOTH_PORT` | `8000` | Server port |
| `PHOTOBOOTH_DEBUG` | `false` | Enable debug logs and hot reload |
| `PHOTOBOOTH_MOCK_CAMERA` | `false` | Force dev/mock mode even if `picamera2` is installed |
| `PHOTOBOOTH_WEBCAM_DEVICE` | `0` | Physical webcam index (e.g. `/dev/video0`) |
| `PHOTOBOOTH_USE_SYNTHETIC` | `false` | Force synthetic test pattern instead of webcam |
| `PHOTOBOOTH_PREVIEW_FPS` | `15` | Default preview target FPS |
| `PHOTOBOOTH_PREVIEW_QUALITY` | `70` | JPEG compression quality (1–100) |
| `PHOTOBOOTH_PREVIEW_WIDTH` | `640` | Default stream preview width |
| `PHOTOBOOTH_PREVIEW_HEIGHT` | `480` | Default stream preview height |
| `PHOTOBOOTH_CAPTURE_WIDTH` | `2592` | Still photo capture resolution width |
| `PHOTOBOOTH_CAPTURE_HEIGHT` | `1944` | Still photo capture resolution height |
| `PHOTOBOOTH_CAPTURE_QUALITY` | `95` | Still photo capture JPEG quality |
| `PHOTOBOOTH_FLIP_HORIZONTAL` | `false` | Default mirror flip (disabled by default) |

---

## 🗺️ Project Roadmap

- [x] **Phase 1: Pi Camera & WebSocket Streaming Hub** (Hardware capture, bounded queues, dev monitor)
- [x] **Phase 2: Photo Capture, Animated GIFs & Countdown** (High-res stills, burst GIF engine, storage)
- [x] **Phase 3: React Web Client** (Vite + React, dark theme design system, 10 filters, gallery drawer, filter baking)
- [x] **Phase 4: Native Android Client** (Kotlin + Jetpack Compose, 4-quadrant photobooth, mDNS auto-discovery, native sharing)
- [ ] **Phase 5: Polish & Hardware Integration** (Thermal management, kiosk auto-launch, enclosure setup)

Refer to [`roadmap.md`](file:///data/Projects/trc-photobooth/roadmap.md) for full phase specifications and delivery logs.

---

## 📄 License

This project is licensed under the MIT License.
