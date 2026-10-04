"""Integration tests for web admin endpoints and SQLite database."""

import io
import pytest
from PIL import Image
from fastapi.testclient import TestClient
from src.main import app
from src.config import settings


@pytest.fixture(scope="module")
def client():
    """Create test client with lifespan context."""
    with TestClient(app) as c:
        yield c


@pytest.fixture(autouse=True)
def setup_mock_printer():
    """Ensure mock_printer is enabled for tests."""
    old_mock = settings.mock_printer
    settings.mock_printer = True
    yield
    settings.mock_printer = old_mock


def test_admin_html_page_served(client: TestClient) -> None:
    """GET /admin and /admin/ should serve the web admin HTML page."""
    res1 = client.get("/admin")
    assert res1.status_code == 200
    assert "text/html" in res1.headers["content-type"]
    assert "TRC Photo Booth" in res1.text
    assert "Admin Console" in res1.text
    assert "CPU Thermal Sensor" in res1.text
    assert "SHOW LIVE FEED" in res1.text

    res2 = client.get("/admin/")
    assert res2.status_code == 200
    assert "TRC Photo Booth" in res2.text


def test_admin_stats_endpoint(client: TestClient) -> None:
    """GET /api/admin/stats should return system metrics, temperatures and photo stats."""
    res = client.get("/api/admin/stats")
    assert res.status_code == 200
    data = res.json()
    assert "total_printed" in data
    assert "today_printed" in data
    assert "total_copies" in data
    assert "total_size_bytes" in data
    assert "cpu_temp_c" in data
    assert "cpu_percent" in data
    assert "memory_percent" in data


def test_record_print_and_download(client: TestClient) -> None:
    """Simulate a print job, verify it is recorded in DB and downloadable."""
    # Generate test JPEG bytes
    img = Image.new("RGB", (200, 400), color="blue")
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    jpeg_bytes = buf.getvalue()

    # Call print API
    res = client.post(
        "/api/print",
        content=jpeg_bytes,
        headers={"Content-Type": "image/jpeg"},
    )
    assert res.status_code == 200
    print_data = res.json()
    job_id = print_data["job_id"]
    assert str(job_id) != ""

    # Verify photo shows in admin printed list
    list_res = client.get("/api/admin/printed")
    assert list_res.status_code == 200
    photos = list_res.json()
    assert isinstance(photos, list)
    assert len(photos) > 0
    matched = [p for p in photos if p["job_id"] == str(job_id)]
    assert len(matched) == 1
    photo = matched[0]
    photo_id = photo["id"]

    # Test download endpoint
    download_res = client.get(f"/api/admin/printed/{photo_id}/download")
    assert download_res.status_code == 200
    assert download_res.headers["content-type"] == "image/jpeg"
    assert "attachment" in download_res.headers["content-disposition"]
    assert len(download_res.content) == len(jpeg_bytes)

    # Test inline preview endpoint
    image_res = client.get(f"/api/admin/printed/{photo_id}/image")
    assert image_res.status_code == 200
    assert image_res.headers["content-type"] == "image/jpeg"
    assert "inline" in image_res.headers["content-disposition"]

    # Test delete endpoint
    del_res = client.delete(f"/api/admin/printed/{photo_id}")
    assert del_res.status_code == 200
    assert del_res.json()["success"] is True

    # Ensure it no longer exists
    not_found_res = client.get(f"/api/admin/printed/{photo_id}/download")
    assert not_found_res.status_code == 404
