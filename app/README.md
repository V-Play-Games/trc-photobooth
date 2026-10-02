# TRC Photo Booth — Native Android Client (`app/`)

Native Android client application for the TRC Photo Booth, built using **Kotlin**, **Jetpack Compose (Material3)**, and **OkHttp**. It matches all capabilities of the React web client while taking full advantage of Android system features (Network Service Discovery, MediaStore gallery integration, and the system Share Sheet).

---

## Features

- **Real-Time Viewfinder**: Low-latency binary JPEG WebSocket streaming from the Raspberry Pi (`ws://<pi-ip>:8000/ws/feed`) decoded and rendered with GPU hardware acceleration.
- **10 Studio Preset Filters**: Live ColorMatrix and shader styling:
  1. *Normal / RAW*
  2. *Monochrome (B&W)*
  3. *Sepia (Antique)*
  4. *Vintage (70s Film Roll)*
  5. *Cool Tone*
  6. *Warm Golden Hour*
  7. *High Contrast Cyber*
  8. *Vignette Shadowing*
  9. *Textured Film Grain*
  10. *Polaroid Instant Print* (with vintage white border card and caption)
- **Capture Modes**: High-resolution photos and multi-frame animated burst GIFs.
- **Studio Countdown & Flash**: Fullscreen 3-2-1-SMILE animated overlay and studio strobe white flash.
- **Gallery & Lightbox**: Slide-up gallery sheet with filterable tabs (ALL / PHOTOS / GIFS). Fullscreen lightbox with client-side filter baking, direct save to device gallery (`Pictures/TRCPhotoBooth`), and native Android Share Sheet (`Intent.ACTION_SEND`).
- **Pi Discovery & Telemetry**:
  - Auto-discovery of the Raspberry Pi over local Wi-Fi via Network Service Discovery (`_photobooth._tcp`).
  - Fallback manual IP entry with connection persistence.
  - Live hardware telemetry monitor (CPU %, temperature °C, RAM, load averages, throttling flags).
  - Stream controls: target FPS (10-30), JPEG compression quality (50-95%), resolution (SD/HD/FHD), preview mirror, and red/blue channel swap.

---

## Architecture

```
app/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/trc/photobooth/
│       │   ├── MainActivity.kt             # Edge-to-edge Compose activity
│       │   ├── Navigation.kt               # Root Navigation host
│       │   ├── data/
│       │   │   ├── WebSocketClient.kt      # OkHttp binary frames, ping/pong & telemetry
│       │   │   ├── PhotoBoothRepository.kt # StateFlows, REST API calls, SharedPreferences host
│       │   │   └── models/
│       │   │       └── Messages.kt         # Kotlinx serialization data models
│       │   ├── filters/
│       │   │   └── Presets.kt              # 10 ColorMatrix presets and metadata
│       │   ├── theme/
│       │   │   ├── Color.kt                # Cyber studio dark palette tokens
│       │   │   ├── Theme.kt                # Material3 DarkColorScheme
│       │   │   └── Type.kt
│       │   ├── ui/
│       │   │   ├── main/
│       │   │   │   ├── MainScreen.kt       # Studio workspace layout
│       │   │   │   └── MainScreenViewModel.kt # Unified UI state coordinator
│       │   │   └── components/
│       │   │       ├── Header.kt           # Status pill, guides toggle, mirror, counters
│       │   │       ├── LivePreview.kt      # Viewfinder, ColorFilter, HUD, rule of thirds
│       │   │       ├── FilterStrip.kt      # Horizontal preset selector cards
│       │   │       ├── CaptureControls.kt  # Mode pill, timer, GIF params, animated shutter
│       │   │       ├── CountdownOverlay.kt # Digit ring, smile banner, strobe flash
│       │   │       ├── GallerySheet.kt     # Filterable captures grid (All/Photos/GIFs)
│       │   │       ├── LightboxDialog.kt   # Viewer, filter baking, MediaStore & share
│       │   │       └── SettingsDialog.kt   # IP input, NSD scanner, stream sliders, telemetry
│       │   └── util/
│       │       ├── BitmapUtils.kt          # JPEG decode, filter baking, MediaStore & share
│       │       └── NetworkDiscovery.kt     # Android NsdManager service discovery
│       └── res/
└── gradle/
    └── libs.versions.toml
```

---

## Building & Installing

### Prerequisites
- JDK 17+ (Java 21 recommended)
- Android SDK (`compileSdk = 36`, `minSdk = 26`)

### Build Debug APK
From the `app/` directory:
```bash
./gradlew assembleDebug
```
The compiled APK will be output to:
```
app/app/build/outputs/apk/debug/app-debug.apk
```

### Install to Connected Device
```bash
adb install -r app/app/build/outputs/apk/debug/app-debug.apk
```
