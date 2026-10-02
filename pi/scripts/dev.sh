#!/usr/bin/env bash
# Launch TRC Photo Booth Pi Server in development mode
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PI_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PI_DIR}"

VENV_PYTHON="${PI_DIR}/.venv/bin/python"

if [ ! -f "${VENV_PYTHON}" ]; then
  echo "Virtual environment not found. Setting up..."
  python3 -m venv "${PI_DIR}/.venv"
  "${PI_DIR}/.venv/bin/pip" install --upgrade pip
  "${PI_DIR}/.venv/bin/pip" install -e "${PI_DIR}[dev]"
fi

export PHOTOBOOTH_DEBUG=true
export PYTHONPATH="${PI_DIR}"

echo "=========================================================="
echo " Starting TRC Photo Booth Pi Server (Dev Mode)"
echo " Open http://localhost:8000/ to view live camera monitor"
echo " WebSocket feed: ws://localhost:8000/ws/feed"
echo "=========================================================="

exec "${PI_DIR}/.venv/bin/uvicorn" src.main:app --host 0.0.0.0 --port 8000 --reload
