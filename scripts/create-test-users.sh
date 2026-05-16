#!/usr/bin/env bash
#
# Create N RSpace users named <prefix>1 .. <prefix>N via the sysadmin API.
# This script is intended for populating a test/dev environment only.
#
# AI and Third-Party Code policy reminder: this script was generated with AI
# assistance. Review it under the policy in Drata before running against any
# environment you care about.
#
# Usage:
#   prefix='foo' [RSPACE_BASE_URL='http://host:port'] ./create-test-users.sh
#
# Required environment variables:
#   prefix    Username prefix; users '<prefix>1' .. '<prefix>${USER_COUNT}' are created.
#
# Optional environment variables:
#   RSPACE_BASE_URL    RSpace server base URL (default: http://localhost:8080).
#                      HTTPS is strongly preferred; plain HTTP to a non-local
#                      host is refused unless ALLOW_INSECURE=1 is set.
#   USER_COUNT         Number of users to create (default: 10).
#   DEFAULT_PASSWORD   Password applied to each created user (default: Password1!).
#   EMAIL_DOMAIN       Email domain for each user (default: example.com).
#   AFFILIATION        User affiliation field (default: RSpace).
#   ROLE               User role (default: ROLE_USER).
#   ALLOW_INSECURE     Set to 1 to permit plain HTTP to a non-local host.

set -euo pipefail

# shellcheck source=lib.sh
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

# --- config ---------------------------------------------------------------
RSPACE_BASE_URL="${RSPACE_BASE_URL:-http://localhost:8080}"
USER_COUNT="${USER_COUNT:-10}"
DEFAULT_PASSWORD="${DEFAULT_PASSWORD:-Password1!}"
EMAIL_DOMAIN="${EMAIL_DOMAIN:-example.com}"
AFFILIATION="${AFFILIATION:-RSpace}"
ROLE="${ROLE:-ROLE_USER}"

print_usage() {
  cat <<EOF
Usage:
  prefix='foo' [RSPACE_BASE_URL='https://host:port'] $0

The sysadmin apiKey is requested via a silent interactive prompt at runtime
and is held only in a mode-600 temp file (never on curl's command line).

Required environment variables:
  prefix    Username prefix; users '<prefix>1' .. '<prefix>\${USER_COUNT}' are created.

Optional environment variables:
  RSPACE_BASE_URL    RSpace server base URL (default: http://localhost:8080).
                     HTTPS is strongly preferred; plain HTTP to a non-local
                     host is refused unless ALLOW_INSECURE=1 is set.
  USER_COUNT         Number of users to create (default: 10).
  DEFAULT_PASSWORD   Password applied to each created user (default: Password1!).
  EMAIL_DOMAIN       Email domain for each user (default: example.com).
  AFFILIATION        User affiliation field (default: RSpace).
  ROLE               User role (default: ROLE_USER).
  ALLOW_INSECURE     Set to 1 to permit plain HTTP to a non-local host.
EOF
}

# --- env-var config -------------------------------------------------------
PREFIX="${prefix:-}"

missing=()
[[ -z "${PREFIX// /}" ]] && missing+=("prefix")

if (( ${#missing[@]} > 0 )); then
  printf 'Error: missing required environment variable(s): %s\n\n' "${missing[*]}" >&2
  print_usage >&2
  exit 2
fi

rspace_assert_url_safe "${RSPACE_BASE_URL}" || exit 2

# --- prompt for apiKey (silent; written to a mode-600 temp file) ---------
if [[ ! -t 0 ]]; then
  echo "Error: stdin is not a TTY; apiKey must be entered interactively." >&2
  exit 2
fi
read -r -s -p "Enter sysadmin apiKey: " RSPACE_API_KEY
echo
if [[ -z "${RSPACE_API_KEY// /}" ]]; then
  echo "Error: apiKey must be non-empty" >&2
  exit 2
fi
AUTH_HEADER_FILE="$(rspace_write_auth_header_file "${RSPACE_API_KEY}")"
TMP_BODY="$(mktemp -t rspace-create-user.XXXXXX)"
TMP_REQ="$(mktemp -t rspace-create-user-req.XXXXXX)"
trap 'rm -f "${AUTH_HEADER_FILE}" "${TMP_BODY}" "${TMP_REQ}"' EXIT
unset RSPACE_API_KEY

# --- JSON escaping --------------------------------------------------------
# Escape a value for safe embedding inside a JSON string literal: escape
# backslash, double-quote, control characters, tab, CR, LF. This prevents an
# operator-supplied prefix / password / affiliation containing " or \ from
# producing malformed JSON or injecting fields like "role":"ROLE_SYSADMIN".
json_escape() {
  python3 -c '
import json, sys
sys.stdout.write(json.dumps(sys.argv[1])[1:-1])
' "$1"
}

echo "Creating ${USER_COUNT} users '${PREFIX}1' .. '${PREFIX}${USER_COUNT}' at ${RSPACE_BASE_URL}"

# --- create users ---------------------------------------------------------
created=0
failed=0
esc_prefix="$(json_escape "${PREFIX}")"
esc_password="$(json_escape "${DEFAULT_PASSWORD}")"
esc_email_domain="$(json_escape "${EMAIL_DOMAIN}")"
esc_role="$(json_escape "${ROLE}")"
esc_affiliation="$(json_escape "${AFFILIATION}")"

for i in $(seq 1 "${USER_COUNT}"); do
  username="${PREFIX}${i}"
  esc_username="$(json_escape "${username}")"

  cat > "${TMP_REQ}" <<JSON
{
  "username": "${esc_username}",
  "password": "${esc_password}",
  "email": "${esc_username}@${esc_email_domain}",
  "firstName": "${esc_prefix}",
  "lastName": "${i}",
  "role": "${esc_role}",
  "affiliation": "${esc_affiliation}"
}
JSON

  : > "${TMP_BODY}"
  http_status=$(curl -sS -o "${TMP_BODY}" -w "%{http_code}" \
    --connect-timeout 10 --max-time 30 \
    -X POST \
    -H @"${AUTH_HEADER_FILE}" \
    -H "Content-Type: application/json" \
    --data-binary @"${TMP_REQ}" \
    "${RSPACE_BASE_URL}/api/v1/sysadmin/users") || http_status="000"

  if [[ "${http_status}" == "201" ]]; then
    echo "  [${i}/${USER_COUNT}] ${username} -> 201 Created"
    created=$((created + 1))
  else
    echo "  [${i}/${USER_COUNT}] ${username} -> ${http_status}"
    sed 's/^/      /' "${TMP_BODY}"
    echo
    failed=$((failed + 1))
  fi
done

echo
echo "Done. Created: ${created}, failed: ${failed}."
[[ "${failed}" -eq 0 ]]
