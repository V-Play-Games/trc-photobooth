# TRC Photo Booth — React Web Client (`web/`)

A high-performance, dark-mode React web client for the TRC Photo Booth. Built with **Vite**, **React 19**, **TypeScript**, and **Vanilla CSS**.

Served directly by the Raspberry Pi FastAPI server at `http://<pi-ip>:8000/`.

---

## 📸 Features

- **Live Stream Canvas**: High-throughput binary JPEG frame rendering directly onto an HTML5 `<canvas>` via `createImageBitmap` and `requestAnimationFrame`.
- **10 Aesthetic Filters**:
  - `Normal` — Raw, unfiltered camera stream
  - `Monochrome` — High-definition black & white with punchy contrast
  - `Sepia` — Warm antique print tone
  - `Vintage` — 1970s Kodachrome warm saturation
  - `Cool Tone` — Arctic cyan & blue styling
  - `Golden Hour` — Sunset glow ideal for portraits
  - `Dramatic` — High-contrast Noir
  - `Vignette` — Procedural radial edge darkening
  - `Film Grain` — Analog ISO 35mm grain texture
  - `Polaroid` — Retro instant print color grading with classic white border frame
- **Canvas Filter Baking Engine**:
  - Renders CSS filters in real-time on the live preview with zero latency.
  - Bakes the selected filter preset, vignette, grain, and Polaroid frame directly onto high-res captures before downloading or sharing.
- **Interactive Shutter & Capture Modes**:
  - High-res still photos and multi-frame animated GIF bursts.
  - Shutter button with glowing pulse ring, mobile haptic vibration, and <kbd>Spacebar</kbd> shortcut.
  - Fullscreen 3-2-1 countdown overlay with studio strobe flash effect and celebration confetti.
- **Gallery & Lightbox**:
  - Slide-out gallery drawer with media badges (`PHOTO` vs `GIF`), file size, and timestamps.
  - Expanded lightbox viewer with live filter preview switcher, instant download, and native Web Share API support.
- **Raspberry Pi Telemetry & Controls**:
  - Dynamic FPS (5–60 FPS), JPEG quality (30–95%), and resolution switching (480p, 720p, 1080p).
  - Real-time Pi hardware health: live CPU %, RAM usage, and SoC core temperature.
- **Dedicated 4-Quadrant Photobooth Experience (`BoothView`)**:
  - 2×2 sequential photo grid designed for guests: Quadrant 1 (Top-Left) → Quadrant 2 (Top-Right) → Quadrant 3 (Bottom-Left) → Quadrant 4 (Bottom-Right).
  - Live preview immediately appears in Quadrant 1 on entry.
  - Choosable filters before starting, including **🎲 RANDOM** (picks a fresh random filter per photo in the sequence).
  - Configurable countdown timer (3s, 5s, 10s).
  - **Unstoppable sequence**: Once initiated, the sequence takes 4 photos automatically without intervention; cancel and navigation are locked until all 4 photos are complete.
  - Multiple save options:
    - **Download Photos**: Sequential browser download of all 4 baked JPEGs (`photo_1_<filter>.jpg` through `photo_4_<filter>.jpg`).
    - **Save to Folder**: Direct local directory save using the modern File System Access API.
    - **Cloudinary Upload**: 1-click direct upload to Cloudinary using client-side unsigned upload presets or credentials.

---

## ☁️ Cloudinary Configuration (2×2 Collage Upload & QR Code)

When a 4-photo photobooth session completes, the web client creates a 1200×1400 2×2 composite collage on canvas, uploads it to Cloudinary, and generates a QR code on screen for guests to scan with their phones.

### 1. Create an Unsigned Upload Preset in Cloudinary
1. Log in to [Cloudinary](https://cloudinary.com/) -> **Settings** (gear icon) -> **Upload**.
2. Scroll to **Upload presets** and click **Add upload preset**.
3. Set **Signing Mode** to **Unsigned** (required for client-side uploads).
4. Note your preset name (e.g., `trc_photobooth`).
5. Click **Save**.

### 2. Configure `web/.env`
Create `web/.env` (or copy from `.env.example`):

```bash
cp .env.example .env
```

Set your values in `web/.env`:
```env
VITE_CLOUDINARY_CLOUD_NAME=your_cloud_name
VITE_CLOUDINARY_UPLOAD_PRESET=your_unsigned_preset
```

Then rebuild the static bundle:
```bash
npm run build
```

---

## 🛠️ Architecture & Components

```
web/src/
├── components/
│   ├── booth/
│   │   ├── BoothView.tsx           # 4-quadrant automated photobooth interface
│   │   ├── BoothQuadrantGrid.tsx   # 2×2 grid with active feed & snapshot display
│   │   ├── BoothFilterBar.tsx      # Booth preset selector including RANDOM
│   │   └── BoothCountdownOverlay.tsx # Sequential countdown overlay & flash
│   ├── Header.tsx              # Top bar with branding, live status, framing grid, and settings
│   ├── LivePreview.tsx         # Canvas stream renderer with filter overlays and HUD
│   ├── FilterBar.tsx           # Horizontal scrollable strip of 10 filter swatches
│   ├── CaptureControls.tsx     # Mode toggle, timer delay, and shutter container
│   ├── CaptureButton.tsx       # Shutter button with glowing pulse ring and spacebar listener
│   ├── CountdownOverlay.tsx    # 3-2-1 animated countdown and camera flash effect
│   ├── GalleryDrawer.tsx       # Slide-out recent captures drawer with filter tabs
│   ├── PhotoCard.tsx           # Capture thumbnail card with quick actions
│   ├── LightboxModal.tsx       # Fullscreen photo view, filter baking, and Web Share
│   └── SettingsModal.tsx       # FPS/Quality sliders, camera toggles, and Pi hardware stats
├── filters/
│   └── presets.ts              # Filter definitions, CSS strings, and canvas baking engine
├── hooks/
│   ├── useWebSocket.ts         # Binary feed streaming, auto-reconnect, and action dispatch
│   └── useCaptures.ts          # REST capture store management
├── utils/
│   ├── audio.ts                # Mechanical shutter and countdown audio synthesis
│   └── cloudinary.ts           # Cloudinary direct uploads and session directory saving
├── types.ts                    # TypeScript interface contracts
├── App.tsx                     # Main photo booth UI coordinator
└── index.css                   # Glassmorphism dark-theme design system
```

---

## 🚀 Development Workflow

```bash
# 1. Install dependencies
npm install

# 2. Start Vite development server (proxies /api and /ws to http://localhost:8000)
npm run dev

# 3. Lint with oxlint
npm run lint

# 4. Production build (compiles and outputs to ../pi/src/static/)
npm run build
```

---

## 🔗 Connection Protocol

In development mode (`port 5173`), the application automatically routes WebSocket traffic to `ws://${window.location.hostname}:8000/ws/feed`.

When bundled and served from the Raspberry Pi (`port 8000`), the client connects directly to `ws://${window.location.host}/ws/feed`.
