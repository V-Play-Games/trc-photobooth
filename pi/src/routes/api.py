import asyncio
import base64
import logging
import time
from pathlib import Path
from typing import Any
from fastapi import APIRouter, HTTPException, Request, Response, status
from src.camera import get_camera, list_video_devices, switch_camera_device
from src.capture import capture_gif, capture_photo, get_capture_store
from src.config import settings
from src.database import get_database
from src.models import (
    AdminStatsResponse,
    CameraConfigUpdate,
    CaptureMetadata,
    CaptureTriggerRequest,
    PrintJobRequest,
    PrintResponse,
    PrinterConfigUpdate,
    PrinterStatusResponse,
    PrintedPhotoRecord,
    SystemStatus,
    SystemStatsResponse,
    VideoDeviceInfo,
    parse_resolution,
)
from src.printer import get_printer_service
from src.streamer import get_streamer
from src.system_info import get_system_stats

logger = logging.getLogger("photobooth.api")

router = APIRouter(prefix="/api", tags=["System"])


@router.get("/status", response_model=SystemStatus)
async def get_status() -> SystemStatus:
    """Return live system telemetry, camera backend, fps, and client metrics."""
    cam = get_camera()
    streamer = get_streamer()
    store = get_capture_store()

    is_healthy = getattr(cam, "is_healthy", cam.is_running)
    cam_connected = getattr(cam, "is_connected", True)
    status_str = "ok" if (cam.is_running and cam_connected) else "degraded"
    devs = list_video_devices()

    return SystemStatus(
        status=status_str,
        camera_ready=cam.is_running and cam_connected,
        is_stream_paused=streamer.is_paused,
        camera_backend=cam.backend_name,
        is_mock=cam.is_mock,
        actual_fps=cam.actual_fps,
        target_fps=settings.preview_fps,
        connected_clients=streamer.client_count,
        resolution=f"{settings.preview_width}x{settings.preview_height}",
        frames_sent_total=streamer.frames_sent_total,
        frames_dropped_total=streamer.frames_dropped_total,
        uptime_seconds=streamer.uptime_seconds,
        swap_rb=settings.swap_rb,
        flip_horizontal=settings.flip_horizontal,
        is_healthy=is_healthy,
        memory_usage_mb=store.memory_usage_mb,
        capture_count=len(store.list_all()),
        webcam_device=settings.webcam_device,
        device_path=f"/dev/video{settings.webcam_device}",
        available_devices=[VideoDeviceInfo(**d) for d in devs],
    )


@router.get("/devices")
async def get_video_devices() -> dict[str, Any]:
    """List detected V4L2 video devices on the system."""
    devices = list_video_devices()
    return {
        "current_device": settings.webcam_device,
        "current_path": f"/dev/video{settings.webcam_device}",
        "devices": devices,
    }


@router.get("/health")
async def health_check() -> dict[str, str | float]:
    """Lightweight health check for reverse proxies or watchdog monitoring."""
    return {"status": "ok", "timestamp": time.time()}


@router.get("/config")
async def get_camera_config() -> dict[str, Any]:
    """Get active preview streaming configuration."""
    devices = list_video_devices()
    return {
        "width": settings.preview_width,
        "height": settings.preview_height,
        "fps": settings.preview_fps,
        "quality": settings.preview_quality,
        "swap_rb": settings.swap_rb,
        "flip_horizontal": settings.flip_horizontal,
        "capture_width": settings.capture_width,
        "capture_height": settings.capture_height,
        "capture_quality": settings.capture_quality,
        "webcam_device": settings.webcam_device,
        "device_path": f"/dev/video{settings.webcam_device}",
        "available_devices": devices,
    }


