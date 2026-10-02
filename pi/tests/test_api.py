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

    # Update config with 60 FPS and 720p resolution
    post_res = client.post(
        "/api/config",
        json={"fps": 60, "quality": 80, "resolution": "720p", "aspect_ratio": "16:9", "swap_rb": True},
    )
    assert post_res.status_code == 200
    updated_data = post_res.json()
    assert updated_data["fps"] == 60
    assert updated_data["quality"] == 80
    assert updated_data["width"] == 1280
    assert updated_data["height"] == 720
    assert updated_data["swap_rb"] is True

    # Test 4:3 resolution preset (e.g. 480p -> 640x480)
    post_res_43 = client.post(
        "/api/config",
        json={"resolution": "480p", "aspect_ratio": "4:3"},
    )
    assert post_res_43.status_code == 200
    assert post_res_43.json()["width"] == 640
    assert post_res_43.json()["height"] == 480

    # Reset swap_rb to False
    post_res2 = client.post("/api/config", json={"swap_rb": False, "fps": 15})
    assert post_res2.status_code == 200
    assert post_res2.json()["swap_rb"] is False
    assert post_res2.json()["fps"] == 15


def test_serve_static_html(client: TestClient) -> None:
    """GET / and GET /test.html should return the React app and HTML test monitor."""
    res_root = client.get("/")
    assert res_root.status_code == 200
    assert "TRC Photo Booth" in res_root.text

    res_test = client.get("/test.html")
    assert res_test.status_code == 200
    assert "cameraCanvas" in res_test.text


def test_serve_vite_assets(client: TestClient) -> None:
    """GET /assets/... should serve Vite bundled scripts and stylesheets."""
    import re
    res_root = client.get("/")
    assert res_root.status_code == 200
    # Find asset paths in index.html
    matches = re.findall(r'(?:src|href)="(?:\./)?(assets/[^"]+)"', res_root.text)
    assert len(matches) > 0, "No assets found in index.html"
    for asset_path in matches:
        res_asset = client.get(f"/{asset_path}")
        assert res_asset.status_code == 200
        assert len(res_asset.content) > 100


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

        # Request system stats on-demand via WebSocket
        websocket.send_text('{"action": "get_system_stats"}')
        stats_resp = websocket.receive_json()
        assert stats_resp["type"] == "system_stats"
        assert "cpu_percent" in stats_resp["data"]
        assert "memory" in stats_resp["data"]

        # Toggle flip_horizontal via WebSocket
        websocket.send_text('{"action": "flip_horizontal", "value": false}')
        flip_resp = websocket.receive_json()
        assert flip_resp["type"] == "config"
        assert flip_resp["flip_horizontal"] is False


def test_system_stats_endpoint(client: TestClient) -> None:
    """GET /api/system/stats should report real-time on-demand hardware telemetry."""
    res = client.get("/api/system/stats")
    assert res.status_code == 200
    data = res.json()

    assert "cpu_percent" in data
    assert isinstance(data["cpu_percent"], (int, float))
    assert 0.0 <= data["cpu_percent"] <= 100.0

    assert "cpu_temp_c" in data
    if data["cpu_temp_c"] is not None:
        assert isinstance(data["cpu_temp_c"], (int, float))
        assert -20.0 < data["cpu_temp_c"] < 130.0

    assert "memory" in data
    mem = data["memory"]
    assert mem["total_mb"] > 0
    assert mem["used_mb"] >= 0
    assert 0.0 <= mem["percent"] <= 100.0

    assert "load_avg" in data
    assert len(data["load_avg"]) == 3
    assert "cpu_count" in data
    assert data["cpu_count"] >= 1
    assert "timestamp" in data


def test_flip_horizontal_controls(client: TestClient) -> None:
    """GET /api/config and POST /api/config should support flip_horizontal (mirror)."""
    # Verify current flip setting in config
    cfg_res = client.get("/api/config")
    assert cfg_res.status_code == 200
    assert "flip_horizontal" in cfg_res.json()

    # Toggle flip_horizontal to False
    post_res = client.post("/api/config", json={"flip_horizontal": False})
    assert post_res.status_code == 200
    assert post_res.json()["flip_horizontal"] is False

    # Check status endpoint
    status_res = client.get("/api/status")
    assert status_res.status_code == 200
    assert status_res.json()["flip_horizontal"] is False

    # Toggle flip_horizontal back to True
    post_res2 = client.post("/api/config", json={"flip_horizontal": True})
    assert post_res2.status_code == 200
    assert post_res2.json()["flip_horizontal"] is True

    # Restore default setting (False)
    restore_res = client.post("/api/config", json={"flip_horizontal": False})
    assert restore_res.status_code == 200
    assert restore_res.json()["flip_horizontal"] is False
