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
        self.connected_at = time.time()

    def offer_frame(self, frame: bytes) -> None:
        """Enqueue the latest frame, dropping the older pending frame if necessary."""
        if not self.is_active:
            return
        if self.frame_queue.full():
            try:
                # Discard stale unconsumed frame
                self.frame_queue.get_nowait()
            except asyncio.QueueEmpty:
                pass
        try:
            self.frame_queue.put_nowait(frame)
        except asyncio.QueueFull:
            pass


class Streamer:
    """Manages active WebSocket connections and distributes live camera frames."""

    def __init__(self, camera: BaseCamera | None = None) -> None:
        self.camera = camera or get_camera()
        self._clients: Set[ClientSession] = set()
        self._broadcast_task: asyncio.Task | None = None
        self._running = False
        self._frames_sent_total = 0
        self._start_time = time.time()
        self._last_frame_bytes: bytes | None = None

    @property
    def client_count(self) -> int:
        return len(self._clients)

    @property
    def frames_sent_total(self) -> int:
        return self._frames_sent_total

    @property
    def uptime_seconds(self) -> float:
        return round(time.time() - self._start_time, 1)

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
        self._clients.discard(session)
        logger.info("Client disconnected. Remaining clients: %d", len(self._clients))

    async def _broadcast_loop(self) -> None:
        """Continuous loop polling camera frames and distributing them to clients."""
        while self._running:
            loop_start = time.time()

            # Only fetch frame if there are connected clients
            if self._clients:
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
                                action = payload.get("action")
                                if action == "swap_rb":
                                    val = payload.get("value")
                                    if val is None:
                                        settings.swap_rb = not settings.swap_rb
                                    else:
                                        settings.swap_rb = bool(val)
                                    await websocket.send_json({
                                        "type": "config",
                                        "swap_rb": settings.swap_rb,
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
