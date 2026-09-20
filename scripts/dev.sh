#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

REQUESTED_WEB_PORT="${WEB_PORT:-51888}"
WEB_PORT="${REQUESTED_WEB_PORT}"
while lsof -nP -iTCP:"${WEB_PORT}" -sTCP:LISTEN >/dev/null 2>&1; do
  WEB_PORT=$((WEB_PORT + 1))
done

if [[ "${WEB_PORT}" != "${REQUESTED_WEB_PORT}" ]]; then
  printf 'Port %s is in use; web will run on %s.\n' \
    "${REQUESTED_WEB_PORT}" "${WEB_PORT}"
fi

REQUESTED_WEB_URL="http://localhost:${REQUESTED_WEB_PORT}"
LOCAL_WEB_URL="http://localhost:${WEB_PORT}"
if [[ -z "${CLIENT_ORIGIN:-}" || "${CLIENT_ORIGIN}" == "${REQUESTED_WEB_URL}" ]]; then
  CLIENT_ORIGIN="${LOCAL_WEB_URL}"
fi
if [[ -z "${WEB_BASE_URL:-}" || "${WEB_BASE_URL}" == "${REQUESTED_WEB_URL}" ]]; then
  WEB_BASE_URL="${LOCAL_WEB_URL}"
fi

export WEB_PORT CLIENT_ORIGIN WEB_BASE_URL
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:${PORT:-55888}}"

cleanup() {
  kill "${API_PID:-}" "${WEB_PID:-}" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

(
  cd "${ROOT_DIR}/services/api"
  exec ./gradlew bootRun
) &
API_PID=$!

(
  cd "${ROOT_DIR}/apps/web"
  exec npm run dev
) &
WEB_PID=$!

wait "${API_PID}" "${WEB_PID}"
