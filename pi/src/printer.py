"""Hardware printer subsystem using CUPS lp CLI on Raspberry Pi."""

import asyncio
import logging
import os
import re
import shutil
import socket
import time
import uuid
from pathlib import Path
from typing import Any

from src.config import settings

logger = logging.getLogger("photobooth.printer")


class PrinterService:
    """Manages physical printing jobs via CUPS lp command."""

    def __init__(self) -> None:
        self.spool_dir: Path = settings.captures_dir / "print_spool"
        self._ensure_spool_dir()

    def _ensure_spool_dir(self) -> None:
        try:
            self.spool_dir.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            logger.warning("Could not create printer spool dir %s: %s", self.spool_dir, e)

    def clean_spool_dir(self, max_age_seconds: int = 600) -> None:
        """Remove temporary spooled files older than max_age_seconds."""
        now = time.time()
        if not self.spool_dir.exists():
            return
        for item in self.spool_dir.iterdir():
            if item.is_file():
                try:
                    if now - item.stat().st_mtime > max_age_seconds:
                        item.unlink(missing_ok=True)
                except Exception as e:
                    logger.debug("Failed cleaning spool file %s: %s", item, e)

    async def print_file(
        self,
        filepath: Path | str,
        printer_name: str | None = None,
        color_mode: str | None = None,
        copies: int = 1,
        options: dict[str, str] | None = None,
    ) -> tuple[bool, str, str | None, str]:
        """Print a file using CUPS `lp` command.

        Returns:
            (success: bool, message: str, job_id: str | None, command: str)
        """
        p_name = printer_name or settings.printer_name
        c_mode = color_mode or settings.printer_color_mode
        file_p = Path(filepath)

        if not file_p.exists():
            return False, f"File to print not found: {file_p}", None, ""

        cmd: list[str] = [
            "lp",
            "-d",
            p_name,
            "-o",
            f"print-color-mode={c_mode}",
        ]
        if copies > 1:
            cmd.extend(["-n", str(copies)])

        if options:
            for k, v in options.items():
                cmd.extend(["-o", f"{k}={v}"])

        cmd.append(str(file_p.resolve()))
        cmd_str = " ".join(cmd)

        # Mock printer bypass for development or environments without CUPS
        if settings.mock_printer or not shutil.which("lp"):
            logger.info("[MOCK PRINT] %s", cmd_str)
            job_id = f"mock-job-{uuid.uuid4().hex[:6]}"
            return True, f"Mock print job submitted to '{p_name}'", job_id, cmd_str

        logger.info("Executing print command: %s", cmd_str)
        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout_bytes, stderr_bytes = await proc.communicate()
            stdout_str = stdout_bytes.decode(errors="replace").strip()
            stderr_str = stderr_bytes.decode(errors="replace").strip()

            if proc.returncode == 0:
                # Extract job ID if available e.g. "request id is TRC_Printer-10 (1 file(s))"
                match = re.search(r"request id is ([^\s]+)", stdout_str, re.IGNORECASE)
                job_id = match.group(1) if match else None
                msg = stdout_str or f"Print job sent to {p_name}"
                logger.info("Print success: %s", msg)
                return True, msg, job_id, cmd_str
            else:
                err_msg = stderr_str or stdout_str or f"lp exited with code {proc.returncode}"
                logger.error("Print failed (%d): %s", proc.returncode, err_msg)
                return False, err_msg, None, cmd_str

        except Exception as e:
            logger.exception("Failed to execute lp command: %s", e)
            return False, str(e), None, cmd_str

    async def print_bytes(
        self,
        data: bytes,
        filename: str = "print_job.jpg",
        printer_name: str | None = None,
        color_mode: str | None = None,
        copies: int = 1,
        options: dict[str, str] | None = None,
    ) -> tuple[bool, str, str | None, str]:
        """Write payload bytes to spool and print."""
        self._ensure_spool_dir()
        self.clean_spool_dir()

        safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", filename)
        spool_file = self.spool_dir / f"print_{int(time.time())}_{uuid.uuid4().hex[:6]}_{safe_name}"
        try:
            spool_file.write_bytes(data)
        except Exception as e:
            return False, f"Failed writing spool file: {e}", None, ""

        return await self.print_file(
            spool_file,
            printer_name=printer_name,
            color_mode=color_mode,
            copies=copies,
            options=options,
        )

    async def print_test(
        self,
        printer_name: str | None = None,
        color_mode: str | None = None,
    ) -> tuple[bool, str, str | None, str]:
        """Print test document (/etc/hostname as requested by user)."""
        p_name = printer_name or settings.printer_name
        c_mode = color_mode or settings.printer_color_mode

        hostname_file = Path("/etc/hostname")
        if hostname_file.exists():
            test_path = hostname_file
        else:
            # Fallback if running on system without /etc/hostname
            self._ensure_spool_dir()
            test_path = self.spool_dir / "test_hostname.txt"
            test_path.write_text(f"{socket.gethostname()}\nTRC Photo Booth Test Print\n")

        return await self.print_file(
            test_path,
            printer_name=p_name,
            color_mode=c_mode,
            copies=1,
        )

    async def get_status(self) -> dict[str, Any]:
        """Inspect CUPS lp and lpstat status."""
        lp_installed = shutil.which("lp") is not None
        lpstat_installed = shutil.which("lpstat") is not None
        available_printers: list[str] = []
        is_ready = False
        status_msg = "lp command available" if lp_installed else "lp command not found"

        if lpstat_installed:
            try:
                proc = await asyncio.create_subprocess_exec(
                    "lpstat",
                    "-p",
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                )
                stdout_b, _ = await proc.communicate()
                out_str = stdout_b.decode(errors="replace")
                # Lines like "printer TRC_Printer is idle..."
                matches = re.findall(r"printer\s+([^\s]+)", out_str)
                available_printers = list(dict.fromkeys(matches))

                if settings.printer_name in available_printers:
                    is_ready = True
                    status_msg = f"Printer '{settings.printer_name}' ready in CUPS"
                elif available_printers:
                    status_msg = f"Found printers: {', '.join(available_printers)} (target '{settings.printer_name}' not listed)"
                else:
                    status_msg = "No CUPS destinations currently configured"
            except Exception as e:
                status_msg = f"Error running lpstat: {e}"
        elif lp_installed:
            # lp exists, assume ready if mock or pi
            is_ready = True

        return {
            "printer_name": settings.printer_name,
            "color_mode": settings.printer_color_mode,
            "lp_installed": lp_installed,
            "available_printers": available_printers,
            "is_ready": is_ready or settings.mock_printer,
            "status_message": status_msg,
        }


# Singleton instance
_printer_service: PrinterService | None = None


def get_printer_service() -> PrinterService:
    """Return the global PrinterService singleton."""
    global _printer_service
    if _printer_service is None:
        _printer_service = PrinterService()
    return _printer_service
