"""Camera abstraction supporting official Raspberry Pi picamera2, laptop webcam via OpenCV, and synthetic fallback."""

from __future__ import annotations

import glob
import io
import logging
import math
import os
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
        self._target_fps: int = settings.preview_fps
        self._quality: int = settings.preview_quality
        self.width: int = settings.preview_width
        self.height: int = settings.preview_height
        self.flip_horizontal: bool = settings.flip_horizontal
        self.is_connected: bool = True
        self.last_frame_time: float = time.time()
        self.consecutive_errors: int = 0
        self._paused: bool = False

    @property
    def is_paused(self) -> bool:
        """True if camera capture loop is paused to save CPU/thermal energy."""
        return self._paused

    def pause(self) -> None:
        """Pause capture loop to allow Pi to cool down."""
        self._paused = True
        logger.info("Camera capture paused (%s)", self.backend_name)

    def resume(self) -> None:
        """Resume capture loop."""
        self._paused = False
        logger.info("Camera capture resumed (%s)", self.backend_name)

    @property
    def is_healthy(self) -> bool:
        """True if camera is actively producing frames and healthy."""
        return self._running and self.is_connected and (time.time() - self.last_frame_time < 8.0)

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
    def target_fps(self) -> int:
        """Target capture and preview frame rate."""
        return self._target_fps

    @target_fps.setter
    def target_fps(self, fps: int) -> None:
        self._target_fps = max(1, int(fps))

    @property
    def quality(self) -> int:
        """JPEG encoding quality (10-100)."""
        return self._quality

    @quality.setter
    def quality(self, val: int) -> None:
        self._quality = max(10, min(100, int(val)))

    def set_resolution(self, width: int, height: int) -> None:
        """Update preview stream resolution."""
        self.width = max(160, int(width))
        self.height = max(120, int(height))

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
        self._target_fps = target_fps
        self._quality = quality
        self.device_index = device_index
        self.use_webcam = use_webcam and not settings.use_synthetic
        self.flip_horizontal = mirror if mirror is not None else settings.flip_horizontal
        self._active_backend = "webcam" if self.use_webcam else "mock"
        self._cap = None
        self._device_lock = threading.Lock()

    def set_device(self, device: int | str) -> bool:
        """Switch active webcam device index or /dev/video* path on the fly."""
        idx = parse_device_index(device)
        self.device_index = idx
        settings.webcam_device = idx
        self.use_webcam = not settings.use_synthetic

        with self._device_lock:
            old_cap = self._cap
            self._cap = None
            if old_cap is not None:
                try:
                    old_cap.release()
                except Exception:
                    pass

            try:
                import cv2
                logger.info("Opening webcam device index %d (/dev/video%d)...", idx, idx)
                new_cap = cv2.VideoCapture(idx)
                if new_cap.isOpened():
                    new_cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.width)
                    new_cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.height)
                    new_cap.set(cv2.CAP_PROP_FPS, self.target_fps)
                    ret, test_frame = new_cap.read()
                    if ret and test_frame is not None:
                        self._cap = new_cap
                        self._active_backend = "webcam"
                        self.is_connected = True
                        self.consecutive_errors = 0
                        logger.info("Switched camera successfully to /dev/video%d (%dx%d)", idx, self.width, self.height)
                        return True
                    else:
                        logger.warning("Webcam /dev/video%d opened but failed to read frames", idx)
                        new_cap.release()
                else:
                    logger.warning("Unable to open webcam device /dev/video%d", idx)
            except Exception as exc:
                logger.error("Error opening /dev/video%d: %s", idx, exc)

            self._active_backend = "mock"
            self.is_connected = False
            return False

    @property
    def mirror(self) -> bool:
        """Alias for flip_horizontal for backwards compatibility."""
        return self.flip_horizontal

    @mirror.setter
    def mirror(self, val: bool) -> None:
        self.flip_horizontal = bool(val)

    @BaseCamera.target_fps.setter
    def target_fps(self, fps: int) -> None:
        self._target_fps = max(1, int(fps))
        if self._cap is not None:
            try:
                import cv2
                self._cap.set(cv2.CAP_PROP_FPS, float(self._target_fps))
            except Exception:
                pass

    def set_resolution(self, width: int, height: int) -> None:
        """Update preview resolution for webcam scaling or synthetic pattern."""
        self.width = max(160, int(width))
        self.height = max(120, int(height))
        if self._cap is not None:
            try:
                import cv2
                self._cap.set(cv2.CAP_PROP_FRAME_WIDTH, float(self.width))
                self._cap.set(cv2.CAP_PROP_FRAME_HEIGHT, float(self.height))
            except Exception:
                pass
        logger.info("MockCamera resolution updated to %dx%d", self.width, self.height)

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

        # Invert left-to-right if horizontal flip (mirror) is requested
        if self.flip_horizontal:
            img = img.transpose(Image.FLIP_LEFT_RIGHT)

        # Encode to JPEG
        buffer = io.BytesIO()
        img.save(buffer, format="JPEG", quality=self.quality, optimize=True)
        return buffer.getvalue()

    def _capture_loop(self) -> None:
        """Capture loop with OpenCV webcam input and synthetic fallback."""
        cv2 = None

        if self.use_webcam:
            try:
                import cv2 as cv_module
                cv2 = cv_module
                self.set_device(self.device_index)
            except Exception as exc:
                logger.warning(
                    "OpenCV webcam initialization failed (%s). Falling back to synthetic pattern.",
                    exc,
                )

        if self._cap is None:
            self._active_backend = "mock"
            logger.info("Using synthetic photo booth test pattern generator")

        last_probe_time = 0.0

        while self._running:
            if self._paused:
                time.sleep(0.08)
                continue

            loop_start = time.time()
            self._frame_count += 1

            jpeg_bytes = None

            with self._device_lock:
                cap = self._cap

            if cap is not None and cv2 is not None:
                try:
                    ret, frame = cap.read()
                    if ret and frame is not None:
                        self.consecutive_errors = 0
                        self.is_connected = True
                        # Mirror horizontally for natural photo booth reflection
                        if self.flip_horizontal:
                            frame = cv2.flip(frame, 1)

                        # Resize if webcam resolution differs from requested preview dimensions
                        h, w = frame.shape[:2]
                        if w != self.width or h != self.height:
                            frame = cv2.resize(
                                frame, (self.width, self.height), interpolation=cv2.INTER_LINEAR
                            )

                        # Optional Red/Blue channel swap
                        if settings.swap_rb:
                            frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

                        # Hardware-accelerated JPEG encoding via OpenCV
                        encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), int(self.quality)]
                        success, encimg = cv2.imencode(".jpg", frame, encode_param)
                        if success:
                            jpeg_bytes = encimg.tobytes()
                    else:
                        self.consecutive_errors += 1
                        if self.consecutive_errors >= 5:
                            logger.warning(
                                "Webcam /dev/video%d disconnected or failed to return frames. "
                                "Falling back to synthetic pattern while probing for reconnection...",
                                self.device_index,
                            )
                            self._active_backend = "mock"
                            self.is_connected = False
                            with self._device_lock:
                                if self._cap is cap:
                                    try:
                                        self._cap.release()
                                    except Exception:
                                        pass
                                    self._cap = None
                except Exception as exc:
                    self.consecutive_errors += 1
                    logger.error("Error reading from webcam: %s (consecutive errors: %d)", exc, self.consecutive_errors)
                    if self.consecutive_errors >= 5:
                        self._active_backend = "mock"
                        self.is_connected = False
                        with self._device_lock:
                            if self._cap is cap:
                                try:
                                    self._cap.release()
                                except Exception:
                                    pass
                                self._cap = None

            # Periodic webcam reconnection probe when disconnected
            if self._cap is None and self.use_webcam and cv2 is not None:
                now_probe = time.time()
                if now_probe - last_probe_time > 3.0:
                    last_probe_time = now_probe
                    try:
                        probe_cap = cv2.VideoCapture(self.device_index)
                        if probe_cap.isOpened():
                            ret_probe, test_probe = probe_cap.read()
                            if ret_probe and test_probe is not None:
                                logger.info(
                                    "Webcam /dev/video%d reconnected successfully!",
                                    self.device_index,
                                )
                                probe_cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.width)
                                probe_cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.height)
                                probe_cap.set(cv2.CAP_PROP_FPS, self.target_fps)
                                with self._device_lock:
                                    self._cap = probe_cap
                                    self._active_backend = "webcam"
                                    self.is_connected = True
                                    self.consecutive_errors = 0
                            else:
                                probe_cap.release()
                        else:
                            probe_cap.release()
                    except Exception:
                        pass

            if jpeg_bytes is None:
                # Fallback to synthetic frame generator
                jpeg_bytes = self._generate_frame(self._frame_count)

            with self._lock:
                self._latest_jpeg = jpeg_bytes
                self.last_frame_time = time.time()

            self._update_fps()

            elapsed = time.time() - loop_start
            interval = 1.0 / max(1, self.target_fps)
            sleep_time = interval - elapsed
            if sleep_time > 0:
                time.sleep(sleep_time)

        with self._device_lock:
            if self._cap is not None:
                try:
                    self._cap.release()
                except Exception:
                    pass
                self._cap = None

    def capture_high_res(self) -> bytes:
        """Capture a high-quality still frame from webcam or synthetic fallback."""
        with self._device_lock:
            cap = self._cap
        if cap is not None and cap.isOpened():
            try:
                import cv2

                ret, frame = cap.read()
                if ret and frame is not None:
                    if self.flip_horizontal:
                        frame = cv2.flip(frame, 1)
                    if settings.swap_rb:
                        frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
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
        if self.flip_horizontal:
            img = img.transpose(Image.FLIP_LEFT_RIGHT)
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
        self._target_fps = target_fps
        self._quality = quality
        self._picam2 = None
        self._last_dark_warn_time = 0.0
        self._pending_reconfigure: tuple[int, int] | None = None
        self._picam2_lock = threading.Lock()

    def _get_sensor_mode(self, req_w: int, req_h: int, fps: int) -> tuple[int, int]:
        """Select optimal uncropped hardware sensor readout mode to prevent optical zoom-in.

        For OV5647 (5MP 4:3 native):
          Mode 0: 640x480   @ 62.5 fps (crop: 16, 0, 2560, 1920 -> 98.8% full FOV, high-speed 60fps)
          Mode 1: 1296x972  @ 46.3 fps (crop: 0, 0, 2592, 1944  -> 100.0% FULL FOV, 2x2 binned)
          Mode 2: 1920x1080 @ 32.8 fps (crop: 348, 434, 1928, 1080 -> 41.3% CROP - NEVER auto-select!)
          Mode 3: 2592x1944 @ 15.6 fps (crop: 0, 0, 2592, 1944  -> 100.0% FULL FOV, full 5MP)
        """
        if not self._picam2 or not hasattr(self._picam2, "sensor_modes") or not self._picam2.sensor_modes:
            return (1296, 972)

        # 1. High framerate (> 45 fps, e.g. 60 fps):
        # Use high-speed uncropped Mode 0 (640x480 @ 62.5 fps)
        if fps > 45:
            for mode in self._picam2.sensor_modes:
                if mode.get("fps", 0) >= 55.0:
                    return mode["size"]
            return (640, 480)

        # 2. High resolution (> 1296x972, e.g. 1440x1080 or full 5MP):
        # Use full 5MP uncropped Mode 3 (2592x1944)
        if req_w > 1296 or req_h > 972:
            max_w, max_h = 2592, 1944
            for mode in self._picam2.sensor_modes:
                mw, mh = mode.get("size", (0, 0))
                if mw * mh > max_w * max_h:
                    max_w, max_h = mw, mh
            return (max_w, max_h)

        # 3. Standard / 720p 4:3 preview (e.g. 960x720, 640x480):
        # Use uncropped Mode 1 (1296x972, 2x2 binned, 0 crop, up to 46 fps).
        # The Broadcom hardware ISP scales this down to 960x720 (or 640x480) with 100% full FOV.
        for mode in self._picam2.sensor_modes:
            mw, mh = mode.get("size", (0, 0))
            cl = mode.get("crop_limits")
            if cl and len(cl) == 4:
                cx, cy, cw, ch = cl
                if cw >= 2560 and ch >= 1920 and mw < 2592 and mode.get("fps", 0) >= fps:
                    return (mw, mh)

        return (1296, 972)

    @BaseCamera.target_fps.setter
    def target_fps(self, fps: int) -> None:
        self._target_fps = max(1, int(fps))
        if self._picam2 is not None:
            try:
                duration_us = int(1_000_000 / self._target_fps)
                self._picam2.set_controls({
                    "FrameRate": float(self._target_fps),
                    "FrameDurationLimits": (duration_us, duration_us),
                })
                logger.info(
                    "Updated picamera2 hardware FrameRate control to %d fps (%d us)",
                    self._target_fps,
                    duration_us,
                )
            except Exception as exc:
                logger.warning("Could not set picamera2 FrameRate control: %s", exc)

    def set_resolution(self, width: int, height: int) -> None:
        """Update preview resolution and schedule hardware reconfiguration."""
        w = max(160, int(width))
        h = max(120, int(height))
        self.width = w
        self.height = h
        self._pending_reconfigure = (w, h)
        logger.info("PiCamera preview resolution updated to %dx%d (reconfiguration scheduled)", w, h)

    @property
    def backend_name(self) -> str:
        return "picamera2"

    @property
    def is_mock(self) -> bool:
        return False

    def _initialize_hardware(self) -> None:
        from picamera2 import Picamera2

        self._picam2 = Picamera2()
        fps = max(1, self.target_fps)
        duration_us = int(1_000_000 / fps)
        sensor_size = self._get_sensor_mode(self.width, self.height, fps)
        logger.info(
            "picamera2 hardware mode selected: sensor output_size=%s for preview %dx%d @ %d fps",
            sensor_size,
            self.width,
            self.height,
            fps,
        )
        video_config = self._picam2.create_video_configuration(
            main={"size": (self.width, self.height), "format": "RGB888"},
            sensor={"output_size": sensor_size},
            controls={
                "FrameRate": float(fps),
                "FrameDurationLimits": (duration_us, duration_us),
            },
        )
        self._picam2.configure(video_config)
        self._picam2.start()
        logger.info("picamera2 hardware started at %d fps (%d us). Warming up AGC/AEC...", fps, duration_us)

        # Discard initial calibration frames so Auto-Exposure (AEC) and Auto-Gain (AGC)
        # can adapt to ambient lighting (OV5647 starts with 0 exposure)
        for _ in range(12):
            try:
                self._picam2.capture_array("main")
            except Exception:
                pass
            time.sleep(0.04)
        logger.info("picamera2 sensor warmup complete")

    def _generate_disconnect_frame(self, frame_idx: int) -> bytes:
        """Render a diagnostic fallback frame when Pi Camera is disconnected."""
        img = Image.new("RGB", (self.width, self.height), color=(30, 16, 20))
        draw = ImageDraw.Draw(img)

        cx, cy = self.width // 2, self.height // 2
        box_w, box_h = min(self.width - 40, 480), 140
        x1, y1 = cx - box_w // 2, cy - box_h // 2
        x2, y2 = cx + box_w // 2, cy + box_h // 2

        pulse_color = (255, 60, 60) if (frame_idx // 5) % 2 == 0 else (255, 180, 0)
        draw.rectangle([(x1, y1), (x2, y2)], outline=pulse_color, width=3, fill=(45, 20, 26))

        draw.text((x1 + 16, y1 + 20), "PI CAMERA HARDWARE DISCONNECTED", fill=(255, 255, 255))
        draw.text((x1 + 16, y1 + 50), "Check CSI ribbon cable / USB connection.", fill=(220, 220, 220))
        draw.text((x1 + 16, y1 + 80), f"Auto-reconnecting... (probe #{frame_idx // 15 + 1})", fill=pulse_color)

        if self.flip_horizontal:
            img = img.transpose(Image.FLIP_LEFT_RIGHT)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=self.quality)
        return buf.getvalue()

    def _capture_loop(self) -> None:
        try:
            self._initialize_hardware()
            self.is_connected = True
        except Exception as exc:
            logger.error("Failed initial picamera2 startup (%s). Entering auto-reconnect probe loop...", exc)
            self.is_connected = False
            self._picam2 = None

        cv2 = None
        try:
            import cv2 as cv_mod
            cv2 = cv_mod
        except ImportError:
            pass

        last_reconnect_attempt = 0.0

        while self._running:
            if self._paused:
                time.sleep(0.08)
                continue

            loop_start = time.time()
            self._frame_count += 1

            if not self.is_connected or self._picam2 is None:
                now_recon = time.time()
                if now_recon - last_reconnect_attempt > 3.0:
                    last_reconnect_attempt = now_recon
                    logger.info("Probing picamera2 hardware reconnection...")
                    try:
                        if self._picam2 is not None:
                            try:
                                self._picam2.stop()
                                self._picam2.close()
                            except Exception:
                                pass
                        self._initialize_hardware()
                        self.is_connected = True
                        self.consecutive_errors = 0
                        logger.info("picamera2 hardware reconnected successfully!")
                    except Exception as re_err:
                        logger.debug("picamera2 reconnection probe failed: %s", re_err)

                if not self.is_connected:
                    jpeg_bytes = self._generate_disconnect_frame(self._frame_count)
                    with self._lock:
                        self._latest_jpeg = jpeg_bytes
                        self.last_frame_time = time.time()
                    self._update_fps()
                    time.sleep(0.1)
                    continue

            if self._pending_reconfigure is not None:
                try:
                    req_w, req_h = self._pending_reconfigure
                    self._pending_reconfigure = None
                    if self._picam2 is not None:
                        logger.info("Reconfiguring picamera2 hardware to %dx%d...", req_w, req_h)
                        with self._picam2_lock:
                            self._picam2.stop()
                            duration_us = int(1_000_000 / max(1, self.target_fps))
                            sensor_size = self._get_sensor_mode(req_w, req_h, self.target_fps)
                            logger.info(
                                "picamera2 reconfigure: sensor output_size=%s for %dx%d @ %d fps",
                                sensor_size,
                                req_w,
                                req_h,
                                self.target_fps,
                            )
                            video_config = self._picam2.create_video_configuration(
                                main={"size": (req_w, req_h), "format": "RGB888"},
                                sensor={"output_size": sensor_size},
                                controls={
                                    "FrameRate": float(self.target_fps),
                                    "FrameDurationLimits": (duration_us, duration_us),
                                },
                            )
                            self._picam2.configure(video_config)
                            self._picam2.start()
                        logger.info("picamera2 hardware reconfigured to %dx%d", req_w, req_h)
                except Exception as reconf_exc:
                    logger.warning("picamera2 hardware reconfigure failed (%s), using software resize", reconf_exc)

            try:
                # Capture frame array from picamera2 (returns RGB)
                with self._picam2_lock:
                    if not self._picam2 or not self.is_connected:
                        continue
                    frame_arr = self._picam2.capture_array("main")
                self.consecutive_errors = 0
                self.is_connected = True

                # Resize if frame dimensions differ from target preview resolution
                h_cur, w_cur = frame_arr.shape[:2]
                if cv2 is not None and (w_cur != self.width or h_cur != self.height):
                    frame_arr = cv2.resize(frame_arr, (self.width, self.height), interpolation=cv2.INTER_LINEAR)

                # Diagnostic check for pitch-black frames (e.g. lens cap or cable fault)
                mean_brightness = float(frame_arr.mean()) if hasattr(frame_arr, "mean") else 100.0
                now = time.time()
                if mean_brightness < 2.0 and (now - self._last_dark_warn_time) > 5.0:
                    logger.warning(
                        "Captured frame is completely dark (mean brightness %.1f/255). "
                        "If you see black, verify room lighting, lens cap, or ribbon cable.",
                        mean_brightness,
                    )
                    self._last_dark_warn_time = now

                # Fast encoding: picamera2 outputs in standard memory order matching OpenCV (BGR)
                if cv2 is not None:
                    if settings.swap_rb:
                        bgr_frame = cv2.cvtColor(frame_arr, cv2.COLOR_RGB2BGR)
                    else:
                        bgr_frame = frame_arr
                    if self.flip_horizontal:
                        bgr_frame = cv2.flip(bgr_frame, 1)
                    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), int(self.quality)]
                    ret, encimg = cv2.imencode(".jpg", bgr_frame, encode_param)
                    if ret:
                        jpeg_bytes = encimg.tobytes()
                    else:
                        raise RuntimeError("cv2.imencode failed on picamera2 frame")
                else:
                    # Fallback to Pillow (Image.fromarray natively expects RGB)
                    if settings.swap_rb:
                        frame_to_img = frame_arr
                    else:
                        frame_to_img = frame_arr[..., ::-1]
                    img = Image.fromarray(frame_to_img)
                    if self.flip_horizontal:
                        img = img.transpose(Image.FLIP_LEFT_RIGHT)
                    buf = io.BytesIO()
                    img.save(buf, format="JPEG", quality=self.quality)
                    jpeg_bytes = buf.getvalue()

                with self._lock:
                    self._latest_jpeg = jpeg_bytes
                    self.last_frame_time = time.time()

                self._update_fps()
            except Exception as exc:
                self.consecutive_errors += 1
                logger.error("Error capturing frame from picamera2: %s (consecutive errors: %d)", exc, self.consecutive_errors)
                if self.consecutive_errors >= 5:
                    logger.warning("picamera2 repeated capture failures. Marking disconnected to trigger recovery.")
                    self.is_connected = False
                    if self._picam2 is not None:
                        try:
                            self._picam2.stop()
                            self._picam2.close()
                        except Exception:
                            pass
                        self._picam2 = None
                time.sleep(0.05)

            elapsed = time.time() - loop_start
            interval = 1.0 / max(1, self.target_fps)
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
        if not self._picam2 or not self.is_connected:
            logger.warning("picamera2 not ready or disconnected during high-res capture; using fallback frame")
            latest = self.get_latest_frame()
            if latest:
                return latest
            w, h = settings.capture_width, settings.capture_height
            img = Image.new("RGB", (w, h), color=(30, 16, 20))
            draw = ImageDraw.Draw(img)
            draw.rectangle([(40, 40), (w - 40, h - 40)], outline=(255, 60, 60), width=8)
            draw.text(
                (w // 2 - 240, h // 2 - 20),
                f"CAMERA DISCONNECTED ({w}x{h})\nTRC PHOTO BOOTH RECONNECTING...",
                fill=(255, 255, 255),
            )
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=settings.capture_quality)
            return buf.getvalue()

        # Select maximum uncropped sensor resolution (5MP 2592x1944 on OV5647)
        max_w, max_h = settings.capture_width, settings.capture_height
        if self._picam2 and hasattr(self._picam2, "sensor_modes"):
            for mode in self._picam2.sensor_modes:
                mw, mh = mode.get("size", (0, 0))
                if mw * mh > max_w * max_h:
                    max_w, max_h = mw, mh

        try:
            with self._picam2_lock:
                capture_config = self._picam2.create_still_configuration(
                    main={"size": (max_w, max_h), "format": "RGB888"},
                    sensor={"output_size": (max_w, max_h)},
                )
                frame_arr = self._picam2.switch_mode_and_capture_array(capture_config)
        except Exception as exc:
            logger.warning("switch_mode_and_capture_array failed (%s), falling back to capture_array", exc)
            with self._picam2_lock:
                frame_arr = self._picam2.capture_array("main")
        try:
            import cv2
            if settings.swap_rb:
                bgr_frame = cv2.cvtColor(frame_arr, cv2.COLOR_RGB2BGR)
            else:
                bgr_frame = frame_arr
            if self.flip_horizontal:
                bgr_frame = cv2.flip(bgr_frame, 1)
            encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), int(settings.capture_quality)]
            ret, encimg = cv2.imencode(".jpg", bgr_frame, encode_param)
            if ret:
                return encimg.tobytes()
        except ImportError:
            pass

        if settings.swap_rb:
            frame_to_img = frame_arr
        else:
            frame_to_img = frame_arr[..., ::-1]
        img = Image.fromarray(frame_to_img)
        if self.flip_horizontal:
            img = img.transpose(Image.FLIP_LEFT_RIGHT)
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


