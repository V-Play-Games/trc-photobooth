"""Watchdog monitoring service for camera capture health and systemd integration."""

from __future__ import annotations

import logging
import os
import socket
import threading
import time
from typing import Callable

from src.config import settings

logger = logging.getLogger("photobooth.watchdog")


class SystemdNotifier:
    """Sends status and watchdog heartbeats to systemd via unix domain socket."""

    def __init__(self) -> None:
        self.notify_socket = os.environ.get("NOTIFY_SOCKET")
        self._enabled = bool(self.notify_socket)
        if self._enabled:
            logger.info("systemd notify socket detected at %s", self.notify_socket)

    def notify(self, state: str) -> bool:
        """Send notification state string (e.g. 'READY=1', 'WATCHDOG=1') to systemd."""
        if not self._enabled or not self.notify_socket:
            return False

        sock_addr = self.notify_socket
        if sock_addr.startswith("@"):
            # Abstract unix domain socket namespace
            sock_addr = "\0" + sock_addr[1:]

        try:
            sock = socket.socket(socket.AF_UNIX, socket.SOCK_DGRAM)
            try:
                sock.sendto(state.encode(), sock_addr)
                return True
            finally:
                sock.close()
        except Exception as exc:
            logger.debug("Failed sending notification to systemd: %s", exc)
            return False

    def notify_ready(self) -> bool:
        return self.notify("READY=1")

    def notify_watchdog(self) -> bool:
        return self.notify("WATCHDOG=1")

    def notify_stopping(self) -> bool:
        return self.notify("STOPPING=1")


class Watchdog:
    """Monitors camera and streamer loops, restarting components if hung."""

    def __init__(
        self,
        timeout_seconds: float = settings.watchdog_timeout,
        get_camera_fn: Callable | None = None,
        get_streamer_fn: Callable | None = None,
    ) -> None:
        self.timeout_seconds = timeout_seconds
        self.get_camera_fn = get_camera_fn
        self.get_streamer_fn = get_streamer_fn
        self.notifier = SystemdNotifier()
        self._running = False
        self._thread: threading.Thread | None = None
        self._last_kick = time.time()

    def start(self) -> None:
        """Start the watchdog background monitor."""
        if self._running or not settings.enable_watchdog:
            return
        self._running = True
        self._last_kick = time.time()
        self._thread = threading.Thread(target=self._monitor_loop, daemon=True)
        self._thread.start()
        self.notifier.notify_ready()
        logger.info(
            "Watchdog monitor started (timeout: %.1fs, systemd_notify=%s)",
            self.timeout_seconds,
            self.notifier._enabled,
        )

    def stop(self) -> None:
        """Stop the watchdog monitor."""
        self._running = False
        self.notifier.notify_stopping()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=1.0)
        logger.info("Watchdog monitor stopped")

    def kick(self) -> None:
        """External heartbeat kick."""
        self._last_kick = time.time()
        self.notifier.notify_watchdog()

    def _monitor_loop(self) -> None:
        """Periodic loop inspecting camera frame activity and kicking systemd."""
        while self._running:
            time.sleep(2.0)
            now = time.time()

            is_healthy = True

            # Check camera responsiveness
            if self.get_camera_fn:
                try:
                    cam = self.get_camera_fn()
                    if cam and cam.is_running:
                        time_since_frame = now - cam.last_frame_time
                        if time_since_frame > self.timeout_seconds:
                            logger.error(
                                "Watchdog alert: Camera loop stalled (no frame for %.1fs). Restarting camera...",
                                time_since_frame,
                            )
                            is_healthy = False
                            cam.stop()
                            time.sleep(0.5)
                            cam.start()
                except Exception as exc:
                    logger.warning("Watchdog encountered error checking camera: %s", exc)

            # Check streamer loop if registered
            if self.get_streamer_fn:
                try:
                    streamer = self.get_streamer_fn()
                    if streamer and hasattr(streamer, "last_loop_time"):
                        time_since_loop = now - streamer.last_loop_time
                        if time_since_loop > self.timeout_seconds and streamer.client_count > 0:
                            logger.error(
                                "Watchdog alert: Streamer loop stalled (%.1fs).",
                                time_since_loop,
                            )
                            is_healthy = False
                except Exception as exc:
                    logger.warning("Watchdog encountered error checking streamer: %s", exc)

            # If healthy, notify systemd watchdog
            if is_healthy:
                self.notifier.notify_watchdog()
                self._last_kick = now


_watchdog_instance: Watchdog | None = None


def get_watchdog() -> Watchdog:
    """Retrieve or create the global Watchdog singleton."""
    global _watchdog_instance
    if _watchdog_instance is None:
        from src.camera import get_camera
        from src.streamer import get_streamer

        _watchdog_instance = Watchdog(
            timeout_seconds=settings.watchdog_timeout,
            get_camera_fn=get_camera,
            get_streamer_fn=get_streamer,
        )
    return _watchdog_instance
