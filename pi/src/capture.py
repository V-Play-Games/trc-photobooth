"""Photo booth capture engine for high-resolution stills, multi-frame animated GIFs, and capture storage."""

from __future__ import annotations

import asyncio
import io
import logging
import threading
import time
import uuid
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime
from PIL import Image

from src.camera import BaseCamera, get_camera
from src.config import settings
from src.models import CaptureMetadata

logger = logging.getLogger("photobooth.capture")


@dataclass
class CaptureItem:
    """In-memory representation of a captured photo or animated GIF."""

    id: str
    content_type: str
    data: bytes
    filename: str
    width: int
    height: int
    created_at: float
    capture_type: str  # "photo" or "gif"
    frames: int | None = None

    @property
    def size_bytes(self) -> int:
        return len(self.data)

    def to_metadata(self) -> CaptureMetadata:
        """Serialize into client-facing metadata model."""
        return CaptureMetadata(
            id=self.id,
            type=self.capture_type,  # type: ignore[arg-type]
            content_type=self.content_type,
            filename=self.filename,
            width=self.width,
            height=self.height,
            size_bytes=self.size_bytes,
            created_at=self.created_at,
            url=f"/api/captures/{self.id}",
            frames=self.frames,
        )


class CaptureStore:
    """Thread-safe bounded capture store with memory byte caps, TTL expiration, and disk persistence."""

    def __init__(
        self,
        max_captures: int = settings.max_captures,
        max_bytes: int = settings.max_capture_store_mb * 1024 * 1024,
        ttl_seconds: float = 3600.0,
        captures_dir: Path | None = None,
        persist_to_disk: bool = False,
    ) -> None:
        self.max_captures = max_captures
        self.max_bytes = max_bytes
        self.ttl_seconds = ttl_seconds
        self.captures_dir = captures_dir
        self.persist_to_disk = persist_to_disk and (captures_dir is not None)
        self._captures: OrderedDict[str, CaptureItem] = OrderedDict()
        self._total_bytes: int = 0
        self._lock = threading.Lock()

        # Ensure disk directory exists if persistence enabled
        if self.persist_to_disk and self.captures_dir:
            try:
                self.captures_dir.mkdir(parents=True, exist_ok=True)
            except Exception as exc:
                logger.warning("Could not create captures_dir %s: %s", self.captures_dir, exc)

    def _purge_expired_locked(self, now: float) -> None:
        """Remove captures that have exceeded time-to-live from RAM."""
        expired_keys = [
            k for k, v in self._captures.items()
            if (now - v.created_at) > self.ttl_seconds
        ]
        for k in expired_keys:
            item = self._captures.pop(k, None)
            if item:
                self._total_bytes -= item.size_bytes

    def add(self, item: CaptureItem) -> None:
        """Add a capture to store, evicting oldest items if item count or memory limit is exceeded."""
        now = time.time()

        # 1. Persist to disk for permanent event storage if enabled
        if self.persist_to_disk and self.captures_dir:
            try:
                disk_file = self.captures_dir / item.filename
                disk_file.write_bytes(item.data)
            except Exception as exc:
                logger.warning("Failed saving capture %s to disk: %s", item.id, exc)

        with self._lock:
            self._purge_expired_locked(now)

            # Evict oldest items if at count limit or exceeding RAM byte cap
            while self._captures and (
                len(self._captures) >= self.max_captures
                or (self._total_bytes + item.size_bytes > self.max_bytes)
            ):
                oldest_id, oldest_item = self._captures.popitem(last=False)
                self._total_bytes -= oldest_item.size_bytes
                logger.info(
                    "Evicted oldest capture %s from RAM (RAM freed: %d KB, now %d MB / %d MB)",
                    oldest_id,
                    round(oldest_item.size_bytes / 1024),
                    round(self._total_bytes / (1024 * 1024), 1),
                    round(self.max_bytes / (1024 * 1024)),
                )

            self._captures[item.id] = item
            self._total_bytes += item.size_bytes
            logger.info(
                "Stored capture %s (%s, %dx%d, %d KB). RAM cache: %d items, %.1f MB",
                item.id,
                item.capture_type,
                item.width,
                item.height,
                round(item.size_bytes / 1024),
                len(self._captures),
                self._total_bytes / (1024 * 1024),
            )

    def get(self, capture_id: str, include_disk: bool = False) -> CaptureItem | None:
        """Retrieve a capture by ID from RAM cache or disk."""
        now = time.time()
        with self._lock:
            item = self._captures.get(capture_id)
            if item is not None:
                if (now - item.created_at) <= self.ttl_seconds:
                    return item
                # Expired from active cache
                self._captures.pop(capture_id, None)
                self._total_bytes -= item.size_bytes

        # Fallback to loading from disk if requested and evicted from RAM
        if include_disk and self.persist_to_disk and self.captures_dir:
            try:
                for ext in (".jpg", ".gif"):
                    disk_file = self.captures_dir / f"{capture_id}{ext}"
                    if disk_file.exists():
                        data = disk_file.read_bytes()
                        cap_type = "gif" if ext == ".gif" else "photo"
                        content_type = "image/gif" if ext == ".gif" else "image/jpeg"
                        w, h = 640, 480
                        try:
                            with Image.open(io.BytesIO(data)) as img:
                                w, h = img.size
                        except Exception:
                            pass
                        restored = CaptureItem(
                            id=capture_id,
                            content_type=content_type,
                            data=data,
                            filename=disk_file.name,
                            width=w,
                            height=h,
                            created_at=disk_file.stat().st_mtime,
                            capture_type=cap_type,
                        )
                        return restored
            except Exception as exc:
                logger.debug("Failed reading capture %s from disk: %s", capture_id, exc)

        return None

    def list_all(self) -> list[CaptureMetadata]:
        """Return metadata for all active captures sorted newest first."""
        now = time.time()
        with self._lock:
            self._purge_expired_locked(now)
            # Return list in reverse insertion order (newest first)
            return [item.to_metadata() for item in reversed(list(self._captures.values()))]

    def delete(self, capture_id: str) -> bool:
        """Delete a capture from storage by ID and disk."""
        deleted = False
        with self._lock:
            item = self._captures.pop(capture_id, None)
            if item is not None:
                self._total_bytes -= item.size_bytes
                deleted = True

        if self.captures_dir:
            for ext in (".jpg", ".gif"):
                disk_file = self.captures_dir / f"{capture_id}{ext}"
                if disk_file.exists():
                    try:
                        disk_file.unlink()
                        deleted = True
                    except Exception:
                        pass

        if deleted:
            logger.info("Deleted capture %s", capture_id)
        return deleted

    def clear(self) -> None:
        """Remove all captures from the store."""
        with self._lock:
            self._captures.clear()
            self._total_bytes = 0

    @property
    def total_bytes(self) -> int:
        return self._total_bytes

    @property
    def memory_usage_mb(self) -> float:
        return round(self._total_bytes / (1024 * 1024), 2)


