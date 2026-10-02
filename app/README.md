# TRC Photo Booth — Android Client (`app/`)

Native Android client application for the TRC Photo Booth, built using **Kotlin** and **Jetpack Compose**. Scheduled for implementation in **Phase 4** of the prototype roadmap.

---

## Planned Architecture

```
app/
├── app/
│   └── src/main/
│       ├── java/com/trc/photobooth/
│       │   ├── MainActivity.kt         # Entry point & Compose host
│       │   ├── ui/
│       │   │   ├── screens/
│       │   │   │   ├── LiveScreen.kt       # Live WebSocket preview + overlay
│       │   │   │   ├── GalleryScreen.kt    # Photo & GIF gallery
│       │   │   │   └── SettingsScreen.kt   # Host IP, quality preferences
│       │   │   ├── components/
│       │   │   │   ├── LiveFeed.kt         # Hardware-accelerated Bitmap canvas
│       │   │   │   ├── FilterStrip.kt      # Horizontal filter picker
│       │   │   │   ├── CaptureControls.kt  # Photo / GIF buttons
│       │   │   │   └── CountdownOverlay.kt # 3-2-1 animated countdown
│       │   │   └── theme/
│       │   │       ├── Theme.kt
│       │   │       ├── Color.kt
│       │   │       └── Type.kt
│       │   ├── data/
│       │   │   ├── WebSocketClient.kt      # OkHttp WebSocket manager
│       │   │   ├── PhotoBoothRepository.kt # Feed state & API actions
│       │   │   └── models/
│       │   │       └── Messages.kt         # Telemetry & capture response models
│       │   ├── filters/
│       │   │   ├── FilterEngine.kt         # ColorMatrix GPU filter pipeline
│       │   │   └── Presets.kt              # 10 preset filters matching web
│       │   └── util/
│       │       ├── BitmapUtils.kt          # ByteArray -> Bitmap converter
│       │       └── NetworkDiscovery.kt     # mDNS / NSD auto-discovery for Pi
│       └── res/
└── build.gradle.kts
```

---

## Key Technologies

- **UI**: Jetpack Compose (Material3 Dark Theme)
- **Networking**: OkHttp WebSocket (`ws://<pi-ip>:8000/ws/feed`)
- **Filters**: Android `ColorMatrix` / `ColorFilter` for real-time preview (matching web CSS filters)
- **Image Pipeline**: Coil + `BitmapFactory.decodeByteArray()`
- **Discovery**: Android Network Service Discovery (NSD) to automatically discover `_photobooth._tcp` on local WiFi
- **Minimum SDK**: 26 (Android 8.0+)
- **Target SDK**: 34 (Android 14+)
