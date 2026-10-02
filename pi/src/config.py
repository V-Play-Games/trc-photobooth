"""Configuration management for the TRC Photo Booth Pi Server."""

from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Server and camera settings configurable via environment variables."""

    # Server settings
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = False

    # Camera & Capture
    # Force mock/dev camera even if picamera2 is available (useful for off-pi dev/testing)
    mock_camera: bool = False
    # Webcam device index (0 for default laptop camera, 1 for external, etc.)
    webcam_device: int = 0
    # Force synthetic test pattern instead of physical laptop webcam
    use_synthetic: bool = False
    # Manual Red/Blue channel swap override if needed for non-standard camera modules
    swap_rb: bool = False
    # Horizontal left-to-right mirror flip for natural selfie / photobooth reflection
    flip_horizontal: bool = False

    # Preview stream configuration (optimized for low latency over local WiFi)
    preview_width: int = 640
    preview_height: int = 480
    preview_fps: int = 15
    preview_quality: int = 70  # JPEG quality (1-100)

    # High-resolution capture settings (used when photo trigger is invoked)
    capture_width: int = 2592
    capture_height: int = 1944
    capture_quality: int = 95

    # Path to static assets
    static_dir: Path = Path(__file__).resolve().parent / "static"

    # CORS configuration for development
    cors_origins: list[str] = ["*"]

    model_config = SettingsConfigDict(
        env_prefix="PHOTOBOOTH_",
        env_file=".env",
        extra="ignore",
    )


# Global settings singleton
settings = Settings()
