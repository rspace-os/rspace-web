#!/usr/bin/env bash
# Compares the Gradle build's resolved classpaths with Maven's, printing modules (with
# versions) that only one build resolves.
#
# Usage: scripts/gradle/compare-classpaths.sh [--check] [--offline]
#   --check    exit non-zero if a module resolves at a different version in the two builds, or
#              is on only one build's classpath without an entry in
#              gradle/known-classpath-differences.txt
#   --offline  run Maven and Gradle offline (needs warm local caches)
set -euo pipefail
cd "$(dirname "$0")/../.."
check=false
mvn_args=(-B -q)
gradle_args=(-q)
for arg in "$@"; do
  case $arg in
    --check) check=true ;;
    --offline) mvn_args+=(-o); gradle_args+=(--offline) ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done
allowlist=gradle/known-classpath-differences.txt
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
./mvnw "${mvn_args[@]}" dependency:list -DoutputFile="$tmp/mvn.txt" -Dsort=true
grep -E '^\s+\S+:\S+' "$tmp/mvn.txt" | sed -E 's/^\s+//; s/ -- .*//' > "$tmp/deps"
coords() { awk -F: "$1"' { print $1 ":" $2 ":" $(NF == 6 ? 5 : 4) }' "$tmp/deps" | LC_ALL=C sort -u; }
coords '$NF == "compile" || $NF == "provided"' > "$tmp/compileClasspath"
coords '$NF == "compile" || $NF == "runtime"' > "$tmp/runtimeClasspath"
coords '1' > "$tmp/testCompileClasspath"
cp "$tmp/testCompileClasspath" "$tmp/testRuntimeClasspath"
./gradlew "${gradle_args[@]}" dumpClasspaths
: > "$tmp/diffs"
for cp in compileClasspath runtimeClasspath testCompileClasspath testRuntimeClasspath; do
  echo "== $cp"
  LC_ALL=C sort -u "build/classpaths/$cp.txt" > "$tmp/gradle-$cp"
  LC_ALL=C comm -23 "$tmp/$cp" "$tmp/gradle-$cp" > "$tmp/maven-only"
  LC_ALL=C comm -13 "$tmp/$cp" "$tmp/gradle-$cp" > "$tmp/gradle-only"
  echo "   Maven only:  $(tr '\n' ' ' < "$tmp/maven-only")"
  echo "   Gradle only: $(tr '\n' ' ' < "$tmp/gradle-only")"
  sed "s/^/$cp maven-only /" "$tmp/maven-only" >> "$tmp/diffs"
  sed "s/^/$cp gradle-only /" "$tmp/gradle-only" >> "$tmp/diffs"
done
$check || exit 0

# Each diffs line is "<classpath> <side> group:artifact:version". A module on both sides of one
# classpath resolved at different versions; anything else must match an allowlist entry
# "<classpath|*> <side> group:artifact".
if ! awk '
  FNR == NR {
    sub(/#.*/, "")
    if (NF == 3) { allowed[$1 " " $2 " " $3] = 1 }
    else if (NF) { printf "%s:%d: expected \"<classpath|*> <maven-only|gradle-only> group:artifact\"\n", FILENAME, FNR; bad = 1 }
    next
  }
  {
    split($3, c, ":"); ga = c[1] ":" c[2]
    version[$1 " " ga " " $2] = c[3]
    sides[$1 " " ga]++
    if (!(($1 " " $2 " " ga) in allowed) && !(("* " $2 " " ga) in allowed)) { unlisted[$1 " " $2 " " ga] = $3 }
    used[$1 " " $2 " " ga] = used["* " $2 " " ga] = 1
  }
  END {
    for (k in sides) if (sides[k] > 1) { split(k, p, " "); printf "version mismatch in %s: %s (Maven %s, Gradle %s)\n", p[1], p[2], version[k " maven-only"], version[k " gradle-only"]; bad = 1 }
    for (k in unlisted) { split(k, p, " "); if (sides[p[1] " " p[3]] == 1) { printf "unexpected %s module in %s: %s\n", p[2], p[1], unlisted[k]; bad = 1 } }
    for (k in allowed) if (!(k in used)) printf "note: allowlist entry \"%s\" no longer matches a difference; consider removing it\n", k
    exit bad
  }' "$allowlist" "$tmp/diffs" | LC_ALL=C sort > "$tmp/report"; then
  cat "$tmp/report"
  cat <<EOF

The Gradle build no longer resolves the same classpaths as Maven (pom.xml is the source of truth).
- After a version or scope change in pom.xml, run scripts/gradle/export-maven-versions.sh and
  commit gradle/maven-resolved-versions.txt.
- After adding, removing or changing exclusions on a dependency in pom.xml, make the same change
  in build.gradle's dependencies block (scripts/gradle/pom2gradle.py generates it from the
  effective POM), then rerun scripts/gradle/export-maven-versions.sh.
- If the difference is intended, add it to $allowlist with a reason.
Rerun scripts/gradle/compare-classpaths.sh --check to confirm.
EOF
  exit 1
fi
cat "$tmp/report"
echo "Classpaths match Maven, apart from the differences listed in $allowlist."
