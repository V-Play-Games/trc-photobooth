"""Unit tests for CUPS printer subsystem and print API routes."""

import base64
import time
import pytest
from fastapi.testclient import TestClient

from src.capture import CaptureItem, get_capture_store
from src.config import settings
from src.main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def setup_mock_printer():
    """Ensure mock_printer is enabled for tests so lp command doesn't require physical hardware."""
    old_mock = settings.mock_printer
    settings.mock_printer = True
    yield
    settings.mock_printer = old_mock


def test_printer_status():
    """GET /api/print/status should return printer availability."""
    response = client.get("/api/print/status")
    assert response.status_code == 200
    data = response.json()
    assert data["printer_name"] == "TRC_Printer"
    assert data["color_mode"] == "monochrome"
    assert "lp_installed" in data
    assert "is_ready" in data


def test_test_print_endpoint():
    """POST /api/print/test triggers test print of /etc/hostname."""
    response = client.post("/api/print/test")
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["printer"] == "TRC_Printer"
    assert "command" in data
    assert "-d TRC_Printer" in data["command"]
    assert "-o print-color-mode=monochrome" in data["command"]


def test_print_raw_binary_image():
    """POST /api/print with raw image bytes should spool and print."""
    dummy_bytes = b"\xff\xd8\xff\xe0\x00\x10JFIF" + b"\x00" * 100
    response = client.post(
        "/api/print",
        content=dummy_bytes,
        headers={"Content-Type": "image/jpeg"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["printer"] == "TRC_Printer"
    assert "job_id" in data


def test_print_json_base64():
    """POST /api/print with JSON image_base64 payload."""
    dummy_bytes = b"\xff\xd8\xff\xe0\x00\x10JFIF" + b"\x00" * 100
    b64_str = base64.b64encode(dummy_bytes).decode("ascii")
    response = client.post(
        "/api/print",
        json={"image_base64": b64_str, "filename": "test_collage.jpg"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["printer"] == "TRC_Printer"
    assert "job_id" in data


def test_print_capture_id():
    """POST /api/print with capture_id should fetch capture and print."""
    store = get_capture_store()
    item = CaptureItem(
        id="print_test_cap_01",
        capture_type="photo",
        content_type="image/jpeg",
        filename="test_photo.jpg",
        data=b"fake-photo-data-for-print",
        width=640,
        height=480,
        created_at=time.time(),
    )
    store.add(item)

    response = client.post("/api/print", json={"capture_id": "print_test_cap_01"})
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True

    # Also test convenience endpoint POST /api/print/capture/{capture_id}
    res2 = client.post("/api/print/capture/print_test_cap_01")
    assert res2.status_code == 200
    assert res2.json()["success"] is True


def test_print_missing_params_error():
    """POST /api/print without file, capture_id, or test flag should return 400."""
    response = client.post("/api/print", json={})
    assert response.status_code == 400
    assert "Must provide" in response.json()["detail"]


def test_print_nonexistent_capture_error():
    """POST /api/print with non-existent capture_id should return 404."""
    response = client.post("/api/print", json={"capture_id": "non_existent_123"})
    assert response.status_code == 404


def test_printer_config_endpoints():
    """GET and POST /api/print/config should view and adjust printer settings."""
    # 1. Get current config
    res1 = client.get("/api/print/config")
    assert res1.status_code == 200
    assert "printer_name" in res1.json()
    assert "color_mode" in res1.json()

    # 2. Update config
    res2 = client.post(
        "/api/print/config",
        json={"printer_name": "Custom_Photo_Printer", "color_mode": "color"},
    )
    assert res2.status_code == 200
    data2 = res2.json()
    assert data2["printer_name"] == "Custom_Photo_Printer"
    assert data2["color_mode"] == "color"

    # Reset back to default
    client.post(
        "/api/print/config",
        json={"printer_name": "TRC_Printer", "color_mode": "monochrome"},
    )

