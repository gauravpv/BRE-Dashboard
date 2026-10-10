#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$SCRIPT_DIR}"
PID_FILE="${PID_FILE:-$APP_DIR/bre-dashboard.pid}"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

if [[ ! -f "$PID_FILE" ]]; then
    echo "BRE Dashboard is not running: PID file not found at $PID_FILE."
    exit 0
fi

app_pid="$(sed -n '1p' "$PID_FILE")"
jar_file="$(sed -n '2p' "$PID_FILE")"

if [[ ! "$app_pid" =~ ^[0-9]+$ ]]; then
    echo "Invalid PID file: $PID_FILE."
    exit 1
fi

if ! kill -0 "$app_pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "BRE Dashboard is already stopped. Removed stale PID file."
    exit 0
fi

jar_name="$(basename -- "$jar_file")"
command_line="$(ps -p "$app_pid" -o args= 2>/dev/null || true)"
if [[ -z "$jar_name" || "$command_line" != *"$jar_name"* ]]; then
    echo "PID $app_pid does not belong to the recorded BRE Dashboard JAR; refusing to stop it."
    exit 1
fi

echo "Stopping BRE Dashboard (PID $app_pid)..."
kill "$app_pid"

for ((elapsed = 0; elapsed < STOP_TIMEOUT; elapsed++)); do
    if ! kill -0 "$app_pid" 2>/dev/null; then
        rm -f "$PID_FILE"
        echo "BRE Dashboard stopped."
        exit 0
    fi
    sleep 1
done

echo "Graceful shutdown timed out after ${STOP_TIMEOUT}s; forcing stop."
kill -KILL "$app_pid"
rm -f "$PID_FILE"
echo "BRE Dashboard stopped."