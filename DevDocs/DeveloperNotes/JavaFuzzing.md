# Java fuzzing

RSpace uses test-scoped Jazzer 0.30.0 for the pure Java RSQL parser, booking opening-hours codec, and booking scheduling policy. Ordinary tests replay curated inputs and retain the parser's deterministic 10,000-case loop. Discovery campaigns are manual opt-in runs for developers changing these components.

Use Java 17 and run from the repository root. The commands below were exercised on macOS arm64 with Temurin 17.0.20.1 and Maven Wrapper 3.8.1.

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
./mvnw -B test -Dfast=true -Dtest=RsqlFilterParserFuzzTest
./mvnw -B test -Dfast=true -Dtest=InMemoryCollectionQueryTest
JAZZER_FUZZ=1 ./mvnw -B test -Dfast=true '-Dtest=RsqlFilterParserFuzzTest#parsesOrRejects'
./mvnw -B test -Dfast=true -DenableTestCoverage -Dtest=RsqlFilterParserFuzzTest,InMemoryCollectionQueryTest
```

Select exactly one fuzz method per discovery process. `parsesOrRejects` defaults to 30 seconds, limits parser input to 1,024 UTF-8 bytes, and also constructs valid scalar expressions. The fuzz execution sets `jazzer.max_len=4096`, bounding the campaign's serialized input separately. Unexpected exceptions, errors and assertions fail the test; arbitrary invalid queries may throw `CollectionQueryException` with a non-null declared reason.

The parser campaign is implemented in
[`RsqlFilterParserFuzzTest`](../../src/test/java/com/researchspace/model/collection/RsqlFilterParserFuzzTest.java).

## Booking targets

[`BookingOpeningHoursCodecFuzzTest`](../../src/test/java/com/researchspace/model/booking/BookingOpeningHoursCodecFuzzTest.java)
covers bounded invalid input, canonical JSON, round trips, and independent weekday/time entries.
[`BookingSchedulingPolicyFuzzTest`](../../src/test/java/com/researchspace/booking/service/BookingSchedulingPolicyFuzzTest.java)
covers opening/closing boundaries, minute grids, elapsed maximum duration, buffers, and booking and
maintenance conflicts across fixed UTC, Berlin, Lord Howe, and Kathmandu dates, including DST gaps
and folds. It uses an independent boundary oracle rather than copying the policy's coverage algorithm.

The `elapsedIncrements` campaign covers minute/hour increments, optional start alignment,
elapsed-duration multiples, and item maxima. The `calendarDayIncrements` campaign covers local
calendar-day increments, DST-varying elapsed maxima, and missing local end times. Parameterized
regressions and [`BookingIncrementScenariosTest`](../../src/test/java/com/researchspace/booking/service/BookingIncrementScenariosTest.java)
replay fixed boundaries during ordinary Maven tests. See the scheduling fuzz test for current
generators and assertions rather than treating this guide as a case inventory.

Run replay together with the existing unit tests:

```sh
./mvnw -B test -Dfast=true -Dtest=RsqlFilterParserFuzzTest,BookingOpeningHoursCodecFuzzTest,BookingSchedulingPolicyFuzzTest,BookingOpeningHoursCodecTest,BookingSchedulingSettingsTest,BookingSchedulingPolicyImplTest,BookingIncrementScenariosTest
```

Each booking method defaults to a 30-second campaign. Run each separately:

```sh
JAZZER_FUZZ=1 ./mvnw -B test -Dfast=true '-Dtest=BookingOpeningHoursCodecFuzzTest#decodesOrRejects'
JAZZER_FUZZ=1 ./mvnw -B test -Dfast=true '-Dtest=BookingOpeningHoursCodecFuzzTest#validSchedules'
JAZZER_FUZZ=1 ./mvnw -B test -Dfast=true '-Dtest=BookingSchedulingPolicyFuzzTest#validBookingsAndConflicts'
JAZZER_FUZZ=1 ./mvnw -B test -Dfast=true '-Dtest=BookingSchedulingPolicyFuzzTest#enforcesSingleRuleBoundaries'
JAZZER_FUZZ=1 ./mvnw -B test -Dfast=true '-Dtest=BookingSchedulingPolicyFuzzTest#elapsedIncrements'
JAZZER_FUZZ=1 ./mvnw -B test -Dfast=true '-Dtest=BookingSchedulingPolicyFuzzTest#calendarDayIncrements'
```

## Forks and reports

Surefire's `fuzz-tests` execution includes `**/*FuzzTest.java`, sets `forkCount=1` and `reuseForks=false`, and gives each fuzz class its own JVM. The JVM exits after that class, preventing Jazzer instrumentation from reaching ordinary tests. Literal execution settings preserve this isolation even with CI's `-DforkCount=2 -DreuseForks=true`. Existing group filters, `skipUnitTests`, integration-test executions and JaCoCo `argLine` remain in effect.

Reports are under `target/surefire-reports/fuzz/` with a `-fuzz` suffix. Ordinary reports remain directly under `target/surefire-reports/`. CI publishers use recursive XML globs to collect both. `-Dtest` overrides Surefire include/exclude lists, so the focused-selection profile intersects the requested selection with each execution's class partition. Use `-Dtest` for focused selection; global `-Dsurefire.includes` or `-Dsurefire.excludes` overrides replace the execution selectors. Either partition may legitimately be empty; confirm the `Running` lines and nonzero test counts because an unmatched focused selection can succeed with no tests.

## Inputs and replay

Curated parser seeds live in `src/test/resources/com/researchspace/model/collection/RsqlFilterParserFuzzTestInputs/parsesOrRejects/`. For this single non-null String parameter, these files contain raw UTF-8 text. Keep only small, useful regression inputs there. Jazzer writes coverage inputs to the ignored `.cifuzz-corpus/` directory; these are discovery data, not curated regressions. `JAZZER_COVERAGE=1` additionally replays that generated corpus.

Booking seeds follow the same convention under
`src/test/resources/com/researchspace/model/booking/BookingOpeningHoursCodecFuzzTestInputs/<method>/`
and `src/test/resources/com/researchspace/booking/service/BookingSchedulingPolicyFuzzTestInputs/<method>/`.
Arbitrary-text codec seeds contain raw UTF-8; structured methods consume raw `FuzzedDataProvider`
bytes. Preserve those bytes, including the absence of a trailing newline.

Some curated raw-byte inputs use a `.png` suffix to bypass Maven's UTF-8 resource filtering;
these are fuzz inputs, not images. Preserve them unchanged.

Jazzer saves a failing input in the conventional test-resource directory above. Preserve its bytes and rerun the ordinary test command without `JAZZER_FUZZ`; the normal Maven lifecycle copies the new resource before replay. Fix the defect, replay again, and retain a meaningful regression input.

## JUnit and coverage

Import `org.junit:junit-bom:${junit.jupiter.version}` in dependency management. The inherited property is 6.1.3. Jazzer 0.30.0 declares Jupiter 5.9.0 and Platform 1.9.0. Without alignment the build resolves Commons/Launcher 1.9.0 alongside Jupiter/Engine 6.1.3 and fails discovery. The BOM aligns the exercised modules to 6.1.3.

The tested Java 17/JUnit 6 combination is project verification, not upstream certification of every extension, agent or lifecycle callback. Keep active campaigns separate from the coverage profile. JaCoCo regression replay is checked separately. Linux CI, the full Java workflow and database-backed shards remain acceptance gates; local focused runs do not replace them.
