"""WebSocket streaming hub with per-client backpressure and frame-dropping."""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Set
from fastapi import WebSocket, WebSocketDisconnect

from src.camera import BaseCamera, get_camera
from src.config import settings

logger = logging.getLogger("photobooth.streamer")


class ClientSession:
    """Represents a connected client with a bounded frame queue to drop stale frames."""

    def __init__(self, websocket: WebSocket) -> None:
        self.websocket = websocket
        # Queue with capacity 1: if client is slow, the older frame is replaced with the fresh one
        self.frame_queue: asyncio.Queue[bytes] = asyncio.Queue(maxsize=1)
        self.is_active = True
        self.frames_sent = 0
        self.frames_dropped = 0
        self.connected_at = time.time()

    def offer_frame(self, frame: bytes) -> None:
        """Enqueue the latest frame, dropping the older pending frame if necessary."""
        if not self.is_active:
            return
        if self.frame_queue.full():
            try:
                # Discard stale unconsumed frame
                self.frame_queue.get_nowait()
                self.frames_dropped += 1
            except asyncio.QueueEmpty:
                pass
        try:
            self.frame_queue.put_nowait(frame)
        except asyncio.QueueFull:
            self.frames_dropped += 1


class Streamer:
    """Manages active WebSocket connections and distributes live camera frames."""

    def __init__(self, camera: BaseCamera | None = None) -> None:
        self.camera = camera or get_camera()
        self._clients: Set[ClientSession] = set()
        self._broadcast_task: asyncio.Task | None = None
        self._running = False
        self._frames_sent_total = 0
        self._frames_dropped_historical = 0
        self._start_time = time.time()
        self.last_loop_time = time.time()
        self._last_frame_bytes: bytes | None = None
        self._capturing_lock = asyncio.Lock()
        self._base_quality = settings.preview_quality
        self._is_degraded = False
        if settings.camera_default_off:
            self.camera.pause()

    @property
    def is_paused(self) -> bool:
        return getattr(self.camera, "is_paused", False)

    async def pause_stream(self) -> None:
        """Pause camera capture and streaming to eliminate CPU load and cooling."""
        if hasattr(self.camera, "pause"):
            self.camera.pause()
        await self.broadcast_json({"type": "stream_status", "is_paused": True})
        logger.info("Stream paused by user")

    async def resume_stream(self) -> None:
        """Resume camera capture and streaming."""
        if hasattr(self.camera, "resume"):
            self.camera.resume()
        await self.broadcast_json({"type": "stream_status", "is_paused": False})
        logger.info("Stream resumed by user")

    async def toggle_stream(self) -> bool:
        """Toggle stream pause state and broadcast."""
        if self.is_paused:
            await self.resume_stream()
        else:
            await self.pause_stream()
        return self.is_paused

    async def broadcast_json(self, message: dict) -> None:
        """Broadcast a JSON message to all connected clients."""
        for client in list(self._clients):
            if client.is_active:
                try:
                    await client.websocket.send_json(message)
                except Exception:
                    pass

    async def execute_countdown_and_capture(
        self,
        action: str = "photo",
        countdown_seconds: int = 0,
        frames: int = 10,
        interval_ms: int = 150,
    ) -> None:
        """Run countdown with broadcast ticks and execute photo or GIF capture."""
        if self._capturing_lock.locked():
            logger.warning("Capture request ignored: capture or countdown already active")
            return

        if self.is_paused:
            await self.resume_stream()

        from src.gpio_status import LedState, get_gpio_indicator
        gpio = get_gpio_indicator()

        async with self._capturing_lock:
            gpio.set_state(LedState.CAPTURING)
            try:
                # 1. Countdown ticks
                if countdown_seconds > 0:
                    for sec in range(countdown_seconds, 0, -1):
                        await self.broadcast_json({
                            "type": "countdown_tick",
                            "seconds_left": sec,
                            "action": action,
                        })
                        await asyncio.sleep(1.0)
                    await self.broadcast_json({
                        "type": "countdown_tick",
                        "seconds_left": 0,
                        "action": action,
                    })

                # 2. Perform capture
                from src.capture import capture_photo, capture_gif

                if action == "photo":
                    item = capture_photo(self.camera)
                    await self.broadcast_json({
                        "type": "capture_result",
                        "data": item.to_metadata().model_dump(),
                    })
                elif action == "gif":
                    await self.broadcast_json({
                        "type": "gif_recording",
                        "frames": frames,
                        "interval_ms": interval_ms,
                    })
                    item = await capture_gif(self.camera, frames=frames, interval_ms=interval_ms)
                    await self.broadcast_json({
                        "type": "gif_result",
                        "data": item.to_metadata().model_dump(),
                    })
            finally:
                gpio.set_state(LedState.HEARTBEAT if self._clients else LedState.READY)

    @property
    def client_count(self) -> int:
        return len(self._clients)

    @property
    def frames_sent_total(self) -> int:
        return self._frames_sent_total

    @property
    def frames_dropped_total(self) -> int:
        current_dropped = sum(c.frames_dropped for c in self._clients)
        return self._frames_dropped_historical + current_dropped

    @property
    def uptime_seconds(self) -> float:
        return round(time.time() - self._start_time, 1)

    @property
    def is_degraded(self) -> bool:
        """True if server is currently in idle low-power degradation mode."""
        return self._is_degraded

    async def start(self) -> None:
        """Start the camera capture and broadcast task."""
        if self._running:
            return
        self._running = True
        self.camera.start()
        self._broadcast_task = asyncio.create_task(self._broadcast_loop())
        logger.info("Streamer started broadcast task")

    async def stop(self) -> None:
        """Stop broadcast loop and disconnect remaining clients."""
        self._running = False
        if self._broadcast_task:
            self._broadcast_task.cancel()
            try:
                await self._broadcast_task
            except asyncio.CancelledError:
                pass
        self.camera.stop()
        # Close all active client sessions
        for client in list(self._clients):
            client.is_active = False
            self._frames_dropped_historical += client.frames_dropped
            try:
                await client.websocket.close(code=1001, reason="Server shutting down")
            except Exception:
                pass
        self._clients.clear()
        logger.info("Streamer stopped")

    def register(self, websocket: WebSocket) -> ClientSession:
        """Register a new connected WebSocket client."""
        session = ClientSession(websocket)
        self._clients.add(session)
        logger.info("Client connected. Total clients: %d", len(self._clients))
        return session

    def unregister(self, session: ClientSession) -> None:
        """Unregister a client session upon disconnect."""
        session.is_active = False
        self._frames_dropped_historical += session.frames_dropped
        self._clients.discard(session)
        logger.info("Client disconnected. Remaining clients: %d", len(self._clients))

    async def _broadcast_loop(self) -> None:
        """Continuous loop polling camera frames and distributing them to clients."""
        from src.gpio_status import LedState, get_gpio_indicator
        gpio = get_gpio_indicator()

        while self._running:
            loop_start = time.time()
            self.last_loop_time = loop_start

            if self.is_paused:
                await asyncio.sleep(0.1)
                continue

            # Hardware LED indication
            if hasattr(self.camera, "is_connected") and not self.camera.is_connected:
                gpio.set_state(LedState.ERROR)
            elif self._capturing_lock.locked():
                gpio.set_state(LedState.CAPTURING)
            elif self._clients:
                gpio.set_state(LedState.HEARTBEAT)
            else:
                gpio.set_state(LedState.READY)

            # 1. Graceful degradation when no clients are connected
            if not self._clients:
                self._is_degraded = True
                # Throttle loop to idle_fps (e.g. 2 fps / 500ms) to save CPU & thermal load
                if self.camera.quality != self._base_quality:
                    self.camera.quality = self._base_quality
                idle_interval = 1.0 / max(1, settings.idle_fps)
                await asyncio.sleep(idle_interval)
                continue

            self._is_degraded = False

            # 2. Adaptive quality when multiple clients are connected
            num_clients = len(self._clients)
            if settings.adaptive_quality:
                if num_clients > 2:
                    adapted_quality = max(35, self._base_quality - (num_clients - 2) * 12)
                else:
                    adapted_quality = self._base_quality
                if self.camera.quality != adapted_quality:
                    self.camera.quality = adapted_quality
                    logger.debug("Adaptive quality adjusted to %d for %d clients", adapted_quality, num_clients)

            # 3. Fetch latest camera frame and fan-out
            frame = self.camera.get_latest_frame()
            if frame and frame is not self._last_frame_bytes:
                self._last_frame_bytes = frame
                for client in list(self._clients):
                    client.offer_frame(frame)

            interval = 1.0 / max(1, settings.preview_fps)
            elapsed = time.time() - loop_start
            sleep_time = interval - elapsed
            if sleep_time > 0:
                await asyncio.sleep(sleep_time)
            else:
                await asyncio.sleep(0.001)

    async def handle_client(self, websocket: WebSocket) -> None:
        """Lifecycle handler for an individual client's WebSocket connection."""
        await websocket.accept()
        session = self.register(websocket)

        # Task to send frames from session's queue to the client
        async def sender_worker() -> None:
            while session.is_active:
                try:
                    frame = await session.frame_queue.get()
                    await websocket.send_bytes(frame)
                    session.frames_sent += 1
                    self._frames_sent_total += 1
                except (WebSocketDisconnect, asyncio.CancelledError):
                    break
                except Exception as exc:
                    logger.debug("Error sending frame to client: %s", exc)
                    break

        sender_task = asyncio.create_task(sender_worker())

        try:
            # Main listener for client messages (pings, control commands)
            while session.is_active:
                message = await websocket.receive()
                if message["type"] == "websocket.disconnect":
                    break
                elif message["type"] == "websocket.receive":
                    text_data = message.get("text")
                    if text_data:
                        # Handle text/json pings and control messages
                        logger.debug("Received text from client: %s", text_data)
                        if "ping" in text_data.lower():
                            await websocket.send_json({"type": "pong", "time": time.time()})
                        else:
                            try:
                                import json
                                payload = json.loads(text_data)
                                action = payload.get("action") or payload.get("type")
                                if action in ("trigger_capture", "capture_photo"):
                                    countdown = int(payload.get("countdown_seconds", payload.get("countdown", payload.get("seconds", 0))))
                                    asyncio.create_task(
                                        self.execute_countdown_and_capture(action="photo", countdown_seconds=countdown)
                                    )
                                elif action in ("trigger_gif", "capture_gif"):
                                    countdown = int(payload.get("countdown_seconds", payload.get("countdown", payload.get("seconds", 0))))
                                    frames_val = int(payload.get("frames", 10))
                                    interval_val = int(payload.get("interval_ms", 150))
                                    asyncio.create_task(
                                        self.execute_countdown_and_capture(
                                            action="gif",
                                            countdown_seconds=countdown,
                                            frames=frames_val,
                                            interval_ms=interval_val,
                                        )
                                    )
                                elif action == "countdown":
                                    countdown = int(payload.get("seconds", payload.get("countdown", 3)))
                                    target_act = payload.get("action_type", payload.get("target", "photo"))
                                    frames_val = int(payload.get("frames", 10))
                                    interval_val = int(payload.get("interval_ms", 150))
                                    asyncio.create_task(
                                        self.execute_countdown_and_capture(
                                            action=target_act,
                                            countdown_seconds=countdown,
                                            frames=frames_val,
                                            interval_ms=interval_val,
                                        )
                                    )
                                elif action == "swap_rb":
                                    val = payload.get("value")
                                    if val is None:
                                        settings.swap_rb = not settings.swap_rb
                                    else:
                                        settings.swap_rb = bool(val)
                                    await websocket.send_json({
                                        "type": "config",
                                        "swap_rb": settings.swap_rb,
                                    })
                                elif action in ("flip_horizontal", "toggle_flip"):
                                    val = payload.get("value")
                                    if val is None:
                                        settings.flip_horizontal = not settings.flip_horizontal
                                    else:
                                        settings.flip_horizontal = bool(val)
                                    if hasattr(self.camera, "flip_horizontal"):
                                        self.camera.flip_horizontal = settings.flip_horizontal
                                    await websocket.send_json({
                                        "type": "config",
                                        "flip_horizontal": settings.flip_horizontal,
                                    })
                                elif action == "get_system_stats":
                                    from src.system_info import get_system_stats
                                    stats = get_system_stats()
                                    await websocket.send_json({
                                        "type": "system_stats",
                                        "data": stats.model_dump(),
                                    })
                                elif action == "set_fps":
                                    fps_val = int(payload.get("value", 15))
                                    settings.preview_fps = fps_val
                                    if hasattr(self.camera, "target_fps"):
                                        self.camera.target_fps = fps_val
                                    await websocket.send_json({
                                        "type": "config",
                                        "fps": settings.preview_fps,
                                    })
                                elif action == "set_quality":
                                    quality_val = int(payload.get("value", 70))
                                    settings.preview_quality = quality_val
                                    if hasattr(self.camera, "quality"):
                                        self.camera.quality = quality_val
                                    await websocket.send_json({
                                        "type": "config",
                                        "quality": settings.preview_quality,
                                    })
                                elif action == "set_resolution":
                                    res_val = payload.get("value")
                                    ratio = payload.get("aspect_ratio", "4:3")
                                    w_val = payload.get("width")
                                    h_val = payload.get("height")
                                    from src.models import parse_resolution
                                    parsed = parse_resolution(res_val, ratio, w_val, h_val)
                                    if parsed:
                                        settings.preview_width, settings.preview_height = parsed
                                        if hasattr(self.camera, "set_resolution"):
                                            self.camera.set_resolution(parsed[0], parsed[1])
                                        else:
                                            self.camera.width, self.camera.height = parsed
                                        await websocket.send_json({
                                            "type": "config",
                                            "width": settings.preview_width,
                                            "height": settings.preview_height,
                                            "resolution": f"{settings.preview_width}x{settings.preview_height}",
                                        })
                                elif action in ("set_device", "set_camera_device"):
                                    dev = payload.get("device", payload.get("value"))
                                    from src.camera import get_camera, list_video_devices, switch_camera_device
                                    success = switch_camera_device(dev)
                                    self.camera = get_camera()
                                    devices = list_video_devices()
                                    await websocket.send_json({
                                        "type": "devices",
                                        "webcam_device": settings.webcam_device,
                                        "device_path": f"/dev/video{settings.webcam_device}",
                                        "available_devices": devices,
                                        "success": success,
                                    })
                                    await self.broadcast_json({
                                        "type": "config",
                                        "webcam_device": settings.webcam_device,
                                        "device_path": f"/dev/video{settings.webcam_device}",
                                        "available_devices": devices,
                                    })
                                elif action in ("get_devices", "list_devices"):
                                    from src.camera import list_video_devices
                                    devices = list_video_devices()
                                    await websocket.send_json({
                                        "type": "devices",
                                        "webcam_device": settings.webcam_device,
                                        "device_path": f"/dev/video{settings.webcam_device}",
                                        "available_devices": devices,
                                    })
                                elif action in ("pause_stream", "pause", "turn_off_camera", "camera_off"):
                                    await self.pause_stream()
                                elif action in ("resume_stream", "resume", "turn_on_camera", "camera_on", "request_camera", "start_stream"):
                                    await self.resume_stream()
                                elif action in ("toggle_pause", "toggle_stream", "toggle_camera"):
                                    await self.toggle_stream()
                                elif action in ("get_stream_status", "stream_status"):
                                    await websocket.send_json({
                                        "type": "stream_status",
                                        "is_paused": self.is_paused,
                                    })
                            except Exception:
                                pass
        except WebSocketDisconnect:
            pass
        except Exception as exc:
            logger.debug("WebSocket client exception: %s", exc)
        finally:
            session.is_active = False
            sender_task.cancel()
            try:
                await sender_task
            except asyncio.CancelledError:
                pass
            self.unregister(session)


# Streamer singleton
_streamer_instance: Streamer | None = None


def get_streamer() -> Streamer:
    """Retrieve or create the Streamer singleton."""
    global _streamer_instance
    if _streamer_instance is None:
        _streamer_instance = Streamer()
    return _streamer_instance