@router.post("/config")
async def update_camera_config(config_update: CameraConfigUpdate) -> dict[str, Any]:
    """Dynamically adjust preview frame rate, JPEG compression quality, color channel order, or flip."""
    cam = get_camera()
    if config_update.fps is not None:
        settings.preview_fps = config_update.fps
        if hasattr(cam, "target_fps"):
            cam.target_fps = config_update.fps
    if config_update.quality is not None:
        settings.preview_quality = config_update.quality
        if hasattr(cam, "quality"):
            cam.quality = config_update.quality

    # Parse and apply resolution (either '720p' style string or explicit width/height)
    res_tuple = parse_resolution(
        resolution=config_update.resolution,
        aspect_ratio=config_update.aspect_ratio or "16:9",
        width=config_update.width,
        height=config_update.height,
    )
    if res_tuple is not None:
        new_width, new_height = res_tuple
        settings.preview_width = new_width
        settings.preview_height = new_height
        if hasattr(cam, "set_resolution"):
            cam.set_resolution(new_width, new_height)
        else:
            cam.width = new_width
            cam.height = new_height

    if config_update.swap_rb is not None:
        settings.swap_rb = config_update.swap_rb

    if config_update.flip_horizontal is not None:
        settings.flip_horizontal = config_update.flip_horizontal
        if hasattr(cam, "flip_horizontal"):
            cam.flip_horizontal = config_update.flip_horizontal

    if config_update.webcam_device is not None:
        switch_camera_device(config_update.webcam_device)
        streamer = get_streamer()
        streamer.camera = get_camera()

    devices = list_video_devices()
    streamer = get_streamer()
    await streamer.broadcast_json({
        "type": "config",
        "fps": settings.preview_fps,
        "quality": settings.preview_quality,
        "width": settings.preview_width,
        "height": settings.preview_height,
        "swap_rb": settings.swap_rb,
        "flip_horizontal": settings.flip_horizontal,
        "webcam_device": settings.webcam_device,
        "device_path": f"/dev/video{settings.webcam_device}",
        "available_devices": devices,
    })

    return {
        "message": "Config updated successfully",
        "fps": settings.preview_fps,
        "quality": settings.preview_quality,
        "width": settings.preview_width,
        "height": settings.preview_height,
        "swap_rb": settings.swap_rb,
        "flip_horizontal": settings.flip_horizontal,
        "webcam_device": settings.webcam_device,
        "device_path": f"/dev/video{settings.webcam_device}",
        "available_devices": devices,
    }


@router.get("/system/stats", response_model=SystemStatsResponse)
async def get_system_hardware_stats() -> SystemStatsResponse:
    """Return on-demand Raspberry Pi system stats (CPU %, RAM used/total, and temperatures).

    Zero compute overhead when idle: stats are measured only when this endpoint is actively called.
    """
    return get_system_stats()


@router.get("/snapshot")
async def get_instant_snapshot() -> Response:
    """Return the latest frame as an immediate image/jpeg download for testing."""
    cam = get_camera()
    frame = cam.get_latest_frame()
    if not frame:
        return Response(content="Camera not ready", status_code=status.HTTP_503_SERVICE_UNAVAILABLE)
    return Response(
        content=frame,
        media_type="image/jpeg",
        headers={"Content-Disposition": "inline; filename=snapshot.jpg"},
    )


# ---------------------------------------------------------------------------
# Phase 2: Capture & GIF Endpoints
# ---------------------------------------------------------------------------


@router.get("/captures", response_model=list[CaptureMetadata])
async def list_recent_captures() -> list[CaptureMetadata]:
    """List recent captures (photos and animated GIFs) stored in memory, newest first."""
    store = get_capture_store()
    return store.list_all()


@router.get("/captures/{capture_id}")
async def get_capture_file(capture_id: str) -> Response:
    """Serve a captured photo (JPEG) or animated GIF by capture ID."""
    store = get_capture_store()
    item = store.get(capture_id, include_disk=True)
    if not item:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Capture '{capture_id}' not found or expired",
        )
    return Response(
        content=item.data,
        media_type=item.content_type,
        headers={
            "Content-Disposition": f"inline; filename={item.filename}",
            "Cache-Control": "public, max-age=3600",
        },
    )


