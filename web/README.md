# TRC Photo Booth — Web Client (`web/`)

React + TypeScript web application for the TRC Photo Booth. Built with Vite and modern responsive CSS.

---

## Features (Phase 3 Target)

- **Live Stream Viewer**: Renders continuous JPEG frames from `ws://<pi-ip>:8000/ws/feed` on an HTML5 `<canvas>`.
- **Client-Side Filters**: Instant, zero-latency CSS `filter()` previews (B&W, Sepia, Vintage, Cool, Warm, Noir, etc.) and Canvas filter baking on capture.
- **Remote Shutter Controls**: Trigger single captures and multi-frame GIFs with 3-2-1 countdown.
- **Local Gallery**: View recent session captures, download, and share.

---

## Development

```bash
# 1. Install dependencies
cd web
npm install

# 2. Start Vite dev server
npm run dev

# 3. Production build (output can be served directly from pi/src/static/)
npm run build
```
