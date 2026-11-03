#!/usr/bin/env bash
# Regenerates gradle/maven-resolved-versions.txt from `mvn dependency:list`, so the trial
# Gradle build keeps resolving the same versions and scopes as pom.xml.
set -euo pipefail
cd "$(dirname "$0")/../.."
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
mvn -B -q dependency:list -DoutputFile="$tmp" -Dsort=true
{
  echo "# Module versions and scopes Maven resolves for pom.xml (mvn dependency:list), so Gradle"
  echo "# picks the same artifacts as Maven's nearest-wins mediation. Format: group:artifact=version scope"
  echo "# Regenerate with scripts/gradle/export-maven-versions.sh after changing pom.xml."
  grep -E '^\s+\S+:\S+' "$tmp" | sed -E 's/^\s+//; s/ -- .*//' \
    | awk -F: '{ print $1 ":" $2 "=" $(NF == 6 ? 5 : 4) " " $NF }' | LC_ALL=C sort -u
} > gradle/maven-resolved-versions.txt
echo "wrote gradle/maven-resolved-versions.txt"