@router.post("/captures/photo", response_model=CaptureMetadata)
@router.post("/capture/photo", response_model=CaptureMetadata, include_in_schema=False)
async def trigger_photo(request: CaptureTriggerRequest | None = None) -> CaptureMetadata:
    """Trigger high-resolution still capture with optional countdown."""
    countdown = request.countdown_seconds if request else 0
    streamer = get_streamer()
    if streamer.is_paused:
        await streamer.resume_stream()

    if countdown > 0:
        await streamer.execute_countdown_and_capture(action="photo", countdown_seconds=countdown)
        store = get_capture_store()
        all_caps = store.list_all()
        if all_caps:
            return all_caps[0]

    item = capture_photo()
    await streamer.broadcast_json({
        "type": "capture_result",
        "data": item.to_metadata().model_dump(),
    })
    return item.to_metadata()


@router.post("/captures/gif", response_model=CaptureMetadata)
@router.post("/capture/gif", response_model=CaptureMetadata, include_in_schema=False)
async def trigger_gif(request: CaptureTriggerRequest | None = None) -> CaptureMetadata:
    """Trigger multi-frame animated GIF capture with optional countdown."""
    countdown = request.countdown_seconds if request else 0
    frames = request.frames if request else 10
    interval_ms = request.interval_ms if request else 150
    streamer = get_streamer()
    if streamer.is_paused:
        await streamer.resume_stream()

    if countdown > 0:
        await streamer.execute_countdown_and_capture(
            action="gif",
            countdown_seconds=countdown,
            frames=frames,
            interval_ms=interval_ms,
        )
        store = get_capture_store()
        all_caps = store.list_all()
        if all_caps:
            return all_caps[0]

    await streamer.broadcast_json({
        "type": "gif_recording",
        "frames": frames,
        "interval_ms": interval_ms,
    })
    item = await capture_gif(frames=frames, interval_ms=interval_ms)
    await streamer.broadcast_json({
        "type": "gif_result",
        "data": item.to_metadata().model_dump(),
    })
    return item.to_metadata()


@router.delete("/captures/{capture_id}")
async def delete_capture(capture_id: str) -> dict[str, Any]:
    """Delete a captured photo or GIF from temporary memory store."""
    store = get_capture_store()
    deleted = store.delete(capture_id)
    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Capture '{capture_id}' not found",
        )
    return {"status": "ok", "deleted": True, "id": capture_id}


# ---------------------------------------------------------------------------
# Physical CUPS Printer Endpoints
# ---------------------------------------------------------------------------


