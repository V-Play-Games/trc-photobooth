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


class CameraConfigUpdate(BaseModel):
    """Optional settings to adjust live feed performance."""

    fps: int | None = Field(None, ge=1, le=30, description="Target preview FPS")
    quality: int | None = Field(None, ge=10, le=100, description="JPEG quality 10-100")
    swap_rb: bool | None = Field(None, description="Invert Red/Blue color channels")


class WebSocketControlMessage(BaseModel):
    """Message sent from client to server over WebSocket."""

    type: str
    data: dict[str, Any] | None = None
