"""Unit and integration tests for Phase 2: Capture, GIF generation, and CaptureStore."""

import pytest
import time
from fastapi.testclient import TestClient
from src.capture import CaptureItem, CaptureStore, capture_gif, capture_photo, get_capture_store
from src.main import app


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:
        yield c


def test_capture_store_add_get_and_delete() -> None:
    """CaptureStore should save items, retrieve them, and allow deletion."""
    store = CaptureStore(max_captures=10, ttl_seconds=60.0)
    item = CaptureItem(
        id="test_photo_1",
        content_type="image/jpeg",
        data=b"\xff\xd8\xff\xd9",
        filename="test_photo_1.jpg",
        width=640,
        height=480,
        created_at=time.time(),
        capture_type="photo",
    )
    store.add(item)

    retrieved = store.get("test_photo_1")
    assert retrieved is not None
    assert retrieved.id == "test_photo_1"
    assert retrieved.size_bytes == 4

    meta_list = store.list_all()
    assert len(meta_list) == 1
    assert meta_list[0].id == "test_photo_1"
    assert meta_list[0].url == "/api/captures/test_photo_1"

    deleted = store.delete("test_photo_1")
    assert deleted is True
    assert store.get("test_photo_1") is None
    assert len(store.list_all()) == 0


def test_capture_store_capacity_eviction() -> None:
    """CaptureStore should enforce max_captures capacity by evicting oldest item."""
    store = CaptureStore(max_captures=3, ttl_seconds=60.0)
    for i in range(5):
        item = CaptureItem(
            id=f"item_{i}",
            content_type="image/jpeg",
            data=b"\xff\xd8",
            filename=f"item_{i}.jpg",
            width=100,
            height=100,
            created_at=time.time() + i,
            capture_type="photo",
        )
        store.add(item)

    # Max capacity is 3, items 0 and 1 should have been evicted
    assert len(store.list_all()) == 3
    assert store.get("item_0") is None
    assert store.get("item_1") is None
    assert store.get("item_2") is not None
    assert store.get("item_3") is not None
    assert store.get("item_4") is not None


def test_capture_store_ttl_expiration() -> None:
    """CaptureStore should expire items exceeding ttl_seconds."""
    store = CaptureStore(max_captures=10, ttl_seconds=0.1)
    item = CaptureItem(
        id="expired_item",
        content_type="image/jpeg",
        data=b"\xff\xd8",
        filename="expired.jpg",
        width=100,
        height=100,
        created_at=time.time() - 1.0,  # 1 second in the past
        capture_type="photo",
    )
    store.add(item)

    assert store.get("expired_item") is None
    assert len(store.list_all()) == 0


def test_capture_photo_generates_valid_jpeg() -> None:
    """capture_photo should take a high-res capture and register it in store."""
    item = capture_photo()
    assert isinstance(item, CaptureItem)
    assert item.content_type == "image/jpeg"
    assert len(item.data) > 500
    assert item.data[:2] == b"\xff\xd8"
    assert item.capture_type == "photo"

    store = get_capture_store()
    retrieved = store.get(item.id)
    assert retrieved is not None
    assert retrieved.id == item.id


@pytest.mark.anyio
async def test_capture_gif_generates_valid_animated_gif() -> None:
    """capture_gif should assemble burst frames into an animated GIF."""
    item = await capture_gif(frames=4, interval_ms=50)
    assert isinstance(item, CaptureItem)
    assert item.content_type == "image/gif"
    assert len(item.data) > 1000
    assert item.data[:6] == b"GIF89a"
    assert item.capture_type == "gif"
    assert item.frames == 4

    store = get_capture_store()
    retrieved = store.get(item.id)
    assert retrieved is not None
    assert retrieved.id == item.id


def test_rest_captures_endpoints(client: TestClient) -> None:
    """REST endpoints should trigger, list, retrieve, and delete captures."""
    # 1. Trigger photo
    photo_res = client.post("/api/captures/photo", json={"countdown_seconds": 0})
    assert photo_res.status_code == 200
    photo_data = photo_res.json()
    assert photo_data["type"] == "photo"
    photo_id = photo_data["id"]

    # 2. Retrieve photo binary
    get_photo = client.get(f"/api/captures/{photo_id}")
    assert get_photo.status_code == 200
    assert get_photo.headers["content-type"] == "image/jpeg"
    assert get_photo.content[:2] == b"\xff\xd8"

    # 3. Trigger GIF
    gif_res = client.post(
        "/api/captures/gif",
        json={"countdown_seconds": 0, "frames": 3, "interval_ms": 50},
    )
    assert gif_res.status_code == 200
    gif_data = gif_res.json()
    assert gif_data["type"] == "gif"
    gif_id = gif_data["id"]

    # 4. Retrieve GIF binary
    get_gif = client.get(f"/api/captures/{gif_id}")
    assert get_gif.status_code == 200
    assert get_gif.headers["content-type"] == "image/gif"
    assert get_gif.content[:6] == b"GIF89a"

    # 5. List captures
    list_res = client.get("/api/captures")
    assert list_res.status_code == 200
    all_captures = list_res.json()
    ids = [c["id"] for c in all_captures]
    assert photo_id in ids
    assert gif_id in ids

    # 6. Delete capture
    del_res = client.delete(f"/api/captures/{photo_id}")
    assert del_res.status_code == 200
    assert del_res.json()["deleted"] is True

    # 7. Subsequent get should return 404
    get_deleted = client.get(f"/api/captures/{photo_id}")
    assert get_deleted.status_code == 404


def test_websocket_capture_and_countdown(client: TestClient) -> None:
    """WebSocket should broadcast countdown ticks and capture results."""
    import json

    def wait_for_json(ws, timeout=4.0):
        start = time.time()
        while time.time() - start < timeout:
            raw = ws.receive()
            if "text" in raw:
                data = json.loads(raw["text"])
                if data.get("type") != "stream_status":
                    return data
        raise TimeoutError("Timed out waiting for JSON text frame")

    with client.websocket_connect("/ws/feed") as ws:
        # Trigger photo capture with 1s countdown
        ws.send_text(json.dumps({"action": "trigger_capture", "countdown": 1}))

        # Expect countdown tick 1
        msg1 = wait_for_json(ws)
        assert msg1["type"] == "countdown_tick"
        assert msg1["seconds_left"] == 1

        # Expect countdown tick 0
        msg0 = wait_for_json(ws)
        assert msg0["type"] == "countdown_tick"
        assert msg0["seconds_left"] == 0

        # Expect capture result
        res_msg = wait_for_json(ws)
        assert res_msg["type"] == "capture_result"
        assert "url" in res_msg["data"]
        assert res_msg["data"]["type"] == "photo"

        # Trigger GIF capture with 0s countdown
        ws.send_text(json.dumps({"action": "trigger_gif", "countdown": 0, "frames": 3, "interval_ms": 50}))
        rec_msg = wait_for_json(ws)
        assert rec_msg["type"] == "gif_recording"

        gif_res_msg = wait_for_json(ws)
        assert gif_res_msg["type"] == "gif_result"
        assert gif_res_msg["data"]["type"] == "gif"
        assert "url" in gif_res_msg["data"]
