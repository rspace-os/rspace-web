#!/usr/bin/env bash
#
# Bulk-delete RSpace groups by ID via the sysadmin API.
#
# AI and Third-Party Code policy reminder: this script was generated with AI
# assistance. Review it under the policy in Drata before running against any
# environment you care about.

set -euo pipefail

RSPACE_BASE_URL="${RSPACE_BASE_URL:-http://localhost:8080}"
ENDPOINT_PATH="/api/v1/sysadmin/groups/lastloginExceedsOneYear"

print_usage() {
  cat <<EOF
Usage:
  inputFile='path/to/group-ids.txt' \\
    [RSPACE_BASE_URL='http://host:port'] $0

The sysadmin apiKey is requested via a silent interactive prompt at runtime
so it never lands in shell history or environment.

Required environment variables:
  inputFile    Path to a file containing one group ID per line.

Optional environment variables:
  RSPACE_BASE_URL     RSpace server base URL (default: http://localhost:8080).
  DELETION_CONFIRMED  Set to 1 to skip the interactive confirmation.

The script calls DELETE /api/v1/sysadmin/groups/{id} for each ID. The
endpoint deletes any group type (LAB_GROUP, PROJECT_GROUP,
COLLABORATION_GROUP, self-service lab group).

Output:
  successful_group_delete.txt and failed_group_delete.txt are written to the
  same directory as inputFile. Both files are appended to (never overwritten)
  and each run starts with a header line stating the date and mode.
EOF
}

# --- env-var config -------------------------------------------------------
INPUT_FILE="${inputFile:-}"

missing=()
[[ -z "${INPUT_FILE// /}" ]] && missing+=("inputFile")

if (( ${#missing[@]} > 0 )); then
  printf 'Error: missing required environment variable(s): %s\n\n' "${missing[*]}" >&2
  print_usage >&2
  exit 2
fi

if [[ ! -f "${INPUT_FILE}" ]]; then
  echo "Error: input file not found: ${INPUT_FILE}" >&2
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

# --- collect IDs (skip blanks; keep order) --------------------------------
# Portable across bash 3.2 (macOS default) and bash 4+.
RAW_IDS=()
while IFS= read -r _line || [[ -n "${_line}" ]]; do
  case "${_line}" in
    ''|*[![:space:]]*)
      [[ -z "${_line//[[:space:]]/}" ]] && continue
      RAW_IDS+=("${_line}")
      ;;
  esac
done < "${INPUT_FILE}"

if [[ "${#RAW_IDS[@]}" -eq 0 ]]; then
  echo "Input file has no non-blank lines; nothing to do." >&2
  exit 0
fi

# --- plan + confirmation --------------------------------------------------
total="${#RAW_IDS[@]}"
first_id="${RAW_IDS[0]}"
last_id="${RAW_IDS[$((total - 1))]}"

echo "About to delete ${total} group(s) from ${RSPACE_BASE_URL}"
echo "  input file : ${INPUT_FILE}"
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
SUCCESS_FILE="${output_dir}/successful_group_delete.txt"
FAILED_FILE="${output_dir}/failed_group_delete.txt"

run_header="# $(date -u +%Y-%m-%dT%H:%M:%SZ) group deletion run"
printf '%s\n' "${run_header}" >> "${SUCCESS_FILE}"
printf '%s\n' "${run_header}" >> "${FAILED_FILE}"

# --- helpers --------------------------------------------------------------
# Encode arbitrary bytes onto a single TSV line: escape \, tab, CR, LF.
escape_for_tsv() {
  sed -e 's/\\/\\\\/g' -e $'s/\t/\\\\t/g' -e $'s/\r/\\\\r/g' \
    | awk 'BEGIN{ORS=""} {if (NR>1) printf "\\n"; print}'
}

record_success() {
  printf '%s\n' "$1" >> "${SUCCESS_FILE}"
}

record_failure() {
  local group_id="$1"
  local detail
  detail=$(printf '%s' "$2" | escape_for_tsv)
  printf '%s\t%s\n' "${group_id}" "${detail}" >> "${FAILED_FILE}"
}

# --- main loop ------------------------------------------------------------
succeeded=0
failed=0
tmp_body="$(mktemp -t rspace-delete-group.XXXXXX)"
trap 'rm -f "${tmp_body}"' EXIT

for raw in "${RAW_IDS[@]}"; do
  # trim leading/trailing whitespace
  group_id="${raw#"${raw%%[![:space:]]*}"}"
  group_id="${group_id%"${group_id##*[![:space:]]}"}"

  if ! [[ "${group_id}" =~ ^[0-9]+$ ]]; then
    echo "  [skip] non-numeric ID: '${group_id}'"
    record_failure "${group_id}" "non-numeric group ID in input file"
    failed=$((failed + 1))
    continue
  fi

  : > "${tmp_body}"
  http_status=$(curl -sS -o "${tmp_body}" -w "%{http_code}" \
    -X DELETE \
    -H "apiKey: ${RSPACE_API_KEY}" \
    "${RSPACE_BASE_URL}${ENDPOINT_PATH}/${group_id}") || http_status="000"

  if [[ "${http_status}" == "204" ]]; then
    echo "  [ok ] ${group_id} -> 204"
    record_success "${group_id}"
    succeeded=$((succeeded + 1))
  else
    body=$(cat "${tmp_body}" 2>/dev/null || true)
    echo "  [err] ${group_id} -> ${http_status}"
    record_failure "${group_id}" "HTTP ${http_status} ${body}"
    failed=$((failed + 1))
  fi
done

echo
echo "Done. Succeeded: ${succeeded}, failed: ${failed}."
echo "  success log: ${SUCCESS_FILE}"
echo "  failure log: ${FAILED_FILE}"

[[ "${failed}" -eq 0 ]]
