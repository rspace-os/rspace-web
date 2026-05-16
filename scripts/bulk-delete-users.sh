#!/usr/bin/env bash
#
# Bulk-delete RSpace users by ID via the sysadmin API.
#
# AI and Third-Party Code policy reminder: this script was generated with AI
# assistance. Review it under the policy in Drata before running against any
# environment you care about.
#
# Pre-flight (operator workflow):
#   Run THIS script ONLY AFTER scripts/delete-groups.sh, so that users are
#   no longer constrained by sole-PI / group-owner rules.
#
#   For temp users (isTempUsers=true):
#     mysql -BN -h <host> -u <user> -p <db> \
#         < scripts/find_temp_users_created_more_than_one_year_ago.sql \
#         > inactive_temp_user_ids.txt
#   For non-temp users (isTempUsers=false):
#     mysql -BN -h <host> -u <user> -p <db> \
#         < scripts/find_users_having_lastlogin_more_than_one_year_ago.sql \
#         > inactive_user_ids.txt
#   The -BN flags strip the column header so each line is just an ID.
#
# Retry only failed IDs from a previous run:
#   awk -F'\t' 'NR>1 && $1 ~ /^[0-9]+$/ {print $1}' \
#     <output-dir>/failed_to_delete.txt > retry.txt
#   inputFile=retry.txt isTempUsers=<true|false> ./bulk-delete-users.sh
#
# Required environment variables:
#   inputFile      Path to a file containing one user ID per line.
#   isTempUsers    "true" (case-insensitive) selects the temp-user-only endpoint
#                  DELETE /api/v1/sysadmin/users/temp/{id}.
#                  Any other value selects DELETE /api/v1/sysadmin/users/{id}.
#
# Optional environment variables:
#   RSPACE_BASE_URL     RSpace server base URL (default http://localhost:8080).
#                       HTTPS is strongly preferred; plain HTTP to a non-local
#                       host is refused unless ALLOW_INSECURE=1 is set.
#   DELETION_CONFIRMED  Set to 1 to skip the interactive confirmation.
#   ALLOW_INSECURE      Set to 1 to permit plain HTTP to a non-local host
#                       (development only).

set -euo pipefail

# shellcheck source=lib.sh
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

RSPACE_BASE_URL="${RSPACE_BASE_URL:-http://localhost:8080}"

