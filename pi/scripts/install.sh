#!/usr/bin/env bash
# Installation & systemd setup script for Raspberry Pi OS
set -euo pipefail

echo "==> Setting up TRC Photo Booth on Raspberry Pi..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PI_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Ensure system dependencies (libcamera, python3-pip, etc.)
if command -v apt-get &> /dev/null; then
    echo "==> Updating apt and installing system camera prerequisites..."
    sudo apt-get update
    sudo apt-get install -y python3-pip python3-venv python3-libcamera python3-picamera2
fi

# Set up virtual environment with system site packages so picamera2 is accessible
echo "==> Creating Python virtual environment (with system-site-packages for picamera2)..."
python3 -m venv --system-site-packages "${PI_DIR}/.venv"

echo "==> Installing Python dependencies..."
"${PI_DIR}/.venv/bin/pip" install --upgrade pip
"${PI_DIR}/.venv/bin/pip" install -r "${PI_DIR}/requirements.txt"

echo "==> Installation complete!"
echo "To run the server:"
echo "  ${PI_DIR}/scripts/dev.sh"
