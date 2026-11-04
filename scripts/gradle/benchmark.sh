#!/usr/bin/env bash
# Times Maven against the Gradle build for clean, no-op, and incremental compiles,
# unit tests, and WAR packaging. Usage: scripts/gradle/benchmark.sh [reps] [scenario-filter]
# Set TOOLS to choose compile-benchmark tools (default "maven maven-lean gradle").
# Pass a regex as the second argument to run only matching <tool>-<scenario> names.
# Writes CSV rows (tool,scenario,rep,seconds,exit) to build-benchmarks/results.csv.
set -uo pipefail
cd "$(dirname "$0")/../.."
REPS=${1:-3}
FILTER=${2:-.}
OUT=build-benchmarks
mkdir -p "$OUT"
CSV=$OUT/results.csv
LOG=$OUT/benchmark.log
[ -f "$CSV" ] || echo "tool,scenario,rep,seconds,exit" > "$CSV"

MVN=(mvn -o -B -q)
MVN_LEAN=(mvn -o -B -q -Dspotless.apply.skip=true -Denforcer.skip=true)
GRADLE=(./gradlew --offline -q)

LEAF=src/main/java/com/researchspace/service/impl/UserExternalIdResolverImpl.java
CORE=src/main/java/com/researchspace/model/User.java
TEST=src/test/java/com/researchspace/service/impl/ApiAvailabilityHandlerImplTest.java
RESOURCE=src/main/resources/ehcache.xml

# Appends a Google-Java-Format-clean member before the closing brace, so Maven's
# spotless:apply has nothing to reformat.
add_member() { # file visibility
  python3 -I - "$1" "$2" <<'PY'
import sys, time
path, vis = sys.argv[1], sys.argv[2]
src = open(path).read().rstrip()
assert src.endswith("}")
name = f"benchmarkProbe{time.time_ns()}"
open(path, "w").write(src[:-1] + f"\n  {vis} static int {name}() {{\n    return 1;\n  }}\n}}\n")
PY
}
revert() { git checkout -q -- "$@"; }

time_run() { # tool scenario rep cmd...
  local tool=$1 scenario=$2 rep=$3; shift 3
  echo "### $tool $scenario rep$rep: $*" >> "$LOG"
  local start end rc
  start=$(date +%s.%N)
  "$@" >> "$LOG" 2>&1
  rc=$?
  end=$(date +%s.%N)
  printf '%s,%s,%s,%.2f,%s\n' "$tool" "$scenario" "$rep" "$(echo "$end - $start" | bc)" "$rc" | tee -a "$CSV"
}

want() { [[ "$1" =~ $FILTER ]]; }

cmd_for() { # tool -> incremental build command
  case $1 in
    maven) echo "${MVN[*]} test-compile" ;;
    maven-lean) echo "${MVN_LEAN[*]} test-compile" ;;
    gradle|gradle-cold-daemon) echo "${GRADLE[*]} testClasses" ;;
  esac
}
clean_for() {
  case $1 in
    maven) echo "${MVN[*]} clean test-compile" ;;
    maven-lean) echo "${MVN_LEAN[*]} clean test-compile" ;;
    gradle|gradle-cold-daemon) echo "${GRADLE[*]} clean testClasses --no-build-cache" ;;
    gradle-build-cache) echo "${GRADLE[*]} clean testClasses" ;;
  esac
}

edit_private() { add_member "$1" private; }
edit_public() { add_member "$1" public; }
edit_resource() { echo "<!-- benchmark $(date +%s%N) -->" >> "$1"; }

# Tools alternate within each rep, so background load on a shared machine lands on all of them.
read -r -a COMPILE_TOOLS <<< "${TOOLS:-maven maven-lean gradle}"

for rep in $(seq "$REPS"); do
  for tool in "${COMPILE_TOOLS[@]}" gradle-cold-daemon gradle-build-cache; do
    want "$tool-clean-compile" || continue
    [ $tool = gradle-cold-daemon ] && ./gradlew --stop >> "$LOG" 2>&1
    [ $tool = gradle-build-cache ] && $(cmd_for gradle) >> "$LOG" 2>&1  # populate the cache
    time_run "$tool" clean-compile "$rep" $(clean_for $tool)
  done
done

for rep in $(seq "$REPS"); do
  for tool in "${COMPILE_TOOLS[@]}"; do
    want "$tool-noop" || continue
    $(cmd_for $tool) >> "$LOG" 2>&1
    time_run "$tool" noop "$rep" $(cmd_for $tool)
  done
done

# Incremental: bring each build up to date, apply an edit, time the rebuild, revert.
for scenario in "leaf-private-change edit_private $LEAF" "core-abi-change edit_public $CORE" \
    "test-file-change edit_private $TEST" "resource-change edit_resource $RESOURCE"; do
  read -r name edit target <<< "$scenario"
  for rep in $(seq "$REPS"); do
    for tool in "${COMPILE_TOOLS[@]}"; do
      want "$tool-$name" || continue
      $(cmd_for $tool) >> "$LOG" 2>&1
      $edit "$target"
      time_run "$tool" "$name" "$rep" $(cmd_for $tool)
      revert "$target"
    done
  done
done

# Unit tests (-Dfast=true skips the Spring and database tests). Test execution only: Maven's
# surefire:test and Gradle's `test --rerun` skip compilation, so the tools run the same work.
# FORKS lists the fork counts to time (default "1 4"; 4 is what -DforkCount=1C gives on CI).
"${MVN[@]}" test-compile >> "$LOG" 2>&1
"${GRADLE[@]}" testClasses >> "$LOG" 2>&1
for rep in $(seq "$REPS"); do
  for forks in ${FORKS:-1 4}; do
    want "maven-unit-tests" && time_run maven "unit-tests-${forks}-forks" "$rep" \
      "${MVN[@]}" surefire:test -Dfast=true -DforkCount="$forks" -DreuseForks=true
    want "gradle-unit-tests" && time_run gradle "unit-tests-${forks}-forks" "$rep" \
      "${GRADLE[@]}" test -Dfast=true -PmaxTestForks="$forks" --rerun
  done
done
if want "gradle-unit-tests-up-to-date"; then
  "${GRADLE[@]}" test -Dfast=true >> "$LOG" 2>&1
  for rep in $(seq "$REPS"); do time_run gradle unit-tests-up-to-date "$rep" "${GRADLE[@]}" test -Dfast=true; done
fi

# WAR packaging (no tests, no frontend bundle): from a compiled tree, and with nothing changed
for rep in $(seq "$REPS"); do
  if want "maven-package"; then
    "${MVN[@]}" test-compile >> "$LOG" 2>&1
    time_run maven package "$rep" "${MVN[@]}" package -DskipTests
  fi
  if want "gradle-package"; then
    "${GRADLE[@]}" testClasses >> "$LOG" 2>&1
    rm -f build/libs/*.war
    time_run gradle package "$rep" "${GRADLE[@]}" war
    time_run gradle package-noop "$rep" "${GRADLE[@]}" war
  fi
done
git status --short -- src