def parse_device_index(val: int | str) -> int:
    """Parse device index from integer, string digit, or /dev/videoX path."""
    if isinstance(val, int):
        return max(0, val)
    s = str(val).strip()
    if s.startswith("/dev/video"):
        num = s.replace("/dev/video", "")
        if num.isdigit():
            return int(num)
    if s.isdigit():
        return int(s)
    return 0


def list_video_devices() -> list[dict[str, Any]]:
    """Scan and list available /dev/video* devices on the system with friendly hardware names."""
    devices: list[dict[str, Any]] = []
    video_paths = sorted(
        glob.glob("/dev/video*"),
        key=lambda p: int(p.replace("/dev/video", "")) if p.replace("/dev/video", "").isdigit() else 999,
    )

    for p in video_paths:
        idx_str = p.replace("/dev/video", "")
        if not idx_str.isdigit():
            continue
        idx = int(idx_str)
        sys_name_file = f"/sys/class/video4linux/video{idx}/name"
        name = f"Camera {idx}"
        if os.path.exists(sys_name_file):
            try:
                with open(sys_name_file, "r") as f:
                    content = f.read().strip()
                    if content:
                        name = content
            except Exception:
                pass

        devices.append({
            "device": p,
            "index": idx,
            "name": name,
            "available": True,
        })

    if not devices:
        devices.append({
            "device": "/dev/video0",
            "index": 0,
            "name": "Default Camera (/dev/video0)",
            "available": False,
        })

    return devices


def switch_camera_device(device: int | str) -> bool:
    """Dynamically switch active camera hardware device across PiCamera / MockCamera."""
    global _camera_instance
    idx = parse_device_index(device)
    settings.webcam_device = idx

    with _camera_lock:
        if isinstance(_camera_instance, MockCamera):
            return _camera_instance.set_device(idx)
        elif _camera_instance is not None:
            try:
                _camera_instance.stop()
            except Exception:
                pass
            _camera_instance = MockCamera(device_index=idx)
            _camera_instance.start()
            return True
        else:
            _camera_instance = MockCamera(device_index=idx)
            _camera_instance.start()
            return True