@router.post("/print", response_model=PrintResponse)
async def submit_print_job(
    request: Request,
    capture_id: str | None = None,
    printer_name: str | None = None,
    color_mode: str | None = None,
    copies: int = 1,
    test: bool = False,
) -> PrintResponse:
    """Submit a print job to CUPS (lp -d "TRC_Printer" -o print-color-mode=monochrome).

    Supports:
    1. Direct binary JPEG / PNG payload in HTTP body (Content-Type: image/jpeg or application/octet-stream).
    2. JSON payload with 'image_base64', 'capture_id', or 'test'.
    3. Query parameters (?capture_id=... or ?test=true).
    """
    printer = get_printer_service()
    streamer = get_streamer()
    p_name = printer_name or settings.printer_name
    c_mode = color_mode or settings.printer_color_mode

    content_type = request.headers.get("content-type", "").lower()
    body_bytes = await request.body()

    is_json = "application/json" in content_type
    json_payload: dict[str, Any] = {}
    if is_json and body_bytes:
        try:
            import json
            json_payload = json.loads(body_bytes.decode())
        except Exception:
            pass

    req_test = test or bool(json_payload.get("test", False))
    req_capture_id = capture_id or json_payload.get("capture_id")
    req_printer_name = json_payload.get("printer_name") or p_name
    req_color_mode = json_payload.get("color_mode") or c_mode
    req_copies = json_payload.get("copies") or copies
    req_image_base64 = json_payload.get("image_base64")
    req_filename = json_payload.get("filename", "print_image.jpg")

    if req_test:
        success, msg, job_id, cmd = await printer.print_test(
            printer_name=req_printer_name,
            color_mode=req_color_mode,
        )
    elif req_image_base64:
        try:
            raw_data = base64.b64decode(req_image_base64)
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid base64 image data: {e}",
            )
        success, msg, job_id, cmd = await printer.print_bytes(
            data=raw_data,
            filename=req_filename,
            printer_name=req_printer_name,
            color_mode=req_color_mode,
            copies=req_copies,
        )
    elif req_capture_id:
        store = get_capture_store()
        item = store.get(req_capture_id, include_disk=True)
        if not item:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Capture '{req_capture_id}' not found",
            )
        success, msg, job_id, cmd = await printer.print_bytes(
            data=item.data,
            filename=item.filename,
            printer_name=req_printer_name,
            color_mode=req_color_mode,
            copies=req_copies,
        )
    elif body_bytes and not is_json:
        ext = ".png" if "png" in content_type else ".jpg"
        success, msg, job_id, cmd = await printer.print_bytes(
            data=body_bytes,
            filename=f"print_job{ext}",
            printer_name=req_printer_name,
            color_mode=req_color_mode,
            copies=req_copies,
        )
    else:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Must provide image binary body, 'image_base64', 'capture_id', or set 'test'=true to print",
        )

    await streamer.broadcast_json({
        "type": "print_result",
        "success": success,
        "message": msg,
        "job_id": job_id,
        "printer": req_printer_name,
    })

    if not success:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Printing failed: {msg}",
        )

    # Record printed photo into local SQLite database
    try:
        db = get_database()
        printed_data = raw_data if req_image_base64 else (item.data if req_capture_id else (body_bytes if (body_bytes and not is_json) else None))
        if printed_data:
            db.record_print(
                data=printed_data,
                filename=req_filename,
                printer_name=req_printer_name,
                color_mode=req_color_mode,
                copies=req_copies,
                job_id=job_id,
                status="printed",
            )
    except Exception as e:
        logger.error("Failed recording print in SQLite database: %s", e)

    return PrintResponse(
        success=True,
        message=msg,
        job_id=job_id,
        printer=req_printer_name,
        command=cmd,
    )


@router.post("/print/capture/{capture_id}", response_model=PrintResponse)
async def print_capture_by_id(
    capture_id: str,
    printer_name: str | None = None,
    color_mode: str | None = None,
    copies: int = 1,
) -> PrintResponse:
    """Print an existing still photo or capture stored on the Pi."""
    store = get_capture_store()
    item = store.get(capture_id, include_disk=True)
    if not item:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Capture '{capture_id}' not found",
        )
    printer = get_printer_service()
    p_name = printer_name or settings.printer_name
    c_mode = color_mode or settings.printer_color_mode
    success, msg, job_id, cmd = await printer.print_bytes(
        data=item.data,
        filename=item.filename,
        printer_name=p_name,
        color_mode=c_mode,
        copies=copies,
    )
    if not success:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Printing failed: {msg}",
        )

    # Record printed capture into local SQLite database
    try:
        db = get_database()
        db.record_print(
            data=item.data,
            filename=item.filename,
            printer_name=p_name,
            color_mode=c_mode,
            copies=copies,
            job_id=job_id,
            status="printed",
        )
    except Exception as e:
        logger.error("Failed recording print in SQLite database: %s", e)

    streamer = get_streamer()
    await streamer.broadcast_json({
        "type": "print_result",
        "success": True,
        "job_id": job_id,
        "message": msg,
        "printer": p_name,
    })

    return PrintResponse(
        success=True,
        message=msg,
        job_id=job_id,
        printer=p_name,
        command=cmd,
    )


