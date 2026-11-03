#!/usr/bin/env bash
# Regenerates gradle/maven-resolved-versions.txt from `mvn dependency:list`, so the trial
# Gradle build keeps resolving the same versions and scopes as pom.xml.
# With --check, exits non-zero instead if the committed file differs from what pom.xml gives.
set -euo pipefail
cd "$(dirname "$0")/../.."
check=false
mvn_args=(-B -q)
for arg in "$@"; do
  case $arg in
    --check) check=true ;;
    --offline) mvn_args+=(-o) ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done
target=gradle/maven-resolved-versions.txt
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
./mvnw "${mvn_args[@]}" dependency:list -DoutputFile="$tmp/mvn.txt" -Dsort=true
{
  echo "# Module versions and scopes Maven resolves for pom.xml (mvn dependency:list), so Gradle"
  echo "# picks the same artifacts as Maven's nearest-wins mediation. Format: group:artifact=version scope"
  echo "# Regenerate with scripts/gradle/export-maven-versions.sh after changing pom.xml."
  grep -E '^\s+\S+:\S+' "$tmp/mvn.txt" | sed -E 's/^\s+//; s/ -- .*//' \
    | awk -F: '{ print $1 ":" $2 "=" $(NF == 6 ? 5 : 4) " " $NF }' | LC_ALL=C sort -u
} > "$tmp/versions"
if $check; then
  if ! diff -u --label "$target (committed)" --label "$target (from pom.xml)" "$target" "$tmp/versions"; then
    echo
    echo "$target is out of date with pom.xml. Run scripts/gradle/export-maven-versions.sh and commit the result."
    exit 1
  fi
  echo "$target matches pom.xml."
else
  cp "$tmp/versions" "$target"
  echo "wrote $target"
fi
