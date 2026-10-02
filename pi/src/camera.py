"""Camera abstraction supporting official Raspberry Pi picamera2, laptop webcam via OpenCV, and synthetic fallback."""

from __future__ import annotations

import io
import math
import logging
import threading
import time
from abc import ABC, abstractmethod
from datetime import datetime
from PIL import Image, ImageDraw

from src.config import settings

logger = logging.getLogger("photobooth.camera")


class BaseCamera(ABC):
    """Abstract base class for camera capture."""

    def __init__(self) -> None:
        self._running: bool = False
        self._thread: threading.Thread | None = None
        self._lock = threading.Lock()
        self._latest_jpeg: bytes | None = None
        self._frame_count: int = 0
        self._start_time: float = 0.0
        self._actual_fps: float = 0.0
        self._last_fps_calc_time: float = 0.0
        self._last_fps_frame_count: int = 0

    @property
    @abstractmethod
    def backend_name(self) -> str:
        """Name of the camera backend implementation."""

    @property
    @abstractmethod
    def is_mock(self) -> bool:
        """Whether this is a non-Pi mock/development camera."""

    @property
    def actual_fps(self) -> float:
        """Measured actual frame rate."""
        return self._actual_fps

    @property
    def is_running(self) -> bool:
        return self._running

    def start(self) -> None:
        """Start the background frame capture loop."""
        with self._lock:
            if self._running:
                return
            self._running = True
            self._start_time = time.time()
            self._last_fps_calc_time = self._start_time
            self._thread = threading.Thread(target=self._capture_loop, daemon=True)
            self._thread.start()
            logger.info("Camera background capture started using %s", self.backend_name)

    def stop(self) -> None:
        """Stop the background frame capture loop."""
        with self._lock:
            if not self._running:
                return
            self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2.0)
        logger.info("Camera background capture stopped (%s)", self.backend_name)

    def get_latest_frame(self) -> bytes | None:
        """Return the most recently captured JPEG bytes (thread-safe)."""
        with self._lock:
            return self._latest_jpeg

    def _update_fps(self) -> None:
        """Helper to periodically compute smoothed rolling FPS."""
        now = time.time()
        elapsed = now - self._last_fps_calc_time
        if elapsed >= 1.0:
            frames = self._frame_count - self._last_fps_frame_count
            self._actual_fps = round(frames / elapsed, 1)
            self._last_fps_calc_time = now
            self._last_fps_frame_count = self._frame_count

    @abstractmethod
    def _capture_loop(self) -> None:
        """Internal worker method running in background thread."""

    @abstractmethod
    def capture_high_res(self) -> bytes:
        """Take a single high-resolution still capture (JPEG)."""


