"""Routes package initialization."""

from src.routes.api import router as api_router
from src.routes.ws import router as ws_router

__all__ = ["api_router", "ws_router"]