print_usage() {
  cat <<EOF
Usage:
  inputFile='path/to/user-ids.txt' isTempUsers=true \\
    [RSPACE_BASE_URL='https://host:port'] $0

The sysadmin apiKey is requested via a silent interactive prompt at runtime
and is held only in a mode-600 temp file (never on curl's command line).

Required environment variables:
  inputFile     Path to a file containing one user ID per line.
  isTempUsers   "true" (case-insensitive) to call the temp-user-only endpoint
                (DELETE /api/v1/sysadmin/users/temp/{id}); any other value
                calls DELETE /api/v1/sysadmin/users/{id}.

Optional environment variables:
  RSPACE_BASE_URL     RSpace server base URL (default: http://localhost:8080).
                      HTTPS is strongly preferred; plain HTTP to a non-local
                      host is refused unless ALLOW_INSECURE=1 is set.
  DELETION_CONFIRMED  Set to 1 to skip the interactive confirmation.
  ALLOW_INSECURE      Set to 1 to permit plain HTTP to a non-local host.

Output:
  successful_delete.txt and failed_to_delete.txt are written to the same
  directory as inputFile. Both files are appended to (never overwritten)
  and each run starts with a header line stating the date and mode.
EOF
}

# --- env-var config -------------------------------------------------------
INPUT_FILE="${inputFile:-}"
IS_TEMP_USERS_ARG="${isTempUsers:-}"

missing=()
[[ -z "${INPUT_FILE// /}" ]] && missing+=("inputFile")
[[ -z "${IS_TEMP_USERS_ARG// /}" ]] && missing+=("isTempUsers")

if (( ${#missing[@]} > 0 )); then
  printf 'Error: missing required environment variable(s): %s\n\n' "${missing[*]}" >&2
  print_usage >&2
  exit 2
fi
if [[ ! -f "${INPUT_FILE}" ]]; then
  echo "Error: input file not found: ${INPUT_FILE}" >&2
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
TMP_BODY="$(mktemp -t rspace-delete-body.XXXXXX)"
trap 'rm -f "${AUTH_HEADER_FILE}" "${TMP_BODY}"' EXIT
unset RSPACE_API_KEY

# Normalise isTempUsers: only the literal string "true" (case-insensitive)
# selects the temp endpoint; everything else falls through to the any-user
# endpoint.
is_temp_lower="$(printf '%s' "${IS_TEMP_USERS_ARG}" | tr '[:upper:]' '[:lower:]')"
if [[ "${is_temp_lower}" == "true" ]]; then
  ENDPOINT_PATH="/api/v1/sysadmin/users/temp"
  DELETE_MODE="temp users"
else
  ENDPOINT_PATH="/api/v1/sysadmin/users"
  DELETE_MODE="any users"
fi

# --- collect IDs (skip blanks; keep order) --------------------------------
RAW_IDS=()
while IFS= read -r _line || [[ -n "${_line}" ]]; do
  [[ -z "${_line//[[:space:]]/}" ]] && continue
  RAW_IDS+=("${_line}")
done < "${INPUT_FILE}"

if [[ "${#RAW_IDS[@]}" -eq 0 ]]; then
  echo "Input file has no non-blank lines; nothing to do." >&2
  exit 0
fi

# --- plan + confirmation --------------------------------------------------
total="${#RAW_IDS[@]}"
first_id="${RAW_IDS[0]}"
last_id="${RAW_IDS[$((total - 1))]}"

echo "About to delete ${total} user(s) from ${RSPACE_BASE_URL}"
echo "  input file : ${INPUT_FILE}"
echo "  mode       : ${DELETE_MODE}"
echo "  endpoint   : DELETE ${ENDPOINT_PATH}/{id}"
echo "  first ID   : ${first_id}"
echo "  last ID    : ${last_id}"

if [[ "${DELETION_CONFIRMED:-0}" != "1" ]]; then
  read -r -p "Type DELETE to proceed: " confirm
  if [[ "${confirm}" != "DELETE" ]]; then
    echo "Aborted." >&2
    exit 1
  fi
fi

# --- output files ---------------------------------------------------------
output_dir="$(dirname "${INPUT_FILE}")"
SUCCESS_FILE="${output_dir}/successful_delete.txt"
FAILED_FILE="${output_dir}/failed_to_delete.txt"

run_header="# $(date -u +%Y-%m-%dT%H:%M:%SZ) deletion run (mode: ${DELETE_MODE})"
printf '%s\n' "${run_header}" >> "${SUCCESS_FILE}"
printf '%s\n' "${run_header}" >> "${FAILED_FILE}"

# --- helpers --------------------------------------------------------------
# Encode arbitrary bytes onto a single TSV line: escape \, tab, CR, LF.
escape_for_tsv() {
  sed -e 's/\\/\\\\/g' -e $'s/\t/\\\\t/g' -e $'s/\r/\\\\r/g' \
    | awk 'BEGIN{ORS=""} {if (NR>1) printf "\\n"; print}'
}

record_failure() {
  local user_id="$1"
  local detail
  detail=$(printf '%s' "$2" | escape_for_tsv)
  printf '%s\t%s\n' "${user_id}" "${detail}" >> "${FAILED_FILE}"
}

# --- main loop ------------------------------------------------------------
succeeded=0
failed=0

for raw in "${RAW_IDS[@]}"; do
  user_id="${raw#"${raw%%[![:space:]]*}"}"
  user_id="${user_id%"${user_id##*[![:space:]]}"}"

  if ! [[ "${user_id}" =~ ^[0-9]+$ ]]; then
    echo "  [skip] non-numeric ID: '${user_id}'"
    record_failure "${user_id}" "non-numeric user ID in input file"
    failed=$((failed + 1))
    continue
  fi

  http_status="$(rspace_curl "${TMP_BODY}" "${AUTH_HEADER_FILE}" DELETE \
    "${RSPACE_BASE_URL}${ENDPOINT_PATH}/${user_id}")"

  if [[ "${http_status}" == "204" ]]; then
    echo "  [ok ] ${user_id} -> 204"
    printf '%s\n' "${user_id}" >> "${SUCCESS_FILE}"
    succeeded=$((succeeded + 1))
  else
    body=$(cat "${TMP_BODY}" 2>/dev/null || true)
    echo "  [err] ${user_id} -> ${http_status}"
    record_failure "${user_id}" "HTTP ${http_status} ${body}"
    failed=$((failed + 1))
  fi
done

echo
echo "Done. Succeeded: ${succeeded}, failed: ${failed}."
echo "  success log: ${SUCCESS_FILE}"
echo "  failure log: ${FAILED_FILE}"

[[ "${failed}" -eq 0 ]]
