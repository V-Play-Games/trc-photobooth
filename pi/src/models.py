"""Pydantic data models for API and WebSocket messages."""

from typing import Any, Literal
from pydantic import BaseModel, Field


class SystemStatus(BaseModel):
    """System health and streaming telemetry."""

    status: Literal["ok", "degraded", "error"] = "ok"
    camera_ready: bool = True
    camera_backend: str = "mock"  # "picamera2" or "mock"
    is_mock: bool = True
    actual_fps: float = Field(0.0, description="Measured camera FPS")
    target_fps: int = 15
    connected_clients: int = 0
    resolution: str = "640x480"
    frames_sent_total: int = 0
    uptime_seconds: float = 0.0
    swap_rb: bool = False


RESOLUTION_PRESETS_16_9: dict[str, tuple[int, int]] = {
    "240p": (426, 240),
    "360p": (640, 360),
    "480p": (854, 480),
    "720p": (1280, 720),
    "1080p": (1920, 1080),
}

RESOLUTION_PRESETS_4_3: dict[str, tuple[int, int]] = {
    "240p": (320, 240),
    "360p": (480, 360),
    "480p": (640, 480),
    "720p": (960, 720),
    "1080p": (1440, 1080),
}


def parse_resolution(
    resolution: str | None = None,
    aspect_ratio: str = "16:9",
    width: int | None = None,
    height: int | None = None,
) -> tuple[int, int] | None:
    """Resolve resolution from 'p' string ('720p', '1080p') or direct dimensions."""
    if resolution:
        key = resolution.strip().lower()
        if not key.endswith("p") and key.isdigit():
            key = f"{key}p"
        mapping = RESOLUTION_PRESETS_4_3 if aspect_ratio == "4:3" else RESOLUTION_PRESETS_16_9
        if key in mapping:
            return mapping[key]
        if "x" in key:
            parts = key.split("x")
            if len(parts) == 2 and parts[0].isdigit() and parts[1].isdigit():
                return int(parts[0]), int(parts[1])
    if width is not None and height is not None:
        return width, height
    return None


class CameraConfigUpdate(BaseModel):
    """Optional settings to adjust live feed performance."""

    fps: int | None = Field(None, ge=1, le=60, description="Target preview FPS")
    quality: int | None = Field(None, ge=10, le=100, description="JPEG quality 10-100")
    resolution: str | None = Field(None, description="Standard 'p' resolution: '240p', '360p', '480p', '720p', '1080p'")
    aspect_ratio: Literal["16:9", "4:3"] | None = Field("16:9", description="Fixed aspect ratio: '16:9' or '4:3'")
    width: int | None = Field(None, ge=160, le=2592, description="Preview frame width in pixels")
    height: int | None = Field(None, ge=120, le=1944, description="Preview frame height in pixels")
    swap_rb: bool | None = Field(None, description="Invert Red/Blue color channels")


class WebSocketControlMessage(BaseModel):
    """Message sent from client to server over WebSocket."""

    type: str
    data: dict[str, Any] | None = None
