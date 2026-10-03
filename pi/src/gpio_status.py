"""GPIO Status LED indicator for camera and server state on Raspberry Pi."""

from __future__ import annotations

import enum
import logging
import threading
import time
from typing import Any

from src.config import settings

logger = logging.getLogger("photobooth.gpio")


class LedState(enum.Enum):
    OFF = "off"
    READY = "ready"  # Solid ON
    HEARTBEAT = "heartbeat"  # 1 pulse every 2s
    CAPTURING = "capturing"  # Fast 5Hz blink
    ERROR = "error"  # Rapid alert flash


class GpioStatusIndicator:
    """Controls a physical status LED on a Raspberry Pi GPIO pin."""

    def __init__(self, pin: int = settings.status_led_pin) -> None:
        self.pin = pin
        self._state = LedState.OFF
        self._running = False
        self._thread: threading.Thread | None = None
        self._backend = "none"
        self._gpio_obj: Any = None
        self._init_backend()

    def _init_backend(self) -> None:
        if self.pin < 0:
            logger.debug("Status LED is disabled (status_led_pin=%d)", self.pin)
            return

        # 1. Attempt gpiod (modern Linux/Pi OS standard)
        try:
            import gpiod  # type: ignore

            chip = gpiod.Chip("gpiochip0")
            line = chip.get_line(self.pin)
            line.request(consumer="photobooth", type=gpiod.LINE_REQ_DIR_OUT)
            self._gpio_obj = line
            self._backend = "gpiod"
            logger.info("Configured status LED on GPIO %d using gpiod", self.pin)
            return
        except Exception:
            pass

        # 2. Attempt RPi.GPIO
        try:
            import RPi.GPIO as GPIO  # type: ignore

            GPIO.setmode(GPIO.BCM)
            GPIO.setup(self.pin, GPIO.OUT, initial=GPIO.LOW)
            self._gpio_obj = GPIO
            self._backend = "rpi_gpio"
            logger.info("Configured status LED on GPIO %d using RPi.GPIO", self.pin)
            return
        except Exception:
            pass

        # 3. Fallback dummy
        self._backend = "mock"
        logger.debug("Physical GPIO unavailable on this host; using mock LED status indicator")

    def _set_output(self, high: bool) -> None:
        val = 1 if high else 0
        if self._backend == "gpiod" and self._gpio_obj:
            try:
                self._gpio_obj.set_value(val)
            except Exception:
                pass
        elif self._backend == "rpi_gpio" and self._gpio_obj:
            try:
                self._gpio_obj.output(self.pin, val)
            except Exception:
                pass

    def set_state(self, state: LedState) -> None:
        """Update active LED indication pattern."""
        self._state = state

    @property
    def state(self) -> LedState:
        """Current LED state."""
        return self._state

    def start(self) -> None:
        """Start LED pattern thread."""
        if self._running or self.pin < 0:
            return
        self._running = True
        self._thread = threading.Thread(target=self._pattern_loop, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        """Stop LED pattern and turn off LED."""
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=1.0)
        self._set_output(False)

    def _pattern_loop(self) -> None:
        """Pattern loop driving LED output timing."""
        while self._running:
            state = self._state

            if state == LedState.OFF:
                self._set_output(False)
                time.sleep(0.2)
            elif state == LedState.READY:
                self._set_output(True)
                time.sleep(0.2)
            elif state == LedState.HEARTBEAT:
                self._set_output(True)
                time.sleep(0.08)
                self._set_output(False)
                time.sleep(0.12)
                self._set_output(True)
                time.sleep(0.08)
                self._set_output(False)
                time.sleep(1.2)
            elif state == LedState.CAPTURING:
                self._set_output(True)
                time.sleep(0.1)
                self._set_output(False)
                time.sleep(0.1)
            elif state == LedState.ERROR:
                self._set_output(True)
                time.sleep(0.05)
                self._set_output(False)
                time.sleep(0.05)


_gpio_indicator: GpioStatusIndicator | None = None


def get_gpio_indicator() -> GpioStatusIndicator:
    """Retrieve or create the GpioStatusIndicator singleton."""
    global _gpio_indicator
    if _gpio_indicator is None:
        _gpio_indicator = GpioStatusIndicator()
    return _gpio_indicator
