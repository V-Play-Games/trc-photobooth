"""mDNS / Zeroconf network service advertisement for TRC Photo Booth."""

from __future__ import annotations

import logging
import socket
from src.config import settings

logger = logging.getLogger("photobooth.discovery")


class ServiceAdvertiser:
    """Advertises the TRC Photo Booth on the local network using mDNS (DNS-SD)."""

    def __init__(
        self,
        service_name: str = settings.mdns_service_name,
        port: int = settings.port,
    ) -> None:
        self.service_name = service_name
        self.port = port
        self._zeroconf = None
        self._service_info = None
        self._is_advertising = False

    def _get_local_ip(self) -> str:
        """Discover the primary LAN IP address of this machine."""
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            # Does not need to be reachable; used to route local interface
            s.connect(("10.255.255.255", 1))
            ip = s.getsockname()[0]
        except Exception:
            ip = "127.0.0.1"
        finally:
            s.close()
        return ip

    def start(self) -> None:
        """Register the _photobooth._tcp service on the local network."""
        if not settings.enable_mdns:
            logger.debug("mDNS advertisement is disabled in settings")
            return
        if self._is_advertising:
            return

        try:
            from zeroconf import IPVersion, ServiceInfo, Zeroconf

            local_ip = self._get_local_ip()
            ip_bytes = socket.inet_aton(local_ip)
            hostname = f"{socket.gethostname()}.local."

            service_type = "_photobooth._tcp.local."
            full_name = f"{self.service_name}.{service_type}"

            properties = {
                b"name": self.service_name.encode("utf-8"),
                b"version": b"0.1.0",
                b"ws_path": b"/ws/feed",
            }

            self._service_info = ServiceInfo(
                type_=service_type,
                name=full_name,
                addresses=[ip_bytes],
                port=self.port,
                properties=properties,
                server=hostname,
            )

            self._zeroconf = Zeroconf(ip_version=IPVersion.V4Only)
            self._zeroconf.register_service(self._service_info)
            self._is_advertising = True
            logger.info(
                "mDNS service '%s' successfully advertised on %s:%d (%s)",
                full_name,
                local_ip,
                self.port,
                service_type,
            )
        except ImportError:
            logger.info("zeroconf library not installed; skipping in-app mDNS advertisement")
        except Exception as exc:
            logger.warning("Failed to start mDNS advertisement: %s", exc)

    def stop(self) -> None:
        """Unregister the service from the network."""
        if not self._is_advertising or self._zeroconf is None:
            return

        try:
            if self._service_info:
                self._zeroconf.unregister_service(self._service_info)
            self._zeroconf.close()
            logger.info("mDNS advertisement stopped")
        except Exception as exc:
            logger.debug("Error stopping mDNS: %s", exc)
        finally:
            self._is_advertising = False
            self._zeroconf = None
            self._service_info = None


_advertiser_instance: ServiceAdvertiser | None = None


def get_service_advertiser() -> ServiceAdvertiser:
    """Retrieve or create the global ServiceAdvertiser singleton."""
    global _advertiser_instance
    if _advertiser_instance is None:
        _advertiser_instance = ServiceAdvertiser()
    return _advertiser_instance
