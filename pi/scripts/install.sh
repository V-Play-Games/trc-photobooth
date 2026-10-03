#!/usr/bin/env bash
# Installation & systemd setup script for Raspberry Pi OS
set -euo pipefail

echo "==> Setting up TRC Photo Booth on Raspberry Pi..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PI_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Ensure system dependencies (libcamera, python3-pip, etc.)
if command -v apt-get &> /dev/null; then
    echo "==> Updating apt and installing system camera prerequisites & Avahi mDNS..."
    sudo apt-get update
    sudo apt-get install -y python3-pip python3-venv python3-libcamera python3-picamera2 avahi-daemon avahi-utils
fi

# Set up virtual environment with system site packages so picamera2 is accessible
echo "==> Creating Python virtual environment (with system-site-packages for picamera2)..."
python3 -m venv --system-site-packages "${PI_DIR}/.venv"

echo "==> Installing Python dependencies via pyproject.toml..."
"${PI_DIR}/.venv/bin/pip" install --upgrade pip
"${PI_DIR}/.venv/bin/pip" install -e "${PI_DIR}[dev,pi]"

# Optional systemd & avahi setup
if [ -d "/etc/avahi/services" ] && [ -w "/etc/avahi/services" ]; then
    echo "==> Configuring Avahi mDNS service..."
    sudo cp "${PI_DIR}/scripts/avahi/photobooth.service" /etc/avahi/services/photobooth.service
    sudo systemctl restart avahi-daemon || true
fi

echo "==> Installation complete!"
echo ""
echo "To run interactively in dev mode:"
echo "  ${PI_DIR}/scripts/dev.sh"
echo ""
echo "To install as a systemd service (auto-start on boot with watchdog):"
echo "  sudo cp ${PI_DIR}/scripts/photobooth.service /etc/systemd/system/"
echo "  sudo systemctl daemon-reload"
echo "  sudo systemctl enable --now photobooth.service"
