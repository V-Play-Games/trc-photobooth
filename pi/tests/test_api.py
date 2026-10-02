"""Integration tests for FastAPI endpoints and WebSocket streaming."""

import pytest
from fastapi.testclient import TestClient
from src.main import app


@pytest.fixture(scope="module")
def client():
    """Create test client with lifespan context to start camera/streamer."""
    with TestClient(app) as c:
        yield c


def test_health_endpoint(client: TestClient) -> None:
    """GET /api/health should return ok status."""
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert "timestamp" in data


def test_status_endpoint(client: TestClient) -> None:
    """GET /api/status should report camera readiness and metrics."""
    response = client.get("/api/status")
    assert response.status_code == 200
    data = response.json()
    assert data["camera_ready"] is True
    assert "actual_fps" in data
    assert "connected_clients" in data
    assert "resolution" in data


def test_config_endpoints(client: TestClient) -> None:
    """GET /api/config and POST /api/config should read and update config."""
    get_res = client.get("/api/config")
    assert get_res.status_code == 200
    initial_config = get_res.json()
    assert "fps" in initial_config
    assert "quality" in initial_config

    # Update config
    post_res = client.post("/api/config", json={"fps": 20, "quality": 80})
    assert post_res.status_code == 200
    updated_data = post_res.json()
    assert updated_data["fps"] == 20
    assert updated_data["quality"] == 80


def test_serve_static_html(client: TestClient) -> None:
    """GET / and GET /test.html should return the HTML test monitor."""
    res_root = client.get("/")
    assert res_root.status_code == 200
    assert "TRC Photo Booth" in res_root.text

    res_test = client.get("/test.html")
    assert res_test.status_code == 200
    assert "cameraCanvas" in res_test.text


def test_websocket_feed_streaming(client: TestClient) -> None:
    """WebSocket /ws/feed should stream binary JPEG frames and respond to ping."""
    with client.websocket_connect("/ws/feed") as websocket:
        # Receive binary frame
        frame_bytes = websocket.receive_bytes()
        assert isinstance(frame_bytes, bytes)
        assert len(frame_bytes) > 500
        # Check JPEG header
        assert frame_bytes[:2] == b"\xff\xd8"

        # Send text ping message
        websocket.send_text("ping")
        response = websocket.receive_json()
        assert response["type"] == "pong"