# Global capture store singleton
_capture_store_instance: CaptureStore | None = None
_store_lock = threading.Lock()


def get_capture_store() -> CaptureStore:
    """Retrieve or instantiate the global CaptureStore singleton."""
    global _capture_store_instance
    with _store_lock:
        if _capture_store_instance is None:
            _capture_store_instance = CaptureStore(
                max_captures=settings.max_captures,
                max_bytes=settings.max_capture_store_mb * 1024 * 1024,
                ttl_seconds=3600.0,
            )
        return _capture_store_instance


def capture_photo(camera: BaseCamera | None = None) -> CaptureItem:
    """Capture a high-resolution still image, encode to JPEG, and store."""
    cam = camera or get_camera()
    jpeg_bytes = cam.capture_high_res()

    # Determine dimensions from captured JPEG
    try:
        with Image.open(io.BytesIO(jpeg_bytes)) as img:
            width, height = img.size
    except Exception:
        width, height = settings.capture_width, settings.capture_height

    now = time.time()
    date_str = datetime.now().strftime("%Y%m%d_%H%M%S")
    rand_suffix = uuid.uuid4().hex[:6]
    cap_id = f"photo_{date_str}_{rand_suffix}"
    filename = f"{cap_id}.jpg"

    item = CaptureItem(
        id=cap_id,
        content_type="image/jpeg",
        data=jpeg_bytes,
        filename=filename,
        width=width,
        height=height,
        created_at=now,
        capture_type="photo",
    )
    get_capture_store().add(item)
    return item


async def capture_gif(
    camera: BaseCamera | None = None,
    frames: int = 10,
    interval_ms: int = 150,
) -> CaptureItem:
    """Capture a burst of frames from the camera feed and assemble into an animated GIF."""
    cam = camera or get_camera()
    frames_count = max(3, min(30, int(frames)))
    interval = max(50, min(500, int(interval_ms)))

    collected_images: list[Image.Image] = []
    width, height = 640, 480

    for idx in range(frames_count):
        raw_bytes = cam.get_latest_frame()
        if raw_bytes is None:
            raw_bytes = cam.capture_high_res()

        try:
            pil_img = Image.open(io.BytesIO(raw_bytes)).convert("RGB")
            width, height = pil_img.size
            collected_images.append(pil_img)
        except Exception as exc:
            logger.warning("Failed decoding frame %d for GIF: %s", idx, exc)

        if idx < frames_count - 1:
            await asyncio.sleep(interval / 1000.0)

    if not collected_images:
        collected_images.append(Image.new("RGB", (width, height), color=(40, 40, 40)))

    gif_bytes: bytes
    # Attempt assembly with imageio (standard for scientific/video workflows), fallback to Pillow
    try:
        import imageio.v2 as imageio
        import numpy as np

        arrays = [np.array(img) for img in collected_images]
        buf = io.BytesIO()
        imageio.mimsave(buf, arrays, format="GIF", duration=interval / 1000.0, loop=0)
        gif_bytes = buf.getvalue()
    except Exception as exc:
        logger.warning("imageio assembly error (%s), using Pillow fallback", exc)
        buf = io.BytesIO()
        collected_images[0].save(
            buf,
            format="GIF",
            save_all=True,
            append_images=collected_images[1:],
            duration=interval,
            loop=0,
            optimize=True,
        )
        gif_bytes = buf.getvalue()

    now = time.time()
    date_str = datetime.now().strftime("%Y%m%d_%H%M%S")
    rand_suffix = uuid.uuid4().hex[:6]
    cap_id = f"gif_{date_str}_{rand_suffix}"
    filename = f"{cap_id}.gif"

    item = CaptureItem(
        id=cap_id,
        content_type="image/gif",
        data=gif_bytes,
        filename=filename,
        width=width,
        height=height,
        created_at=now,
        capture_type="gif",
        frames=len(collected_images),
    )
    get_capture_store().add(item)
    return item
