"""System telemetry collector for Raspberry Pi hardware metrics.

Provides zero-overhead, on-demand readings of:
- CPU utilization percentage (calculated via delta ticks in /proc/stat)
- CPU temperature (via /sys/class/thermal, hwmon, or vcgencmd)
- Memory usage (used, free, total, percentage via /proc/meminfo)
- Load averages and Pi throttling flags

No background polling threads are run; compute is only consumed when telemetry is requested.
"""

from __future__ import annotations

import glob
import logging
import os
import subprocess
import time
from typing import Tuple

from src.models import MemoryStats, SystemStatsResponse

logger = logging.getLogger("photobooth.system_info")


class SystemMonitor:
    """Lightweight, on-demand hardware telemetry reader for Linux / Raspberry Pi."""

    def __init__(self) -> None:
        self._last_cpu_time: float = 0.0
        self._last_idle_time: float = 0.0
        self._last_total_time: float = 0.0

    def _read_cpu_jiffies(self) -> Tuple[float, float] | None:
        """Read total and idle CPU jiffies from /proc/stat."""
        try:
            with open("/proc/stat", "r", encoding="utf-8") as f:
                line = f.readline()
            if not line.startswith("cpu "):
                return None
            fields = [float(x) for x in line.split()[1:]]
            if len(fields) < 4:
                return None
            idle = fields[3] + (fields[4] if len(fields) > 4 else 0.0)
            total = sum(fields)
            return idle, total
        except Exception:
            return None

    def get_cpu_percent(self) -> float:
        """Compute CPU utilization percentage.

        If called repeatedly within 0.2s - 10.0s, computes exact delta jiffies without sleep.
        On cold requests, samples for a brief 50ms interval to return an accurate instant reading.
        """
        now = time.time()
        jiffies = self._read_cpu_jiffies()
        if jiffies is None:
            # Fallback to load average divided by cpu count (e.g. non-Linux host)
            try:
                load1 = os.getloadavg()[0]
                cpus = os.cpu_count() or 1
                return round(min(100.0, max(0.0, (load1 / cpus) * 100.0)), 1)
            except Exception:
                return 0.0

        curr_idle, curr_total = jiffies
        time_elapsed = now - self._last_cpu_time

        # Warm delta computation: previous sample was recent
        if 0.2 <= time_elapsed <= 10.0 and self._last_total_time > 0:
            delta_total = curr_total - self._last_total_time
            delta_idle = curr_idle - self._last_idle_time
            self._last_cpu_time = now
            self._last_idle_time = curr_idle
            self._last_total_time = curr_total
            if delta_total > 0:
                pct = 100.0 * (1.0 - (delta_idle / delta_total))
                return round(min(100.0, max(0.0, pct)), 1)
            return 0.0

        # Cold request or stale sample: take a quick 50ms delta sample
        time.sleep(0.05)
        second_jiffies = self._read_cpu_jiffies()
        now2 = time.time()
        if second_jiffies is not None:
            sec_idle, sec_total = second_jiffies
            delta_total = sec_total - curr_total
            delta_idle = sec_idle - curr_idle
            self._last_cpu_time = now2
            self._last_idle_time = sec_idle
            self._last_total_time = sec_total
            if delta_total > 0:
                pct = 100.0 * (1.0 - (delta_idle / delta_total))
                return round(min(100.0, max(0.0, pct)), 1)
            return 0.0

        self._last_cpu_time = now
        self._last_idle_time = curr_idle
        self._last_total_time = curr_total
        return 0.0

    def get_cpu_temp(self) -> float | None:
        """Read Raspberry Pi or Linux system thermal sensor in degrees Celsius."""
        # 1. Standard Raspberry Pi thermal zone 0
        try:
            with open("/sys/class/thermal/thermal_zone0/temp", "r", encoding="utf-8") as f:
                val = float(f.read().strip())
                return round(val / 1000.0, 1)
        except Exception:
            pass

        # 2. Linux hwmon temperature inputs
        for path in sorted(glob.glob("/sys/class/hwmon/hwmon*/temp*_input")):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    val = float(f.read().strip())
                    return round(val / 1000.0, 1)
            except Exception:
                pass

        # 3. Raspberry Pi vcgencmd utility
        try:
            out = subprocess.check_output(
                ["vcgencmd", "measure_temp"],
                text=True,
                timeout=0.3,
                stderr=subprocess.DEVNULL,
            )
            # Output format: "temp=48.2'C"
            cleaned = out.strip().replace("temp=", "").replace("'C", "")
            return round(float(cleaned), 1)
        except Exception:
            pass

        return None

    def get_memory(self) -> MemoryStats:
        """Read RAM memory statistics from /proc/meminfo."""
        total_kb = free_kb = avail_kb = buffers_kb = cached_kb = 0.0
        try:
            with open("/proc/meminfo", "r", encoding="utf-8") as f:
                for line in f:
                    parts = line.split()
                    if not parts:
                        continue
                    key = parts[0].rstrip(":")
                    val = float(parts[1])
                    if key == "MemTotal":
                        total_kb = val
                    elif key == "MemFree":
                        free_kb = val
                    elif key == "MemAvailable":
                        avail_kb = val
                    elif key == "Buffers":
                        buffers_kb = val
                    elif key == "Cached":
                        cached_kb = val
        except Exception:
            # Fallback if /proc/meminfo doesn't exist
            return MemoryStats(total_mb=1024.0, used_mb=0.0, free_mb=1024.0, percent=0.0)

        if avail_kb == 0.0:
            avail_kb = free_kb + buffers_kb + cached_kb
        used_kb = max(0.0, total_kb - avail_kb)
        total_mb = round(total_kb / 1024.0, 1)
        used_mb = round(used_kb / 1024.0, 1)
        free_mb = round(avail_kb / 1024.0, 1)
        pct = round((used_kb / total_kb * 100.0), 1) if total_kb > 0 else 0.0

        return MemoryStats(
            total_mb=total_mb,
            used_mb=used_mb,
            free_mb=free_mb,
            percent=pct,
        )

    def get_throttled(self) -> str | None:
        """Check for under-voltage or thermal throttling flags on Raspberry Pi."""
        try:
            out = subprocess.check_output(
                ["vcgencmd", "get_throttled"],
                text=True,
                timeout=0.3,
                stderr=subprocess.DEVNULL,
            )
            # format: "throttled=0x0"
            return out.strip().replace("throttled=", "")
        except Exception:
            return None

    def get_stats(self) -> SystemStatsResponse:
        """Collect all system telemetry synchronously on demand."""
        load = (
            [round(x, 2) for x in os.getloadavg()]
            if hasattr(os, "getloadavg")
            else [0.0, 0.0, 0.0]
        )
        return SystemStatsResponse(
            cpu_percent=self.get_cpu_percent(),
            cpu_temp_c=self.get_cpu_temp(),
            memory=self.get_memory(),
            load_avg=load,
            cpu_count=os.cpu_count() or 1,
            throttled=self.get_throttled(),
            timestamp=time.time(),
        )


# Global singleton monitor
_monitor_instance: SystemMonitor | None = None


def get_system_stats() -> SystemStatsResponse:
    """Retrieve on-demand system stats singleton."""
    global _monitor_instance
    if _monitor_instance is None:
        _monitor_instance = SystemMonitor()
    return _monitor_instance.get_stats()
