"""Local SQLite database manager for tracking and archiving printed photos on the Raspberry Pi."""

import datetime
import logging
import re
import sqlite3
import time
import uuid
from pathlib import Path
from typing import Any

from src.config import settings

logger = logging.getLogger("photobooth.database")


class PrintDatabase:
    """Manages persistent SQLite storage and disk archives for all printed photos."""

    def __init__(self, db_path: Path | str | None = None, printed_dir: Path | str | None = None) -> None:
        self.captures_dir = settings.captures_dir
        self.db_path = Path(db_path) if db_path else self.captures_dir / "photobooth.db"
        self.printed_dir = Path(printed_dir) if printed_dir else self.captures_dir / "printed"
        self._ensure_storage()
        self.init_db()

    def _ensure_storage(self) -> None:
        """Create directories for database and printed file archives."""
        try:
            self.captures_dir.mkdir(parents=True, exist_ok=True)
            self.printed_dir.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            logger.warning("Could not create directories for database: %s", e)

    def _get_connection(self) -> sqlite3.Connection:
        """Create a new SQLite connection with foreign keys and dict-like rows."""
        conn = sqlite3.connect(str(self.db_path), timeout=10.0)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode = WAL;")
        conn.execute("PRAGMA synchronous = NORMAL;")
        return conn

    def init_db(self) -> None:
        """Initialize the SQLite tables and indexes."""
        self._ensure_storage()
        with self._get_connection() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS printed_photos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id TEXT,
                    filename TEXT NOT NULL,
                    filepath TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    copies INTEGER DEFAULT 1,
                    color_mode TEXT DEFAULT 'monochrome',
                    printer_name TEXT DEFAULT 'TRC_Printer',
                    status TEXT DEFAULT 'printed',
                    error_message TEXT,
                    printed_at TEXT NOT NULL
                );
                """
            )
            conn.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_printed_at ON printed_photos(printed_at DESC);
                """
            )
            conn.commit()
        logger.info("Initialized SQLite database at %s", self.db_path)

    def record_print(
        self,
        data: bytes,
        filename: str = "printed_strip.jpg",
        printer_name: str | None = None,
        color_mode: str | None = None,
        copies: int = 1,
        job_id: str | None = None,
        status: str = "printed",
        error_message: str | None = None,
    ) -> dict[str, Any]:
        """Save printed image bytes permanently to disk and record into SQLite database."""
        self._ensure_storage()
        now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
        timestamp_prefix = int(time.time())
        rand_suffix = uuid.uuid4().hex[:6]

        safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", filename)
        if not safe_name.lower().endswith((".jpg", ".jpeg", ".png")):
            safe_name = f"{safe_name}.jpg"

        saved_filename = f"print_{timestamp_prefix}_{rand_suffix}_{safe_name}"
        dest_path = self.printed_dir / saved_filename

        try:
            dest_path.write_bytes(data)
            file_size = len(data)
        except Exception as e:
            logger.error("Failed to write printed photo to disk: %s", e)
            file_size = len(data)

        p_name = printer_name or settings.printer_name
        c_mode = color_mode or settings.printer_color_mode

        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO printed_photos (
                    job_id, filename, filepath, file_size, copies,
                    color_mode, printer_name, status, error_message, printed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """,
                (
                    job_id,
                    filename,
                    str(dest_path.resolve()),
                    file_size,
                    copies,
                    c_mode,
                    p_name,
                    status,
                    error_message,
                    now_iso,
                ),
            )
            conn.commit()
            new_id = cursor.lastrowid

        return self.get_photo(new_id) or {}

    def get_photo(self, photo_id: int) -> dict[str, Any] | None:
        """Fetch a single printed photo record by ID."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM printed_photos WHERE id = ?;", (photo_id,))
            row = cursor.fetchone()
            if not row:
                return None
            return self._format_row(dict(row))

    def list_photos(self, limit: int = 100, offset: int = 0) -> list[dict[str, Any]]:
        """List printed photo records, newest first."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT * FROM printed_photos
                ORDER BY id DESC
                LIMIT ? OFFSET ?;
                """,
                (limit, offset),
            )
            rows = cursor.fetchall()
            return [self._format_row(dict(row)) for row in rows]

    def delete_photo(self, photo_id: int) -> bool:
        """Delete record and underlying file from disk."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT filepath FROM printed_photos WHERE id = ?;", (photo_id,))
            row = cursor.fetchone()
            if not row:
                return False

            filepath = Path(row["filepath"])
            try:
                if filepath.exists():
                    filepath.unlink(missing_ok=True)
            except Exception as e:
                logger.warning("Error deleting file %s: %s", filepath, e)

            cursor.execute("DELETE FROM printed_photos WHERE id = ?;", (photo_id,))
            conn.commit()
            return cursor.rowcount > 0

    def get_stats(self) -> dict[str, Any]:
        """Compute aggregate print statistics for admin dashboard."""
        today_start = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT COUNT(*), COALESCE(SUM(copies), 0), COALESCE(SUM(file_size), 0) FROM printed_photos;")
            total_prints, total_copies, total_bytes = cursor.fetchone()

            cursor.execute("SELECT COUNT(*) FROM printed_photos WHERE printed_at >= ?;", (today_start,))
            today_prints = cursor.fetchone()[0]

        total_mb = round(total_bytes / (1024.0 * 1024.0), 2)
        return {
            "total_printed": total_prints,
            "today_printed": today_prints,
            "total_copies": total_copies,
            "total_size_bytes": total_bytes,
            "total_size_mb": total_mb,
        }

    def _format_row(self, row: dict[str, Any]) -> dict[str, Any]:
        """Add UI URLs to row dictionary."""
        p_id = row["id"]
        row["image_url"] = f"/api/admin/printed/{p_id}/image"
        row["download_url"] = f"/api/admin/printed/{p_id}/download"
        return row


# Singleton instance
_db_instance: PrintDatabase | None = None


def get_database() -> PrintDatabase:
    """Return the global PrintDatabase singleton."""
    global _db_instance
    if _db_instance is None:
        _db_instance = PrintDatabase()
    return _db_instance
