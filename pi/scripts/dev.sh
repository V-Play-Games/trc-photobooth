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

MY_IP=$(hostname -I 2>/dev/null | awk '{print $1}')

echo "=========================================================="
echo " Starting TRC Photo Booth Pi Server (Dev Mode)"
echo " Open in browser:"
echo "   Local:   http://localhost:8000/"
if [ -n "${MY_IP}" ]; then
  echo "   Network: http://${MY_IP}:8000/"
fi
echo "   WebSocket: ws://${MY_IP:-localhost}:8000/ws/feed"
echo "=========================================================="

exec "${PI_DIR}/.venv/bin/uvicorn" src.main:app --host 0.0.0.0 --port 8000 --reload
