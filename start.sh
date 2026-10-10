#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$SCRIPT_DIR}"
PID_FILE="${PID_FILE:-$APP_DIR/bre-dashboard.pid}"
CONSOLE_LOG="${CONSOLE_LOG:-$APP_DIR/logs/console.log}"
JAVA_BIN="${JAVA_BIN:-java}"

if [[ -f "$PID_FILE" ]]; then
    existing_pid="$(head -n 1 "$PID_FILE" 2>/dev/null || true)"
    if [[ "$existing_pid" =~ ^[0-9]+$ ]] && kill -0 "$existing_pid" 2>/dev/null; then
        echo "BRE Dashboard is already running with PID $existing_pid."
        exit 1
    fi
    rm -f "$PID_FILE"
fi

if [[ -n "${JAR_FILE:-}" ]]; then
    if [[ "$JAR_FILE" != /* ]]; then
        JAR_FILE="$APP_DIR/$JAR_FILE"
    fi
else
    JAR_FILE=""
    for candidate in "$APP_DIR"/bre-dashboard-*.jar; do
        [[ -f "$candidate" ]] || continue
        if [[ -z "$JAR_FILE" || "$candidate" -nt "$JAR_FILE" ]]; then
            JAR_FILE="$candidate"
        fi
    done
fi

if [[ -z "$JAR_FILE" || ! -f "$JAR_FILE" ]]; then
    echo "No bre-dashboard-*.jar found in $APP_DIR. Build or copy the JAR first."
    exit 1
fi

mkdir -p "$(dirname -- "$CONSOLE_LOG")"

java_args=()
if [[ -n "${JAVA_OPTS:-}" ]]; then
    read -r -a java_args <<< "$JAVA_OPTS"
fi

cd "$APP_DIR"
nohup "$JAVA_BIN" "${java_args[@]}" -jar "$JAR_FILE" >> "$CONSOLE_LOG" 2>&1 < /dev/null &
app_pid=$!
printf '%s\n%s\n' "$app_pid" "$JAR_FILE" > "$PID_FILE"

sleep 2
if ! kill -0 "$app_pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "BRE Dashboard failed to start. Check $CONSOLE_LOG."
    exit 1
fi

echo "BRE Dashboard started in the background with PID $app_pid."
echo "Console output: $CONSOLE_LOG"
echo "Application log: ${BRE_DASHBOARD_LOG_PATH:-$APP_DIR/logs}/bre-dashboard.log"