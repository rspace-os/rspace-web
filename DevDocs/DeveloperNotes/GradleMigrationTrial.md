# Gradle migration trial

A trial Gradle build (`build.gradle`, Groovy DSL) sits beside `pom.xml` to measure what a move
from Maven would gain. **Maven is still the supported build**: CI, Jetty, Liquibase, and
release packaging all still run through Maven.

## Using it

The wrapper downloads Gradle 9.8.2 on first use. Run it with Java 17 or 21.

| Gradle | Maven equivalent |
|---|---|
| `./gradlew testClasses` | `mvn test-compile` |
| `./gradlew test -Dfast=true` | `mvn test -Dfast=true` |
| `./gradlew test --tests '*MyClassTest'` | `mvn test -Dtest=MyClassTest` |
| `./gradlew integrationTest` (plus `integrationTestRun`, `integrationTestProblematic`) | Surefire `integration-tests*` executions |
| `./gradlew war` | `mvn package -DskipTests` |
| `./gradlew war -PgenerateReactDist` | `mvn package -DgenerateReactDist -DskipTests` |
| `./gradlew spotlessApply` / `spotlessCheck` | `spotless:apply` (Maven runs it on every compile) |
| `-Pdeployment=prod`, `-Ptimestamp=now` | `-Ddeployment=prod`, build timestamp |
| `-PmaxTestForks=6` | Surefire `forkCount` |

## How it matches Maven

- **Dependencies.** `scripts/gradle/pom2gradle.py` generated the declarations in `build.gradle`
  from the effective POM, exclusions included. `gradle/maven-resolved-versions.txt` records every
  module version and scope that `mvn dependency:list` resolves. The build forces those
  versions, and excludes modules from the classpaths Maven keeps them off. Regenerate the file
  with `scripts/gradle/export-maven-versions.sh` after changing `pom.xml`, then check with
  `scripts/gradle/compare-classpaths.sh`.
- **Resource filtering.** `buildSrc` holds `MavenPlaceholderFilter`, a Java filter that replaces
  `${name}` and `@name@` only for known properties (`gradle/resource-filter.properties`). It
  keeps line endings intact; Gradle's built-in line filter turned the lone CRs in
  `usersShort.csv` into LFs.
- **Tests.** Gradle uses Surefire's include patterns, the `chemistry` tag exclusion, heap size,
  and `--add-opens` flags, and forwards `-Dfast` and `-Ddeployment` to the test JVM.

Verified on 2026-11-03:

- **Classpaths.** Main compile and runtime classpaths are identical to Maven's. Test classpaths
  differ only in JUnit's compile-only annotation jars (`apiguardian`, `jspecify`),
  `objenesis` (test compile only), and the `junit-platform-launcher` that Gradle needs.
- **Resources.** All 312 main resources match byte for byte, apart from the version stamp in
  `rs.properties`. All 250 test resources match too, apart from 11 binaries that Maven corrupts
  and Gradle copies intact (see findings).
- **WAR.** Every class file and library in the WAR is byte-identical. The differences are Maven's
  `META-INF/maven` metadata, three empty `package-info.class` files maven-compiler-plugin
  generates, the manifest, `rs.properties`, and okio appearing as `okio-jvm-2.4.1.jar` (same
  bytes).
- **Unit tests.** `-Dfast=true` runs the same classes and executes 4,765 tests under Gradle
  against 4,756 under Maven. The gap is Surefire under-reporting `@Nested` tests in
  `DocConverterConfigTest` and `SecurityRealmConfigTest`: its XML lists every case but counts
  fewer. All pass. Surefire also reports classes skipped by `-Dfast` as 0 tests, where Gradle
  counts each method as skipped.

The only source change needed was `EhcacheRegionConfigTest`, which identified test resources by
Maven's `test-classes` path.

## Not migrated

Jetty (`jetty-http`, `jetty-ssl`), the Liquibase and SQL database profiles, `prodRelease`,
Storybook, JaCoCo, Sonar, OWASP, the toolchains plugin, and the enforcer's dependency
convergence check (version pinning covers convergence for now).

## Benchmark

`scripts/gradle/benchmark.sh [reps] [filter]` writes `build-benchmarks/results.csv`. The table
gives medians of 3 runs, in seconds. Maven and Gradle runs were interleaved within each rep, both
offline with warm dependency caches, and Gradle used a warm daemon unless noted.

- **Maven (main)** is the first run, on `main` as of 2026-11-03.
- **Maven with #1350** is the rerun with [#1350](https://github.com/rspace-os/rspace-web/pull/1350)
  applied, which stops Maven recompiling every test source on each build (see below). Gradle was
  rerun alongside it.
- Rows marked "first run" were not repeated, because #1350 cannot affect them.

