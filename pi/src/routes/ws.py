"""WebSocket streaming endpoint for high-frequency low-latency JPEG frames."""

from fastapi import APIRouter, WebSocket
from src.streamer import get_streamer

router = APIRouter(tags=["Stream"])


@router.websocket("/ws/feed")
async def websocket_camera_feed(websocket: WebSocket) -> None:
    """Stream continuous binary JPEG frames to connected clients via WebSocket."""
    streamer = get_streamer()
    await streamer.handle_client(websocket)
