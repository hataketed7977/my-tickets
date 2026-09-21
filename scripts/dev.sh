#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${MY_TICKETS_STATE_DIR:-/tmp/my-tickets}"

STARTED_SERVICES=()
STARTED_PIDS=()
RUN_PID_FILES=()

info() { printf '[INFO]  %s\n' "$*"; }
success() { printf '[OK]    %s\n' "$*"; }
warn() { printf '[WARN]  %s\n' "$*"; }
fail() { printf '[FAIL]  %s\n' "$*"; }

if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

API_PORT="${API_PORT:-${PORT:-55888}}"
PORT="${API_PORT}"
WEB_PORT="${WEB_PORT:-51888}"

LOCAL_WEB_URL="http://localhost:${WEB_PORT}"
if [[ -z "${CLIENT_ORIGIN:-}" ]]; then
  CLIENT_ORIGIN="${LOCAL_WEB_URL}"
fi
if [[ -z "${WEB_BASE_URL:-}" ]]; then
  WEB_BASE_URL="${LOCAL_WEB_URL}"
fi

export API_PORT PORT WEB_PORT CLIENT_ORIGIN WEB_BASE_URL
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:${API_PORT}}"

ensure_state_dir() {
  mkdir -p "${STATE_DIR}"
}

rotate_log() {
  local log_file="$1"
  if [[ -f "${log_file}" ]]; then
    mv "${log_file}" "${log_file}.prev"
  fi
}

stop_port_listeners() {
  local port="$1"
  local pids

  pids="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -z "${pids}" ]]; then
    return
  fi

  warn "Stopping process(es) on port ${port}: ${pids//$'\n'/ }"
  kill ${pids} 2>/dev/null || true

  for _ in {1..20}; do
    if ! lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
      return
    fi
    sleep 0.25
  done

  pids="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "${pids}" ]]; then
    warn "Force stopping process(es) on port ${port}: ${pids//$'\n'/ }"
    kill -9 ${pids} 2>/dev/null || true
  fi
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local log_file="$3"
  local max_seconds="${4:-90}"
  local elapsed=0

  while [[ "${elapsed}" -lt "${max_seconds}" ]]; do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      success "${label} is ready (${url})"
      return
    fi

    if ! service_running "${label}"; then
      fail "${label} exited before becoming ready. Last log lines:"
      tail -n 30 "${log_file}" 2>/dev/null || true
      exit 1
    fi

    sleep 2
    elapsed=$((elapsed + 2))
  done

  fail "${label} did not become ready in ${max_seconds}s (${url}). Last log lines:"
  tail -n 30 "${log_file}" 2>/dev/null || true
  exit 1
}

service_running() {
  local name="$1"
  local i

  for i in "${!STARTED_SERVICES[@]}"; do
    if [[ "${STARTED_SERVICES[$i]}" == "${name}" ]]; then
      kill -0 "${STARTED_PIDS[$i]}" >/dev/null 2>&1
      return
    fi
  done

  return 1
}

start_service() {
  local name="$1"
  local dir="$2"
  local log_file="$3"
  local pid_file="$4"
  shift 4

  rotate_log "${log_file}"

  info "Starting ${name}..."
  (
    cd "${dir}"
    exec "$@"
  ) >"${log_file}" 2>&1 &

  local pid=$!
  printf '%s\n' "${pid}" >"${pid_file}"
  STARTED_SERVICES+=("${name}")
  STARTED_PIDS+=("${pid}")
  RUN_PID_FILES+=("${pid_file}")
  success "${name} started (pid ${pid}, log ${log_file})"
}

cleanup_started_services() {
  local count="${#STARTED_PIDS[@]}"
  if [[ "${count}" -eq 0 ]]; then
    return
  fi

  info "Stopping services started by this run..."

  local i
  for ((i = count - 1; i >= 0; i--)); do
    local pid="${STARTED_PIDS[$i]}"
    local name="${STARTED_SERVICES[$i]}"
    if kill -0 "${pid}" >/dev/null 2>&1; then
      info "Stopping ${name} (pid ${pid})"
      kill "${pid}" >/dev/null 2>&1 || true
    fi
  done

  sleep 1

  for ((i = count - 1; i >= 0; i--)); do
    local pid="${STARTED_PIDS[$i]}"
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill -9 "${pid}" >/dev/null 2>&1 || true
    fi
  done

  for i in "${!RUN_PID_FILES[@]}"; do
    local pid_file="${RUN_PID_FILES[$i]}"
    local pid="${STARTED_PIDS[$i]}"
    if [[ -f "${pid_file}" ]] && [[ "$(cat "${pid_file}")" == "${pid}" ]]; then
      rm -f "${pid_file}"
    fi
  done

  STARTED_SERVICES=()
  STARTED_PIDS=()
  RUN_PID_FILES=()
}

on_interrupt() {
  cleanup_started_services
  exit 130
}

supervise_foreground() {
  info "Foreground supervision is active. Press Ctrl+C to stop started services."

  while true; do
    local i
    for i in "${!STARTED_PIDS[@]}"; do
      local pid="${STARTED_PIDS[$i]}"
      local name="${STARTED_SERVICES[$i]}"
      if ! kill -0 "${pid}" >/dev/null 2>&1; then
        fail "${name} exited. See ${STATE_DIR}/${name}.log"
        cleanup_started_services
        exit 1
      fi
    done
    sleep 2
  done
}

main() {
  trap cleanup_started_services EXIT
  trap on_interrupt INT TERM

  ensure_state_dir

  local api_pid_file="${STATE_DIR}/api.pid"
  local web_pid_file="${STATE_DIR}/web.pid"
  local api_log="${STATE_DIR}/api.log"
  local web_log="${STATE_DIR}/web.log"

  stop_port_listeners "${API_PORT}"
  stop_port_listeners "${WEB_PORT}"
  rm -f "${api_pid_file}" "${web_pid_file}"

  start_service "api" "${ROOT_DIR}/services/api" "${api_log}" "${api_pid_file}" ./gradlew bootRun
  wait_for_http "http://localhost:${API_PORT}/api/auth/config" "api" "${api_log}" 120

  start_service "web" "${ROOT_DIR}/apps/web" "${web_log}" "${web_pid_file}" npm run dev
  wait_for_http "http://localhost:${WEB_PORT}/" "web" "${web_log}" 60

  success "ticket center local development is ready"
  printf '\nServices:\n'
  printf '  API: http://localhost:%s\n' "${API_PORT}"
  printf '  Web: http://localhost:%s\n' "${WEB_PORT}"
  printf '\nLogs:\n'
  printf '  API: %s\n' "${api_log}"
  printf '  Web: %s\n' "${web_log}"
  printf '\nPID files:\n'
  printf '  %s\n' "${api_pid_file}"
  printf '  %s\n\n' "${web_pid_file}"

  supervise_foreground
}

main "$@"
