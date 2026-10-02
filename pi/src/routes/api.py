"""REST API endpoints for photo booth system status and controls."""

import time
from typing import Any
from fastapi import APIRouter, Response, status
from src.camera import get_camera
from src.config import settings
from src.models import CameraConfigUpdate, SystemStatus
from src.streamer import get_streamer

router = APIRouter(prefix="/api", tags=["System"])


@router.get("/status", response_model=SystemStatus)
async def get_status() -> SystemStatus:
    """Return live system telemetry, camera backend, fps, and client metrics."""
    cam = get_camera()
    streamer = get_streamer()

    return SystemStatus(
        status="ok" if cam.is_running else "degraded",
        camera_ready=cam.is_running,
        camera_backend=cam.backend_name,
        is_mock=cam.is_mock,
        actual_fps=cam.actual_fps,
        target_fps=settings.preview_fps,
        connected_clients=streamer.client_count,
        resolution=f"{settings.preview_width}x{settings.preview_height}",
        frames_sent_total=streamer.frames_sent_total,
        uptime_seconds=streamer.uptime_seconds,
        swap_rb=settings.swap_rb,
    )


@router.get("/health")
async def health_check() -> dict[str, str | float]:
    """Lightweight health check for reverse proxies or watchdog monitoring."""
    return {"status": "ok", "timestamp": time.time()}


@router.get("/config")
async def get_camera_config() -> dict[str, Any]:
    """Get active preview streaming configuration."""
    return {
        "width": settings.preview_width,
        "height": settings.preview_height,
        "fps": settings.preview_fps,
        "quality": settings.preview_quality,
        "swap_rb": settings.swap_rb,
        "capture_width": settings.capture_width,
        "capture_height": settings.capture_height,
        "capture_quality": settings.capture_quality,
    }


@router.post("/config")
async def update_camera_config(config_update: CameraConfigUpdate) -> dict[str, Any]:
    """Dynamically adjust preview frame rate, JPEG compression quality, or color channel order."""
    if config_update.fps is not None:
        settings.preview_fps = config_update.fps
        cam = get_camera()
        if hasattr(cam, "target_fps"):
            cam.target_fps = config_update.fps
    if config_update.quality is not None:
        settings.preview_quality = config_update.quality
        cam = get_camera()
        if hasattr(cam, "quality"):
            cam.quality = config_update.quality
    if config_update.swap_rb is not None:
        settings.swap_rb = config_update.swap_rb

    return {
        "message": "Config updated successfully",
        "fps": settings.preview_fps,
        "quality": settings.preview_quality,
        "swap_rb": settings.swap_rb,
    }


@router.get("/snapshot")
async def get_instant_snapshot() -> Response:
    """Return the latest frame as an immediate image/jpeg download for testing."""
    cam = get_camera()
    frame = cam.get_latest_frame()
    if not frame:
        return Response(content="Camera not ready", status_code=status.HTTP_503_SERVICE_UNAVAILABLE)
    return Response(
        content=frame,
        media_type="image/jpeg",
        headers={"Content-Disposition": "inline; filename=snapshot.jpg"},
    )