class MockCamera(BaseCamera):
    """Development camera that captures live video from the laptop webcam using OpenCV.

    If no webcam is accessible or use_synthetic is requested, gracefully falls back
    to generating realistic animated test patterns.
    """

    def __init__(
        self,
        width: int = settings.preview_width,
        height: int = settings.preview_height,
        target_fps: int = settings.preview_fps,
        quality: int = settings.preview_quality,
        device_index: int = settings.webcam_device,
        use_webcam: bool = True,
        mirror: bool = True,
    ) -> None:
        super().__init__()
        self.width = width
        self.height = height
        self.target_fps = target_fps
        self.quality = quality
        self.device_index = device_index
        self.use_webcam = use_webcam and not settings.use_synthetic
        self.mirror = mirror
        self._active_backend = "webcam" if self.use_webcam else "mock"
        self._cap = None

    @property
    def backend_name(self) -> str:
        return self._active_backend

    @property
    def is_mock(self) -> bool:
        return True

    def _generate_frame(self, frame_idx: int) -> bytes:
        """Generate a simulated camera frame with moving animations and overlays."""
        # Create studio backdrop
        img = Image.new("RGB", (self.width, self.height), color=(20, 24, 34))
        draw = ImageDraw.Draw(img)

        # Draw a retro photobooth grid pattern
        grid_spacing = 40
        for x in range(0, self.width, grid_spacing):
            draw.line([(x, 0), (x, self.height)], fill=(28, 34, 48), width=1)
        for y in range(0, self.height, grid_spacing):
            draw.line([(0, y), (self.width, y)], fill=(28, 34, 48), width=1)

        # Moving circular radar / target animation
        center_x = self.width // 2
        center_y = self.height // 2
        angle = (frame_idx * 5) % 360
        rad = math.radians(angle)
        orbit_r = min(self.width, self.height) // 4
        anim_x = int(center_x + orbit_r * math.cos(rad))
        anim_y = int(center_y + orbit_r * math.sin(rad))

        # Face framing guide box (Photo booth guide)
        guide_w, guide_h = int(self.width * 0.45), int(self.height * 0.55)
        gx1 = center_x - guide_w // 2
        gy1 = center_y - guide_h // 2
        gx2 = center_x + guide_w // 2
        gy2 = center_y + guide_h // 2

        # Draw framing box corners
        corner_len = 24
        box_color = (0, 217, 165)
        draw.line([(gx1, gy1), (gx1 + corner_len, gy1)], fill=box_color, width=3)
        draw.line([(gx1, gy1), (gx1, gy1 + corner_len)], fill=box_color, width=3)
        draw.line([(gx2, gy1), (gx2 - corner_len, gy1)], fill=box_color, width=3)
        draw.line([(gx2, gy1), (gx2 - corner_len, gy1)], fill=box_color, width=3)
        draw.line([(gx1, gy2), (gx1 + corner_len, gy2)], fill=box_color, width=3)
        draw.line([(gx1, gy2), (gx1, gy2 - corner_len)], fill=box_color, width=3)
        draw.line([(gx2, gy2), (gx2 - corner_len, gy2)], fill=box_color, width=3)
        draw.line([(gx2, gy2), (gx2, gy2 - corner_len)], fill=box_color, width=3)

        # Moving tracking dot
        dot_r = 14
        draw.ellipse(
            [(anim_x - dot_r, anim_y - dot_r), (anim_x + dot_r, anim_y + dot_r)],
            fill=(255, 64, 129),
            outline=(255, 255, 255),
            width=2,
        )

        # Center crosshair
        ch_size = 12
        draw.line(
            [(center_x - ch_size, center_y), (center_x + ch_size, center_y)],
            fill=(255, 255, 255),
            width=1,
        )
        draw.line(
            [(center_x, center_y - ch_size), (center_x, center_y + ch_size)],
            fill=(255, 255, 255),
            width=1,
        )

        # Bottom SMPTE color bar preview
        bar_colors = [
            (255, 255, 255),
            (255, 255, 0),
            (0, 255, 255),
            (0, 255, 0),
            (255, 0, 255),
            (255, 0, 0),
            (0, 0, 255),
            (0, 0, 0),
        ]
        bar_h = 16
        bar_w = self.width / len(bar_colors)
        for i, color in enumerate(bar_colors):
            x1 = int(i * bar_w)
            x2 = int((i + 1) * bar_w)
            draw.rectangle([(x1, self.height - bar_h), (x2, self.height)], fill=color)

        # Text overlay
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        header = f"TRC PHOTOBOOTH [MOCK CAMERA] • {self.width}x{self.height} @ {self.target_fps}fps"
        sub = f"Frame: #{frame_idx:06d}  Time: {now_str}  Live FPS: {self.actual_fps:.1f}"

        draw.rectangle([(0, 0), (self.width, 36)], fill=(12, 14, 20))
        draw.text((10, 4), header, fill=(255, 204, 0))
        draw.text((10, 18), sub, fill=(200, 210, 230))

        # Encode to JPEG
        buffer = io.BytesIO()
        img.save(buffer, format="JPEG", quality=self.quality, optimize=True)
        return buffer.getvalue()

    def _capture_loop(self) -> None:
        """Capture loop with OpenCV webcam input and synthetic fallback."""
        interval = 1.0 / max(1, self.target_fps)
        has_webcam = False
        cv2 = None

        if self.use_webcam:
            try:
                import cv2 as cv_module
                cv2 = cv_module

                logger.info(
                    "Opening webcam device index %d (/dev/video%d)...",
                    self.device_index,
                    self.device_index,
                )
                self._cap = cv2.VideoCapture(self.device_index)

                if self._cap.isOpened():
                    self._cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.width)
                    self._cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.height)
                    self._cap.set(cv2.CAP_PROP_FPS, self.target_fps)

                    # Test initial frame read
                    ret, test_frame = self._cap.read()
                    if ret and test_frame is not None:
                        has_webcam = True
                        self._active_backend = "webcam"
                        logger.info(
                            "Laptop webcam active (%dx%d, source resolution: %dx%d)",
                            self.width,
                            self.height,
                            test_frame.shape[1],
                            test_frame.shape[0],
                        )
                    else:
                        logger.warning(
                            "Webcam /dev/video%d opened but failed to read frames. Falling back to synthetic pattern.",
                            self.device_index,
                        )
                        self._cap.release()
                        self._cap = None
                else:
                    logger.warning(
                        "Unable to open webcam device %d. Falling back to synthetic pattern.",
                        self.device_index,
                    )
                    self._cap = None
            except Exception as exc:
                logger.warning(
                    "OpenCV webcam initialization failed (%s). Falling back to synthetic pattern.",
                    exc,
                )
                self._cap = None

        if not has_webcam:
            self._active_backend = "mock"
            logger.info("Using synthetic photo booth test pattern generator")

        while self._running:
            loop_start = time.time()
            self._frame_count += 1

            jpeg_bytes = None

            if has_webcam and self._cap is not None and cv2 is not None:
                try:
                    ret, frame = self._cap.read()
                    if ret and frame is not None:
                        # Mirror horizontally for natural photo booth mirror reflection
                        if self.mirror:
                            frame = cv2.flip(frame, 1)

                        # Resize if webcam resolution differs from requested preview dimensions
                        h, w = frame.shape[:2]
                        if w != self.width or h != self.height:
                            frame = cv2.resize(
                                frame, (self.width, self.height), interpolation=cv2.INTER_LINEAR
                            )

                        # Hardware-accelerated JPEG encoding via OpenCV
                        encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), int(self.quality)]
                        success, encimg = cv2.imencode(".jpg", frame, encode_param)
                        if success:
                            jpeg_bytes = encimg.tobytes()
                except Exception as exc:
                    logger.error("Error reading from webcam: %s", exc)

            if jpeg_bytes is None:
                # Fallback to synthetic frame generator
                jpeg_bytes = self._generate_frame(self._frame_count)

            with self._lock:
                self._latest_jpeg = jpeg_bytes

            self._update_fps()

            elapsed = time.time() - loop_start
            sleep_time = interval - elapsed
            if sleep_time > 0:
                time.sleep(sleep_time)

        if self._cap is not None:
            try:
                self._cap.release()
            except Exception:
                pass
            self._cap = None

    def capture_high_res(self) -> bytes:
        """Capture a high-quality still frame from webcam or synthetic fallback."""
        if self._cap is not None and self._cap.isOpened():
            try:
                import cv2

                ret, frame = self._cap.read()
                if ret and frame is not None:
                    if self.mirror:
                        frame = cv2.flip(frame, 1)
                    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), int(settings.capture_quality)]
                    success, encimg = cv2.imencode(".jpg", frame, encode_param)
                    if success:
                        return encimg.tobytes()
            except Exception as exc:
                logger.warning("Webcam high-res capture failed: %s", exc)

        # Fallback to high-res synthetic capture
        w, h = settings.capture_width, settings.capture_height
        img = Image.new("RGB", (w, h), color=(30, 36, 50))
        draw = ImageDraw.Draw(img)
        draw.rectangle([(40, 40), (w - 40, h - 40)], outline=(0, 217, 165), width=8)
        draw.text(
            (w // 2 - 200, h // 2 - 20),
            f"HIGH-RES CAPTURE ({w}x{h})\nTRC PHOTO BOOTH",
            fill=(255, 255, 255),
        )
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=settings.capture_quality)
        return buf.getvalue()


