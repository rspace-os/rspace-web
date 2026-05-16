#!/usr/bin/env bash
#
# Create 10 RSpace users named <prefix>1 .. <prefix>10 via the sysadmin API.
#
# AI and Third-Party Code policy reminder: this script was generated with AI
# assistance. Review it under the policy in Drata before running against any
# environment you care about.
#
# Usage:
#   prefix='foo' [RSPACE_BASE_URL='http://host:port'] ./create-test-users.sh
#
# The sysadmin apiKey is requested via a silent interactive prompt at runtime
# so it never lands in shell history or environment.

set -euo pipefail

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
  prefix='foo' [RSPACE_BASE_URL='http://host:port'] $0

The sysadmin apiKey is requested via a silent interactive prompt at runtime
so it never lands in shell history or environment.

Required environment variables:
  prefix    Username prefix; users '<prefix>1' .. '<prefix>\${USER_COUNT}' are created.

Optional environment variables:
  RSPACE_BASE_URL    RSpace server base URL (default: http://localhost:8080).
  USER_COUNT         Number of users to create (default: 10).
  DEFAULT_PASSWORD   Password applied to each created user (default: Password1!).
  EMAIL_DOMAIN       Email domain for each user (default: example.com).
  AFFILIATION        User affiliation field (default: RSpace).
  ROLE               User role (default: ROLE_USER).
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

# --- prompt for apiKey (silent; never stored) -----------------------------
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

echo "Creating ${USER_COUNT} users '${PREFIX}1' .. '${PREFIX}${USER_COUNT}' at ${RSPACE_BASE_URL}"

# --- create users ---------------------------------------------------------
created=0
failed=0
for i in $(seq 1 "${USER_COUNT}"); do
  username="${PREFIX}${i}"
  body=$(cat <<EOF
{
  "username": "${username}",
  "password": "${DEFAULT_PASSWORD}",
  "email": "${username}@${EMAIL_DOMAIN}",
  "firstName": "${PREFIX}",
  "lastName": "${i}",
  "role": "${ROLE}",
  "affiliation": "${AFFILIATION}"
}
EOF
)

  http_status=$(curl -sS -o /tmp/rspace_create_user.$$ -w "%{http_code}" \
    -X POST \
    -H "apiKey: ${RSPACE_API_KEY}" \
    -H "Content-Type: application/json" \
    --data "${body}" \
    "${RSPACE_BASE_URL}/api/v1/sysadmin/users") || http_status="000"

  if [[ "${http_status}" == "201" ]]; then
    echo "  [${i}/${USER_COUNT}] ${username} -> 201 Created"
    created=$((created + 1))
  else
    echo "  [${i}/${USER_COUNT}] ${username} -> ${http_status}"
    sed 's/^/      /' /tmp/rspace_create_user.$$
    echo
    failed=$((failed + 1))
  fi
done
rm -f /tmp/rspace_create_user.$$

echo
echo "Done. Created: ${created}, failed: ${failed}."
[[ "${failed}" -eq 0 ]]
