"""Pydantic data models for API and WebSocket messages."""

from typing import Any, Literal
from pydantic import BaseModel, Field


class VideoDeviceInfo(BaseModel):
    device: str
    index: int
    name: str = ""
    available: bool = True


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
    frames_dropped_total: int = 0
    uptime_seconds: float = 0.0
    swap_rb: bool = False
    flip_horizontal: bool = True
    is_healthy: bool = True
    memory_usage_mb: float = 0.0
    capture_count: int = 0
    webcam_device: int = 0
    device_path: str = "/dev/video0"
    available_devices: list[VideoDeviceInfo] = Field(default_factory=list)


class MemoryStats(BaseModel):
    """System memory statistics."""

    total_mb: float = Field(..., description="Total system memory in MB")
    used_mb: float = Field(..., description="Used memory in MB")
    free_mb: float = Field(..., description="Available free memory in MB")
    percent: float = Field(..., description="Memory utilization percentage")


class SystemStatsResponse(BaseModel):
    """On-demand Raspberry Pi hardware telemetry."""

    cpu_percent: float = Field(..., description="Instant or delta CPU percentage (0-100%)")
    cpu_temp_c: float | None = Field(None, description="CPU temperature in degrees Celsius")
    memory: MemoryStats = Field(..., description="System RAM memory statistics")
    load_avg: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0], description="1, 5, 15 minute load averages")
    cpu_count: int = Field(1, description="Number of CPU cores")
    throttled: str | None = Field(None, description="Raspberry Pi under-voltage/thermal throttling flags")
    timestamp: float = Field(0.0, description="Telemetry sample epoch timestamp")


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
    aspect_ratio: str = "4:3",
    width: int | None = None,
    height: int | None = None,
) -> tuple[int, int] | None:
    """Resolve resolution from 'p' string ('720p', '1080p') or direct dimensions."""
    if resolution:
        key = resolution.strip().lower()
        if not key.endswith("p") and key.isdigit():
            key = f"{key}p"
        mapping = RESOLUTION_PRESETS_16_9 if aspect_ratio == "16:9" else RESOLUTION_PRESETS_4_3
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
    aspect_ratio: Literal["16:9", "4:3"] | None = Field("4:3", description="Fixed aspect ratio: '4:3' or '16:9'")
    width: int | None = Field(None, ge=160, le=2592, description="Preview frame width in pixels")
    height: int | None = Field(None, ge=120, le=1944, description="Preview frame height in pixels")
    swap_rb: bool | None = Field(None, description="Invert Red/Blue color channels")
    flip_horizontal: bool | None = Field(None, description="Invert camera output horizontally (left-to-right mirror flip)")
    webcam_device: int | str | None = Field(None, description="Webcam device index (0, 1) or /dev/video* path")


class WebSocketControlMessage(BaseModel):
    """Message sent from client to server over WebSocket."""

    type: str
    data: dict[str, Any] | None = None


class CaptureMetadata(BaseModel):
    """Metadata representing a single captured still photo or animated GIF."""

    id: str = Field(..., description="Unique capture identifier")
    type: Literal["photo", "gif"] = "photo"
    content_type: str = Field("image/jpeg", description="MIME content type")
    filename: str = Field(..., description="Download filename")
    width: int = Field(..., description="Image width in pixels")
    height: int = Field(..., description="Image height in pixels")
    size_bytes: int = Field(..., description="Payload size in bytes")
    created_at: float = Field(..., description="Creation epoch timestamp")
    url: str = Field(..., description="Relative HTTP download URL")
    frames: int | None = Field(None, description="Number of frames if animated GIF")


class CaptureTriggerRequest(BaseModel):
    """Parameters for triggering a still photo or animated GIF capture."""

    type: Literal["photo", "gif"] = "photo"
    countdown_seconds: int = Field(0, ge=0, le=10, description="Countdown timer before capture in seconds")
    frames: int = Field(10, ge=3, le=30, description="Number of burst frames for GIF")
    interval_ms: int = Field(150, ge=50, le=500, description="Delay between frames in ms for GIF")


class CountdownTick(BaseModel):
    """Countdown tick broadcast to clients."""

    type: str = "countdown_tick"
    seconds_left: int
    action: Literal["photo", "gif"] = "photo"


class PrintJobRequest(BaseModel):
    """Parameters for initiating a physical print job on the Pi."""

    image_base64: str | None = Field(None, description="Base64 encoded JPEG/PNG image data")
    capture_id: str | None = Field(None, description="Existing capture ID to print")
    printer_name: str | None = Field(None, description="Printer queue name (e.g. TRC_Printer)")
    color_mode: Literal["monochrome", "color"] | None = Field(None, description="CUPS print-color-mode")
    copies: int = Field(1, ge=1, le=10, description="Number of copies to print")
    test: bool = Field(False, description="Whether to print a test document (/etc/hostname)")
    filename: str = Field("print_image.jpg", description="Spool file name")



class PrintResponse(BaseModel):
    """Result of a print submission."""

    success: bool
    message: str
    job_id: str | None = Field(None, description="CUPS print job ID if available")
    printer: str
    command: str | None = Field(None, description="Underlying shell command executed")


class PrinterStatusResponse(BaseModel):
    """Current CUPS printing subsystem status on the Pi."""

    printer_name: str
    color_mode: str
    lp_installed: bool
    available_printers: list[str] = Field(default_factory=list)
    is_ready: bool = True
    status_message: str = "ok"

