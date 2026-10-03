"""FastAPI application entrypoint for TRC Photo Booth Raspberry Pi Server."""

import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from src.camera import get_camera
from src.config import settings
from src.discovery import get_service_advertiser
from src.gpio_status import LedState, get_gpio_indicator
from src.routes.api import router as api_router
from src.routes.ws import router as ws_router
from src.streamer import get_streamer
from src.watchdog import get_watchdog

# Configure logging
logging.basicConfig(
    level=logging.DEBUG if settings.debug else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("photobooth")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifecycle: starts camera streamer, mDNS, watchdog, and GPIO on boot."""
    logger.info("Initializing TRC Photo Booth server...")
    cam = get_camera()
    streamer = get_streamer()
    gpio = get_gpio_indicator()
    watchdog = get_watchdog()
    advertiser = get_service_advertiser()

    # 1. Start hardware status LED
    gpio.start()
    gpio.set_state(LedState.READY)

    # 2. Start camera and streamer
    logger.info("Selected camera backend: %s (is_mock=%s)", cam.backend_name, cam.is_mock)
    await streamer.start()
    logger.info(
        "Live feed ready at ws://%s:%d/ws/feed (target %dfps)",
        settings.host,
        settings.port,
        settings.preview_fps,
    )

    # 3. Start local network mDNS discovery service
    advertiser.start()

    # 4. Start watchdog monitor
    watchdog.start()

    yield

    logger.info("Shutting down photo booth server...")
    watchdog.stop()
    advertiser.stop()
    await streamer.stop()
    gpio.set_state(LedState.OFF)
    gpio.stop()
    logger.info("Shutdown complete.")


app = FastAPI(
    title="TRC Photo Booth Pi Server",
    description="Live camera capture and streaming over local WiFi for TRC Photo Booth",
    version="0.1.0",
    lifespan=lifespan,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API and WebSocket routes
app.include_router(api_router)
app.include_router(ws_router)

# Mount static files directory
if settings.static_dir.exists():
    app.mount("/static", StaticFiles(directory=str(settings.static_dir)), name="static")


@app.api_route("/assets/{file_path:path}", methods=["GET", "HEAD"], include_in_schema=False)
async def serve_asset_file(file_path: str) -> FileResponse:
    """Serve Vite bundled asset files."""
    asset_file = settings.static_dir / "assets" / file_path
    if asset_file.exists():
        return FileResponse(asset_file)
    from fastapi import HTTPException
    raise HTTPException(status_code=404, detail="Asset not found")


@app.api_route("/", methods=["GET", "HEAD"], include_in_schema=False)
async def serve_index() -> FileResponse:
    """Serve the React Photo Booth web application."""
    index_path = settings.static_dir / "index.html"
    return FileResponse(index_path)


@app.api_route("/test.html", methods=["GET", "HEAD"], include_in_schema=False)
async def serve_test_page() -> FileResponse:
    """Serve test.html diagnostic monitor."""
    test_path = settings.static_dir / "test.html"
    if not test_path.exists():
        test_path = settings.static_dir / "index.html"
    return FileResponse(test_path)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "src.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
    )