class PiCamera(BaseCamera):
    """Raspberry Pi Camera Module implementation using picamera2 / libcamera."""

    def __init__(
        self,
        width: int = settings.preview_width,
        height: int = settings.preview_height,
        target_fps: int = settings.preview_fps,
        quality: int = settings.preview_quality,
    ) -> None:
        super().__init__()
        self.width = width
        self.height = height
        self.target_fps = target_fps
        self.quality = quality
        self._picam2 = None

    @property
    def backend_name(self) -> str:
        return "picamera2"

    @property
    def is_mock(self) -> bool:
        return False

    def _initialize_hardware(self) -> None:
        from picamera2 import Picamera2

        self._picam2 = Picamera2()
        preview_config = self._picam2.create_preview_configuration(
            main={"size": (self.width, self.height), "format": "RGB888"},
            controls={"FrameRate": self.target_fps},
        )
        self._picam2.configure(preview_config)
        self._picam2.start()
        logger.info("picamera2 hardware initialized successfully")

    def _capture_loop(self) -> None:
        try:
            self._initialize_hardware()
        except Exception as exc:
            logger.error("Failed to initialize picamera2: %s", exc)
            self._running = False
            return

        interval = 1.0 / max(1, self.target_fps)
        while self._running:
            loop_start = time.time()
            self._frame_count += 1

            try:
                # Capture frame array from picamera2
                frame_arr = self._picam2.capture_array("main")
                img = Image.fromarray(frame_arr)
                buf = io.BytesIO()
                img.save(buf, format="JPEG", quality=self.quality)
                jpeg_bytes = buf.getvalue()

                with self._lock:
                    self._latest_jpeg = jpeg_bytes

                self._update_fps()
            except Exception as exc:
                logger.error("Error capturing frame from picamera2: %s", exc)
                time.sleep(0.1)

            elapsed = time.time() - loop_start
            sleep_time = interval - elapsed
            if sleep_time > 0:
                time.sleep(sleep_time)

        if self._picam2:
            try:
                self._picam2.stop()
                self._picam2.close()
            except Exception:
                pass

    def capture_high_res(self) -> bytes:
        if not self._picam2:
            raise RuntimeError("Camera hardware not active")
        capture_config = self._picam2.create_still_configuration(
            main={"size": (settings.capture_width, settings.capture_height), "format": "RGB888"}
        )
        frame_arr = self._picam2.capture_array(capture_config)
        img = Image.fromarray(frame_arr)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=settings.capture_quality)
        return buf.getvalue()


# Camera singleton and factory
_camera_instance: BaseCamera | None = None
_camera_lock = threading.Lock()


def get_camera() -> BaseCamera:
    """Retrieve or instantiate the camera singleton.

    Priority:
    1. If not settings.mock_camera, attempt PiCamera (picamera2).
    2. Fall back to MockCamera (which captures from laptop webcam via OpenCV,
       or falls back to synthetic pattern if no webcam is connected).
    """
    global _camera_instance
    with _camera_lock:
        if _camera_instance is not None:
            return _camera_instance

        if settings.mock_camera:
            logger.info("PHOTOBOOTH_MOCK_CAMERA is set: using MockCamera (laptop webcam / synthetic)")
            _camera_instance = MockCamera()
            return _camera_instance

        # Attempt to use picamera2 on Raspberry Pi
        try:
            import picamera2  # noqa: F401

            logger.info("picamera2 is installed, attempting hardware initialization...")
            _camera_instance = PiCamera()
        except (ImportError, Exception) as exc:
            logger.info(
                "picamera2 not available (%s). Using MockCamera with laptop webcam support.",
                exc,
            )
            _camera_instance = MockCamera()

        return _camera_instance
