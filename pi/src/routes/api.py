"""REST API endpoints for photo booth system status and controls."""

import time
from typing import Any
from fastapi import APIRouter, Response, status
from src.camera import get_camera
from src.config import settings
from src.models import CameraConfigUpdate, SystemStatus, SystemStatsResponse, parse_resolution
from src.streamer import get_streamer
from src.system_info import get_system_stats

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
        flip_horizontal=settings.flip_horizontal,
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
        "flip_horizontal": settings.flip_horizontal,
        "capture_width": settings.capture_width,
        "capture_height": settings.capture_height,
        "capture_quality": settings.capture_quality,
    }


@router.post("/config")
async def update_camera_config(config_update: CameraConfigUpdate) -> dict[str, Any]:
    """Dynamically adjust preview frame rate, JPEG compression quality, color channel order, or flip."""
    cam = get_camera()
    if config_update.fps is not None:
        settings.preview_fps = config_update.fps
        if hasattr(cam, "target_fps"):
            cam.target_fps = config_update.fps
    if config_update.quality is not None:
        settings.preview_quality = config_update.quality
        if hasattr(cam, "quality"):
            cam.quality = config_update.quality

    # Parse and apply resolution (either '720p' style string or explicit width/height)
    res_tuple = parse_resolution(
        resolution=config_update.resolution,
        aspect_ratio=config_update.aspect_ratio or "16:9",
        width=config_update.width,
        height=config_update.height,
    )
    if res_tuple is not None:
        new_width, new_height = res_tuple
        settings.preview_width = new_width
        settings.preview_height = new_height
        if hasattr(cam, "set_resolution"):
            cam.set_resolution(new_width, new_height)
        else:
            cam.width = new_width
            cam.height = new_height

    if config_update.swap_rb is not None:
        settings.swap_rb = config_update.swap_rb

    if config_update.flip_horizontal is not None:
        settings.flip_horizontal = config_update.flip_horizontal
        if hasattr(cam, "flip_horizontal"):
            cam.flip_horizontal = config_update.flip_horizontal

    return {
        "message": "Config updated successfully",
        "fps": settings.preview_fps,
        "quality": settings.preview_quality,
        "width": settings.preview_width,
        "height": settings.preview_height,
        "swap_rb": settings.swap_rb,
        "flip_horizontal": settings.flip_horizontal,
    }


@router.get("/system/stats", response_model=SystemStatsResponse)
async def get_system_hardware_stats() -> SystemStatsResponse:
    """Return on-demand Raspberry Pi system stats (CPU %, RAM used/total, and temperatures).

    Zero compute overhead when idle: stats are measured only when this endpoint is actively called.
    """
    return get_system_stats()


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