@router.post("/print/test", response_model=PrintResponse)
async def print_test_page(
    printer_name: str | None = None,
    color_mode: str | None = None,
) -> PrintResponse:
    """Trigger test print of /etc/hostname via CUPS (lp -d 'TRC_Printer' -o print-color-mode=monochrome /etc/hostname)."""
    printer = get_printer_service()
    p_name = printer_name or settings.printer_name
    c_mode = color_mode or settings.printer_color_mode
    success, msg, job_id, cmd = await printer.print_test(
        printer_name=p_name,
        color_mode=c_mode,
    )
    if not success:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Test print failed: {msg}",
        )

    streamer = get_streamer()
    await streamer.broadcast_json({
        "type": "print_result",
        "success": True,
        "job_id": job_id,
        "message": msg,
        "printer": p_name,
    })

    return PrintResponse(
        success=True,
        message=msg,
        job_id=job_id,
        printer=p_name,
        command=cmd,
    )


@router.get("/print/status", response_model=PrinterStatusResponse)
async def get_print_status() -> PrinterStatusResponse:
    """Return CUPS lp subsystem and printer availability status."""
    printer = get_printer_service()
    status_dict = await printer.get_status()
    return PrinterStatusResponse(**status_dict)


@router.get("/print/config")
async def get_printer_config() -> dict[str, Any]:
    """Get active printer configuration on the Pi."""
    return {
        "printer_name": settings.printer_name,
        "color_mode": settings.printer_color_mode,
        "mock_printer": settings.mock_printer,
    }


@router.post("/print/config")
async def update_printer_config(config_update: PrinterConfigUpdate) -> dict[str, Any]:
    """Dynamically adjust default printer queue name or color mode on the Pi."""
    if config_update.printer_name is not None and config_update.printer_name.strip():
        settings.printer_name = config_update.printer_name.strip()
    if config_update.color_mode is not None:
        settings.printer_color_mode = config_update.color_mode

    return {
        "message": "Printer configuration updated successfully",
        "printer_name": settings.printer_name,
        "color_mode": settings.printer_color_mode,
    }


@router.get("/stream/status")
async def get_stream_status() -> dict[str, Any]:
    """Return stream pause state."""
    streamer = get_streamer()
    return {
        "is_paused": streamer.is_paused,
    }


@router.post("/stream/pause")
async def pause_stream() -> dict[str, Any]:
    """Pause camera capture and streaming on the Pi to reduce CPU and cool down."""
    streamer = get_streamer()
    await streamer.pause_stream()
    return {
        "is_paused": True,
        "message": "Camera stream paused (CPU and thermal load reduced)",
    }


@router.post("/stream/resume")
async def resume_stream() -> dict[str, Any]:
    """Resume camera capture and streaming on the Pi."""
    streamer = get_streamer()
    await streamer.resume_stream()
    return {
        "is_paused": False,
        "message": "Camera stream resumed",
    }


@router.post("/stream/toggle")
async def toggle_stream() -> dict[str, Any]:
    """Toggle camera capture and streaming state."""
    streamer = get_streamer()
    is_paused = await streamer.toggle_stream()
    state_desc = "paused" if is_paused else "resumed"
    return {
        "is_paused": is_paused,
        "message": f"Camera stream {state_desc}",
    }


# ---------------------------------------------------------------------------
# Admin Panel Endpoints (Web Admin & Print Archive)
# ---------------------------------------------------------------------------


@router.get("/admin/printed", response_model=list[PrintedPhotoRecord])
async def list_printed_photos(limit: int = 100, offset: int = 0) -> list[PrintedPhotoRecord]:
    """List all printed photos stored in the local SQLite database, newest first."""
    db = get_database()
    photos = db.list_photos(limit=limit, offset=offset)
    return [PrintedPhotoRecord(**p) for p in photos]


