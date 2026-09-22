#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

CADDY_VERSION="$(tr -d '[:space:]' <"${ROOT_DIR}/tools/caddy/VERSION")"
ARCHIVE_DIR="${ROOT_DIR}/tools/caddy/v${CADDY_VERSION}"
RUNTIME_ROOT="${ROOT_DIR}/.local-tools/caddy/${CADDY_VERSION}"
CADDY_SETUP_PORT="${CADDY_SETUP_PORT:-54443}"
TRUST_CONFIG="${ROOT_DIR}/tools/caddy/trust.Caddyfile"

fail() {
  printf '[FAIL] %s\n' "$*" >&2
  exit 1
}

[[ "$(uname -s)" == "Darwin" ]] || fail "This script supports macOS only. Use setup-https.ps1 on Windows."

case "$(uname -m)" in
  arm64) platform="mac_arm64" ;;
  x86_64) platform="mac_amd64" ;;
  *) fail "Unsupported macOS architecture: $(uname -m)" ;;
esac

archive_name="caddy_${CADDY_VERSION}_${platform}.tar.gz"
archive="${ARCHIVE_DIR}/${archive_name}"
checksums="${ARCHIVE_DIR}/caddy_${CADDY_VERSION}_checksums.txt"
runtime_dir="${RUNTIME_ROOT}/${platform}"
caddy="${runtime_dir}/caddy"

[[ -f "${archive}" ]] || fail "Missing bundled archive: ${archive}"
[[ -f "${checksums}" ]] || fail "Missing bundled checksums: ${checksums}"

expected="$(awk -v name="${archive_name}" '$2 == name { print $1 }' "${checksums}")"
[[ -n "${expected}" ]] || fail "No checksum found for ${archive_name}"
actual="$(shasum -a 512 "${archive}" | awk '{ print $1 }')"
[[ "${actual}" == "${expected}" ]] || fail "Checksum mismatch for ${archive_name}"

mkdir -p "${runtime_dir}"
tar -xzf "${archive}" -C "${runtime_dir}" caddy LICENSE
chmod 0755 "${caddy}"

export CADDY_SETUP_PORT

"${caddy}" validate --config "${TRUST_CONFIG}" --adapter caddyfile

if lsof -nP -iTCP:"${CADDY_SETUP_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  fail "Port ${CADDY_SETUP_PORT} is in use. Stop the listener and rerun this setup."
fi

cleanup() {
  "${caddy}" stop >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${caddy}" start --config "${TRUST_CONFIG}" --adapter caddyfile
"${caddy}" trust --config "${TRUST_CONFIG}" --adapter caddyfile
curl -fsS -o /dev/null "https://localhost:${CADDY_SETUP_PORT}/"

cleanup
trap - EXIT

printf '[OK] Local HTTPS trust is prepared for localhost.\n'
printf '[INFO] Restart browsers and desktop clients before testing.\n'
