"""Integration tests for Phase 3: React Web Client serving and contracts."""

import re
import pytest
from fastapi.testclient import TestClient
from src.main import app


@pytest.fixture(scope="module")
def client():
    """Create test client with lifespan context to start camera/streamer."""
    with TestClient(app) as c:
        yield c


def test_react_app_entrypoint(client: TestClient) -> None:
    """GET / should serve the React SPA index.html with appropriate title and root container."""
    response = client.get("/")
    assert response.status_code == 200
    assert "TRC Photo Booth" in response.text
    assert '<div id="root"></div>' in response.text


def test_react_app_assets_served(client: TestClient) -> None:
    """All JS and CSS assets referenced in index.html must be served properly with HTTP 200."""
    response = client.get("/")
    assert response.status_code == 200

    # Extract all assets
    asset_urls = re.findall(r'(?:src|href)="(?:\./)?(assets/[^"]+)"', response.text)
    assert len(asset_urls) >= 2, "Expected at least 1 CSS and 1 JS bundle in index.html"

    has_js = False
    has_css = False

    for rel_path in asset_urls:
        asset_res = client.get(f"/{rel_path}")
        assert asset_res.status_code == 200, f"Failed to serve asset: {rel_path}"
        assert len(asset_res.content) > 100, f"Asset is too small or empty: {rel_path}"

        if rel_path.endswith(".js"):
            has_js = True
            assert "javascript" in asset_res.headers.get("content-type", "").lower()
        elif rel_path.endswith(".css"):
            has_css = True
            assert "css" in asset_res.headers.get("content-type", "").lower()

    assert has_js, "JavaScript asset was not loaded"
    assert has_css, "CSS stylesheet was not loaded"


def test_test_monitor_preserved(client: TestClient) -> None:
    """GET /test.html must still serve the developer diagnostic tool."""
    response = client.get("/test.html")
    assert response.status_code == 200
    assert "cameraCanvas" in response.text
    assert "TRC Photo Booth" in response.text


def test_camera_controls_contract(client: TestClient) -> None:
    """Verify that all controls exposed in the React Settings modal are supported by the backend."""
    # Test setting FPS
    fps_res = client.post("/api/config", json={"fps": 25})
    assert fps_res.status_code == 200
    assert fps_res.json()["fps"] == 25

    # Test setting Quality
    q_res = client.post("/api/config", json={"quality": 85})
    assert q_res.status_code == 200
    assert q_res.json()["quality"] == 85

    # Test setting Resolution
    res_res = client.post("/api/config", json={"resolution": "720p", "aspect_ratio": "16:9"})
    assert res_res.status_code == 200
    assert res_res.json()["width"] == 1280
    assert res_res.json()["height"] == 720

    # Test toggle flip_horizontal
    flip_res = client.post("/api/config", json={"flip_horizontal": False})
    assert flip_res.status_code == 200
    assert flip_res.json()["flip_horizontal"] is False

    # Restore default flip
    restore_res = client.post("/api/config", json={"flip_horizontal": True, "fps": 15, "quality": 70})
    assert restore_res.status_code == 200
    assert restore_res.json()["flip_horizontal"] is True
