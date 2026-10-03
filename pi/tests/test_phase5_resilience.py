import os
import shutil
import socket
import tempfile
import time
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from src.capture import CaptureItem, CaptureStore
from src.config import settings
from src.discovery import ServiceAdvertiser
from src.gpio_status import LedState, GpioStatusIndicator
from src.streamer import ClientSession, Streamer
from src.watchdog import SystemdNotifier, Watchdog


class TestCaptureStoreResilience:
    def test_byte_limit_eviction(self):
        # Create store with small byte limit (1000 bytes)
        store = CaptureStore(max_captures=10, max_bytes=1000, persist_to_disk=False)

        # Add 3 items of 400 bytes each
        data1 = b"A" * 400
        data2 = b"B" * 400
        data3 = b"C" * 400

        item1 = CaptureItem(
            id="item1",
            content_type="image/jpeg",
            data=data1,
            filename="item1.jpg",
            width=10,
            height=10,
            created_at=time.time(),
            capture_type="photo",
        )
        item2 = CaptureItem(
            id="item2",
            content_type="image/jpeg",
            data=data2,
            filename="item2.jpg",
            width=10,
            height=10,
            created_at=time.time() + 1,
            capture_type="photo",
        )
        item3 = CaptureItem(
            id="item3",
            content_type="image/jpeg",
            data=data3,
            filename="item3.jpg",
            width=10,
            height=10,
            created_at=time.time() + 2,
            capture_type="photo",
        )

        store.add(item1)
        store.add(item2)
        assert store.get("item1") is not None
        assert store.get("item2") is not None
        assert store.total_bytes == 800

        # Adding item3 (total would be 1200 > 1000) should evict item1
        store.add(item3)
        assert store.get("item1") is None
        assert store.get("item2") is not None
        assert store.get("item3") is not None
        assert store.total_bytes <= 1000

    def test_disk_persistence_fallback(self):
        temp_dir = tempfile.mkdtemp(prefix="photobooth_test_persist_")
        try:
            store = CaptureStore(
                max_captures=2,
                max_bytes=10000,
                persist_to_disk=True,
                captures_dir=Path(temp_dir),
            )

            data = b"PHOTO_PAYLOAD_TEST"
            item = CaptureItem(
                id="persisted_1",
                content_type="image/jpeg",
                data=data,
                filename="persisted_1.jpg",
                width=100,
                height=100,
                created_at=time.time(),
                capture_type="photo",
            )
            store.add(item)

            # File should exist on disk
            disk_path = os.path.join(temp_dir, "persisted_1.jpg")
            assert os.path.exists(disk_path)

            # Evict from RAM directly
            del store._captures["persisted_1"]
            assert store.get("persisted_1", include_disk=False) is None

            # Retrieve with include_disk=True
            recovered = store.get("persisted_1", include_disk=True)
            assert recovered is not None
            assert recovered.data == data
            assert recovered.id == "persisted_1"
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)


class TestStreamerResilience:
    @pytest.mark.anyio
    async def test_frame_dropping_under_load(self):
        ws_mock = AsyncMock()
        session = ClientSession(websocket=ws_mock)

        # Fill the bounded queue (capacity 1)
        session.offer_frame(b"frame_1")
        assert session.frames_dropped == 0

        # Offer frame_2 while frame_1 is still pending in queue
        session.offer_frame(b"frame_2")
        # Older frame was discarded to avoid lag
        assert session.frames_dropped == 1
        assert session.frame_queue.get_nowait() == b"frame_2"

    @pytest.mark.anyio
    async def test_streamer_degraded_idle_status(self):
        camera = MagicMock()
        streamer = Streamer(camera=camera)
        assert streamer.client_count == 0
        assert streamer.is_degraded is False

    @pytest.mark.anyio
    async def test_streamer_frames_dropped_accounting(self):
        camera = MagicMock()
        streamer = Streamer(camera=camera)
        ws_mock = AsyncMock()
        session = streamer.register(ws_mock)

        # Trigger 2 frame drops
        session.offer_frame(b"f1")
        session.offer_frame(b"f2")
        session.offer_frame(b"f3")

        assert session.frames_dropped == 2
        assert streamer.frames_dropped_total == 2

        # Unregister and verify historical accounting preserved
        streamer.unregister(session)
        assert streamer.client_count == 0
        assert streamer.frames_dropped_total == 2


class TestWatchdog:
    def test_systemd_notifier_socket(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            sock_path = os.path.join(tmpdir, "notify.sock")
            server_sock = socket.socket(socket.AF_UNIX, socket.SOCK_DGRAM)
            server_sock.bind(sock_path)
            server_sock.setblocking(False)

            with patch.dict(os.environ, {"NOTIFY_SOCKET": sock_path}):
                notifier = SystemdNotifier()
                assert notifier._enabled is True
                assert notifier.notify_watchdog() is True

                data = server_sock.recv(1024)
                assert b"WATCHDOG=1" in data

            server_sock.close()

    def test_watchdog_kick(self):
        camera_mock = MagicMock()
        cam_instance = MagicMock()
        cam_instance.is_running = True
        cam_instance.last_frame_time = time.time()
        camera_mock.return_value = cam_instance

        wd = Watchdog(timeout_seconds=5.0, get_camera_fn=camera_mock)
        wd.kick()
        assert time.time() - wd._last_kick < 1.0


class TestGpioStatusIndicator:
    def test_status_led_transitions(self):
        led = GpioStatusIndicator(pin=-1)
        assert led.state == LedState.OFF

        led.set_state(LedState.READY)
        assert led.state == LedState.READY

        led.set_state(LedState.CAPTURING)
        assert led.state == LedState.CAPTURING

        led.set_state(LedState.ERROR)
        assert led.state == LedState.ERROR

        led.set_state(LedState.OFF)
        assert led.state == LedState.OFF
        led.close() if hasattr(led, "close") else None


class TestServiceDiscovery:
    def test_advertiser_lifecycle(self):
        with patch.object(settings, "enable_mdns", False):
            advertiser = ServiceAdvertiser(port=8000)
            advertiser.start()
            assert advertiser._is_advertising is False
            advertiser.stop()
