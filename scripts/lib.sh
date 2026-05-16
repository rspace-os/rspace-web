#!/usr/bin/env bash
#
# Shared helpers for the RSpace sysadmin scripts in this directory.
# Source from another script: `. "$(dirname "$0")/lib.sh"`
#
# AI and Third-Party Code policy reminder: this script was generated with AI
# assistance. Review it under the policy in Drata before running against any
# environment you care about.

# --- url guard ------------------------------------------------------------
# Refuse plain http:// for any host other than localhost / 127.0.0.1, unless
# the operator explicitly sets ALLOW_INSECURE=1. This stops the API key (sent
# via the apiKey HTTP header) being leaked to a MITM on the network, and stops
# a MITM from forging a 2xx response that would cause us to log "deleted" when
# nothing was deleted.
rspace_assert_url_safe() {
  local url="$1"
  case "${url}" in
    https://*) return 0 ;;
    http://localhost*|http://127.0.0.1*) return 0 ;;
    http://*)
      if [[ "${ALLOW_INSECURE:-0}" == "1" ]]; then
        echo "WARNING: sending apiKey over plain HTTP to ${url} (ALLOW_INSECURE=1)" >&2
        return 0
      fi
      echo "Error: refusing to send apiKey over plain HTTP to ${url}." >&2
      echo "  Set ALLOW_INSECURE=1 to override (development only)." >&2
      return 2 ;;
    *)
      echo "Error: RSPACE_BASE_URL must start with http:// or https://" >&2
      return 2 ;;
  esac
}

# --- secure auth header file ---------------------------------------------
# Writes "apiKey: <key>" to a mode-600 mktemp file owned by the current user
# and prints the path. The caller is responsible for installing an EXIT trap
# that rm -fs the path. Putting the header in a file (rather than passing it
# on curl's argv) keeps the apiKey out of `ps -ef` and /proc/<pid>/cmdline.
rspace_write_auth_header_file() {
  local api_key="$1"
  local f
  f="$(mktemp -t rspace-auth.XXXXXX)"
  chmod 600 "${f}"
  printf 'apiKey: %s\n' "${api_key}" > "${f}"
  printf '%s' "${f}"
}

# --- curl with timeout + retry on transient failures ---------------------
# Usage: rspace_curl <body-out-file> <header-file> <method> <url> [extra curl args...]
# Prints the final HTTP status code on stdout. Retries up to 3 times on 5xx
# or curl-failure ("000"), with linear backoff. 2xx/3xx/4xx are returned on
# the first attempt.
rspace_curl() {
  local body_out="$1"; shift
  local header_file="$1"; shift
  local method="$1"; shift
  local url="$1"; shift
  local attempt
  local status
  status="000"
  for attempt in 1 2 3; do
    : > "${body_out}"
    status=$(curl -sS -o "${body_out}" -w "%{http_code}" \
      --connect-timeout 10 --max-time 30 \
      -X "${method}" \
      -H @"${header_file}" \
      "$@" \
      "${url}") || status="000"
    case "${status}" in
      000|5*) sleep "$((attempt * 2))" ;;
      *) break ;;
    esac
  done
  printf '%s' "${status}"
}