Environment: 12 cores, 30 GB RAM, OpenJDK 17.0.20, Maven 3.9.12, Gradle 9.8.2. The machine was
shared with other worktrees' Docker stacks and builds (load average 5 to 7 in the first run, 9 to
15 in the rerun), so treat differences under about 15% as noise. Skipping Spotless and the
enforcer made no measurable difference to Maven.

| Scenario (compile = main + test classes) | Maven (main) | Maven with #1350 | Gradle | Gradle vs Maven with #1350 |
|---|---:|---:|---:|---:|
| Clean compile | 65.8 | 60.7 | 41.9 | 1.4x |
| Clean compile, cold Gradle daemon (first run) | | | 64.4 | none |
| Clean compile, from Gradle build cache (first run) | | | 2.2 | 28x |
| No-op (nothing changed) | 38.9 | 10.0 | 1.0 | 10x |
| Private change in a leaf class (`UserExternalIdResolverImpl`) | 59.9 | 64.8 | 2.8 | 23x |
| Public API change in `User` (imported by 1,540 files) | 62.9 | 63.9 | 33.1 | 1.9x |
| Change in one test class | 39.7 | 40.2 | 2.6 | 15x |
| Change in one resource (`ehcache.xml`) | 42.9 | 8.3 | 1.0 | 8x |
| WAR from compiled classes (`package -DskipTests` / `war`) | 51.2 | 15.1 | 11.1 | 1.4x |
| WAR, nothing changed | | | 1.3 | |
| Unit tests, 1 fork (`surefire:test` / `test --rerun`) | 165.0 | 130.2 | 132.5 | none |
| Unit tests, 4 forks (what `-DforkCount=1C` gives on CI) | | 98.3 | 68.0 | 1.4x |
| Unit tests, nothing changed | (always reruns) | (always reruns) | 1.2 | |

#1350 removes Maven's fixed 30 s test recompile from every build that doesn't change a Java
source: no-op builds, resource changes, and packaging. Builds that change any Java source still
recompile the whole affected module in Maven, so those rows don't move. The single-fork test rows
were measured hours apart under different load, so they aren't comparable across columns.

With 4 forks, both tools did similar test work: Maven's per-class times summed to about 300 s,
Gradle's to 324 s. About 10 s of Maven's extra wall time is its fixed startup, the same 10 s its
no-op build takes. The rest of the gap is unexplained.

### What drives the differences

- **Incremental compilation is the big win.** maven-compiler-plugin recompiles the whole
  module, all 2,467 main or 1,350 test files, whenever any source in it changes. Gradle
  recompiles only classes affected by the change. It tracks class ABIs, so a private or
  method-body edit does not recompile dependents. A public change to `User`, which most of the
  codebase imports, still takes about half Maven's time.
- **Up-to-date checks and caching.** Gradle skips tasks whose inputs are unchanged, including
  `test`, so rerunning tests on an unchanged tree takes about 1 s. Maven reruns every phase. The
  build cache restores a clean build in about 2 s after a branch switch or `clean`. A shared
  remote cache would extend that to CI.
- **Clean builds and single-fork test runs are close.** javac and JUnit do the same work in both.
  The clean-compile gain comes from the warm daemon: with a cold daemon, as on CI, Gradle matches
  Maven. Both tools speed up with parallel test forks.

### Maven problems found along the way

These are worth fixing whether or not the build moves:

1. **Maven recompiles every test source on every build.** Three test files trip
   maven-compiler-plugin's stale-source check, so every `mvn test-compile` reports "Changes
   detected" and recompiles all 1,350 test files:
   - `SequenceRunnable.java` declares `SequencedRunnable`.
   - `BaseSampleValidationTestBase.java` declares `InventoryRecordValidationTestBase`.
   - `OldTemplateInGalleryFolderSetup.java` is entirely commented out.

   [#1350](https://github.com/rspace-os/rspace-web/pull/1350) renames the first two and deletes
   the third, cutting Maven's no-op build from 38.9 s to 10.0 s. It also sets the GitHub fast-test
   job to `-DforkCount=1C`.
2. **Maven resource filtering corrupts binary test resources.** Filtering covers every
   extension not in `nonFilteredFileExtensions`, so `.docx`, `.xlsx`, `.odt`, `.ods`, `.odp`,
   `.cdx`, `.mp4`, and `.flv` files reach `target/test-classes` altered. Adding them to the
   list fixes it.
3. **The build timestamp in the version changes `rs.properties` on every build.** That forces
   resources, and anything packaging them, to be redone each time. The Gradle build uses a
   fixed `dev` stamp unless given `-Ptimestamp`.

## If the trial goes further

- Port the Jetty, Liquibase, and database-reset profiles, and update `Jenkinsfile` and the
  GitHub workflows.
- Move the dependency declarations into a version catalog (`gradle/libs.versions.toml`). Once
  Gradle owns resolution, replace the Maven version pin file with dependency locking.
- Configure a remote build cache for CI.