@router.get("/admin/printed/{photo_id}/image")
async def get_printed_photo_image(photo_id: int) -> Response:
    """Serve a printed photo JPEG/PNG file for preview/thumbnail in the admin panel."""
    db = get_database()
    record = db.get_photo(photo_id)
    if not record:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Printed photo not found")
    filepath = Path(record["filepath"])
    if not filepath.exists():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Printed photo file missing on disk")
    media_type = "image/png" if filepath.suffix.lower() == ".png" else "image/jpeg"
    return Response(
        content=filepath.read_bytes(),
        media_type=media_type,
        headers={
            "Content-Disposition": f"inline; filename={record['filename']}",
            "Cache-Control": "public, max-age=3600",
        },
    )


@router.get("/admin/printed/{photo_id}/download")
async def download_printed_photo(photo_id: int) -> Response:
    """Download the full printed photo as an attachment."""
    db = get_database()
    record = db.get_photo(photo_id)
    if not record:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Printed photo not found")
    filepath = Path(record["filepath"])
    if not filepath.exists():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Printed photo file missing on disk")
    media_type = "image/png" if filepath.suffix.lower() == ".png" else "image/jpeg"
    return Response(
        content=filepath.read_bytes(),
        media_type=media_type,
        headers={
            "Content-Disposition": f"attachment; filename={record['filename']}",
        },
    )


@router.post("/admin/printed/{photo_id}/reprint", response_model=PrintResponse)
async def reprint_photo(photo_id: int, copies: int = 1) -> PrintResponse:
    """Re-print an archived photo from the local SQLite database."""
    db = get_database()
    record = db.get_photo(photo_id)
    if not record:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Printed photo not found")
    filepath = Path(record["filepath"])
    if not filepath.exists():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Printed photo file missing on disk")

    printer = get_printer_service()
    p_name = record.get("printer_name") or settings.printer_name
    c_mode = record.get("color_mode") or settings.printer_color_mode
    success, msg, job_id, cmd = await printer.print_file(
        filepath=filepath,
        printer_name=p_name,
        color_mode=c_mode,
        copies=copies,
    )
    if not success:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"Reprint failed: {msg}")

    # Record reprint in database
    try:
        db.record_print(
            data=filepath.read_bytes(),
            filename=f"reprint_{record['filename']}",
            printer_name=p_name,
            color_mode=c_mode,
            copies=copies,
            job_id=job_id,
            status="printed",
        )
    except Exception as e:
        logger.error("Failed recording reprint: %s", e)

    return PrintResponse(
        success=True,
        message=f"Reprint submitted successfully: {msg}",
        job_id=job_id,
        printer=p_name,
        command=cmd,
    )


@router.delete("/admin/printed/{photo_id}")
async def delete_printed_photo(photo_id: int) -> dict[str, Any]:
    """Delete a printed photo record and file from the Pi."""
    db = get_database()
    if not db.delete_photo(photo_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Printed photo not found")
    return {"success": True, "message": f"Printed photo #{photo_id} deleted"}


@router.get("/admin/stats", response_model=AdminStatsResponse)
async def get_admin_stats() -> AdminStatsResponse:
    """Return aggregated admin dashboard statistics including SQLite print count and live hardware telemetry."""
    db = get_database()
    db_stats = db.get_stats()
    sys_stats = get_system_stats()
    streamer = get_streamer()
    printer = get_printer_service()
    p_status = await printer.get_status()

    return AdminStatsResponse(
        total_printed=db_stats["total_printed"],
        today_printed=db_stats["today_printed"],
        total_copies=db_stats["total_copies"],
        total_size_bytes=db_stats["total_size_bytes"],
        total_size_mb=db_stats["total_size_mb"],
        cpu_temp_c=sys_stats.cpu_temp_c,
        cpu_percent=sys_stats.cpu_percent,
        memory_percent=sys_stats.memory.percent,
        memory_used_mb=sys_stats.memory.used_mb,
        memory_total_mb=sys_stats.memory.total_mb,
        printer_name=settings.printer_name,
        printer_ready=p_status.get("is_ready", True),
        is_stream_paused=streamer.is_paused,
    )


