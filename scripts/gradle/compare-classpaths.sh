#!/usr/bin/env bash
# Compares the trial Gradle build's resolved classpaths with Maven's, printing modules (with
# versions) that only one build resolves.
set -euo pipefail
cd "$(dirname "$0")/../.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mvn -B -q -o dependency:list -DoutputFile="$tmp/mvn.txt" -Dsort=true
grep -E '^\s+\S+:\S+' "$tmp/mvn.txt" | sed -E 's/^\s+//; s/ -- .*//' > "$tmp/deps"
coords() { awk -F: "$1"' { print $1 ":" $2 ":" $(NF == 6 ? 5 : 4) }' "$tmp/deps" | LC_ALL=C sort -u; }
coords '$NF == "compile" || $NF == "provided"' > "$tmp/compileClasspath"
coords '$NF == "compile" || $NF == "runtime"' > "$tmp/runtimeClasspath"
coords '1' > "$tmp/testCompileClasspath"
cp "$tmp/testCompileClasspath" "$tmp/testRuntimeClasspath"
./gradlew -q --offline dumpClasspaths
for cp in compileClasspath runtimeClasspath testCompileClasspath testRuntimeClasspath; do
  echo "== $cp"
  LC_ALL=C sort -u "build/classpaths/$cp.txt" > "$tmp/gradle-$cp"
  echo "   Maven only:  $(LC_ALL=C comm -23 "$tmp/$cp" "$tmp/gradle-$cp" | tr '\n' ' ')"
  echo "   Gradle only: $(LC_ALL=C comm -13 "$tmp/$cp" "$tmp/gradle-$cp" | tr '\n' ' ')"
done
