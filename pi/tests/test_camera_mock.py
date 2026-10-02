"""Unit tests for MockCamera, webcam input, and camera capture lifecycle."""

import time
from src.camera import MockCamera


def test_mock_camera_generates_valid_jpeg() -> None:
    """MockCamera should generate a valid JPEG byte buffer with JPEG magic header."""
    cam = MockCamera(width=320, height=240, target_fps=10, quality=60, use_webcam=False)
    frame = cam._generate_frame(1)

    assert isinstance(frame, bytes)
    assert len(frame) > 1000
    # Check JPEG magic bytes SOI (Start of Image)
    assert frame[:2] == b"\xff\xd8"
    # Check JPEG EOI (End of Image)
    assert frame[-2:] == b"\xff\xd9"


def test_mock_camera_high_res_capture() -> None:
    """MockCamera should produce high resolution still capture bytes."""
    cam = MockCamera(use_webcam=False)
    high_res = cam.capture_high_res()

    assert isinstance(high_res, bytes)
    assert high_res[:2] == b"\xff\xd8"


def test_mock_camera_synthetic_thread_lifecycle() -> None:
    """MockCamera synthetic capture loop should start, produce frames, and cleanly stop."""
    cam = MockCamera(width=320, height=240, target_fps=20, use_webcam=False)
    assert not cam.is_running
    assert cam.get_latest_frame() is None

    cam.start()
    assert cam.is_running

    # Wait for at least one frame to be captured
    timeout = time.time() + 2.0
    frame = None
    while time.time() < timeout:
        frame = cam.get_latest_frame()
        if frame is not None:
            break
        time.sleep(0.05)

    assert frame is not None
    assert frame[:2] == b"\xff\xd8"

    cam.stop()
    assert not cam.is_running


def test_mock_camera_webcam_mode() -> None:
    """MockCamera with use_webcam=True should initialize and produce valid JPEG frames."""
    cam = MockCamera(width=320, height=240, target_fps=15, use_webcam=True)
    cam.start()
    assert cam.is_running

    timeout = time.time() + 3.0
    frame = None
    while time.time() < timeout:
        frame = cam.get_latest_frame()
        if frame is not None:
            break
        time.sleep(0.05)

    assert frame is not None
    assert frame[:2] == b"\xff\xd8"
    assert cam.backend_name in ("webcam", "mock")

    cam.stop()
    assert not cam.is_running


def test_dynamic_fps_and_quality_update() -> None:
    """Camera target_fps and quality should be dynamically updatable at runtime."""
    cam = MockCamera(width=320, height=240, target_fps=10, quality=50, use_webcam=False)
    assert cam.target_fps == 10
    assert cam.quality == 50

    cam.target_fps = 25
    assert cam.target_fps == 25

    cam.quality = 85
    assert cam.quality == 85
