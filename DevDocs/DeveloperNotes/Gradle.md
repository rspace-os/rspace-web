# Gradle build

`build.gradle` (Groovy DSL, Gradle 9.8.2 wrapper) builds the same classes, resources, WAR and
test runs as `pom.xml`, with Gradle's incremental compilation, up-to-date checks and build cache.
It runs **alongside Maven, which stays the source of truth**:

- dependencies are declared in `pom.xml` and mirrored into Gradle;
- Jetty, Liquibase, release packaging, Jenkins and most CI jobs still run through Maven.

Use Gradle for the local edit-compile-test loop if you like; nothing requires it.

## Commands

Run Gradle with Java 17 and a UTF-8 locale. A daemon started with `LC_ALL=C` cannot read the
non-ASCII test resource file names, so if one did, run `./gradlew --stop`. Java 21 has not been
tried with this build yet.

| Gradle | Maven equivalent |
|---|---|
| `./gradlew testClasses` | `mvn test-compile` |
| `./gradlew test -Dfast=true` | `mvn test -Dfast=true` |
| `./gradlew test --tests '*MyClassTest'` | `mvn test -Dtest=MyClassTest` |
| `./gradlew integrationTest integrationTestRun integrationTestProblematic` | `mvn integration-test -DskipUnitTests=true` |
| `./gradlew war` | `mvn package -DskipTests` |
| `./gradlew war -PgenerateReactDist` | `mvn package -DgenerateReactDist -DskipTests` |
| `./gradlew spotlessApply` / `spotlessCheck` | `spotless:apply` (Maven runs it on every compile) |
| `-PmaxTestForks=4` | `-DforkCount=4` |

**`-P` versus `-D`:** pass build-time settings as `-P`. These are the switches Maven reads as
profile properties (`environment`, `deployment`, `timestamp`, `jdbc.*`, `RS.devlogLevel`), which
also feed resource filtering. Pass test-JVM settings as `-D`. Surefire forwards every Maven `-D`
to the test JVM; Gradle forwards only the list in `forwardedTestSystemProperties` in
`build.gradle`, so add a property there when tests start reading a new one.

**Version stamp:** local builds stamp the version as `2.28.0-SNAPSHOT-dev`, so resources stay
up to date between builds. Pass `-Ptimestamp=<value>`, or `-Ptimestamp=now`, for a real stamp.

### Database test suites

Set the database up as CI does: start MariaDB, run the "Configure MariaDB" SQL from the
`spring-tests` job in `.github/workflows/lint-and-test.yml`, and seed
`src/main/resources/deployments/dev/deployment.properties` from its `.example`. Then:

```bash
# Spring tests (CI: mvnw clean test -Denvironment=drop-recreate-db ...)
./gradlew clean test -Penvironment=drop-recreate-db -Pjdbc.db.maven=rspace \
  -Pjdbc.url=jdbc:mysql://localhost:3306/rspace -PRS.devlogLevel=INFO \
  -Djavax.xml.accessExternalDTD=all -Dlog4j2.configurationFile=log4j2-dev.xml \
  -DRS_FILE_BASE=$RS_FILE_BASE

# Integration tests (CI: mvnw clean integration-test -DskipUnitTests=true ...)
./gradlew clean integrationTest integrationTestRun integrationTestProblematic --continue \
  <same flags>
```

For a server on another port, add `-Pjdbc.url.maven=jdbc:mysql://localhost:<port>`.

`-Penvironment=drop-recreate-db` makes every test task first run `recreateDatabase`, the
equivalent of the `mysql-clean-liquibase` profile. That task:

- drops and recreates the database;
- loads `rs-dbbaseline-utf8.sql`;
- applies the profile's resource-filter values.

In this mode, test tasks always run and are never cached. Test tasks take turns, because with the
configuration cache on Gradle would otherwise run them in parallel against the same database. The
integration tasks run in Surefire's execution order.

Unlike CI's Maven integration job (`-Dsurefire.rerunFailingTestsCount=1`), Gradle does not rerun
failed tests. A flaky integration test that passes on Maven's rerun fails under Gradle.

## Keeping it in step with Maven

- **Dependencies:**
  - `scripts/gradle/pom2gradle.py` generated `build.gradle`'s dependency declarations, exclusions
    included, from the effective POM.
  - `gradle/maven-resolved-versions.txt` records the version and scope of every module
    `mvn dependency:list` resolves. The build forces those versions, and keeps modules off the
    classpaths Maven keeps them off.
- **After changing a version or scope in `pom.xml`:** run `scripts/gradle/export-maven-versions.sh`
  and commit the regenerated file.
- **After adding or removing a dependency, or changing its exclusions:** make the same change in
  `build.gradle`'s dependencies block, then regenerate the versions file.
- **Checks:**
  - `scripts/gradle/export-maven-versions.sh --check` fails if the versions file is stale.
  - `scripts/gradle/compare-classpaths.sh --check` fails if any classpath differs from Maven's,
    beyond the accepted differences in `gradle/known-classpath-differences.txt`.
  - The `gradle-build` job in `lint-and-test.yml` runs both checks, then compiles, packages the
    WAR and runs the fast unit tests under Gradle. It runs whenever the Java or build files
    change.
- **Resource filtering:** `buildSrc`'s `MavenPlaceholderFilter` replaces `${name}` and `@name@`
  only for known properties (`gradle/resource-filter.properties`), as maven-resources-plugin
  does. Unlike Gradle's line filters, it leaves line endings alone. The binary extensions it
  skips must match `nonFilteredFileExtensions` in `pom.xml`.

## Verified against Maven

Verified on 2026-11-04 against `main`, on MariaDB 11.8 for the database suites.

- **Classpaths:** the main runtime classpath is identical apart from okio's metadata-only
  root jar. The accepted differences are listed, with reasons, in
  `gradle/known-classpath-differences.txt`.
- **Resources:** all main and test resources are byte-identical, apart from the version stamp in
  `rs.properties`.
- **WAR:** every class file and library is byte-identical. The differences are:
  - Maven's `META-INF/maven` metadata;
  - three empty `package-info.class` files maven-compiler-plugin generates;
  - the manifest;
  - `rs.properties`;
  - `okio-3.6.0.jar`, okio's metadata-only root jar, which only Maven ships (both ship
    `okio-jvm-3.6.0.jar`).
- **Tests:** every test case has the same outcome under both builds in the fast unit tests, the
  integration suite (1,211 cases) and the Spring suite (6,291 passing). The exceptions:
  - `PublicDocumentsUtilitiesTest.signsInTheGuestWithoutAPasswordCheck` depends on test order
    and can fail under either build.
  - The `HibernateTest`-based model tests skip unless MariaDB is on `localhost:3306`. They were
    not exercised under either build.
- **End to end:** a WAR built with `./gradlew war -PgenerateReactDist` boots on the Jetty setup
  CI uses (`./mvnw jetty:run-war -Dmaven.war.skip=true`). It gave the same final result as a
  Maven-built WAR for every test in the mocked Chromium, mobile and api projects (609 tests).
  Its `ui/dist` bundle is identical apart from Vite's random asset ids in three source maps.
  `sharingDeleteMove.e2e.ts:54` is flaky under both WARs (3 failures in 16 runs each).
- **Test counts:** they look different only in how they're reported.
  - Surefire's XML undercounts `@Nested` tests in its summary attribute.
  - Surefire reports a class skipped by an assumption as 0 tests; Gradle counts each skipped
    method.
  - Gradle reports `@DisplayName` text as the test name.

## Not ported

Jetty (`jetty-http`, `jetty-ssl`, and the Docker dev stack's `jetty:run`), `prodRelease`,
Storybook, JaCoCo, Sonar, OWASP, the Liquibase diff profile, `mysql-clean-hibernate`,
`mysql-nochange`, the toolchains plugin and the enforcer. Version pinning covers dependency
convergence while Maven resolves the versions.

## Benchmark

`scripts/gradle/benchmark.sh [reps] [filter]` writes `build-benchmarks/results.csv`.

- **Method:** medians of 3 runs, in seconds. Maven and Gradle runs were interleaved within each
  rep, both offline with warm dependency caches, and Gradle used a warm daemon unless noted.
- **Maven column:** Maven includes
  [#1350](https://github.com/rspace-os/rspace-web/pull/1350), which stops it recompiling every
  test source on each build.
- **Environment:** 12 cores, 30 GB RAM, OpenJDK 17.0.20, Maven 3.9.12, Gradle 9.8.2.
- **Noise:** the machine was shared with other builds (load average 9 to 15), so treat
  differences under about 15% as noise.

| Scenario (compile = main + test classes) | Maven | Gradle | Gradle speed-up |
|---|---:|---:|---:|
| No-op (nothing changed) | 10.0 | 1.0 | 10x |
| Change in one resource file | 8.3 | 1.0 | 8x |
| WAR from compiled classes | 15.1 | 11.1 | 1.4x |
| WAR, nothing changed | | 1.3 | |
| Private change in a leaf class (`UserExternalIdResolverImpl`) | 64.8 | 2.8 | 23x |
| Change in one test class | 40.2 | 2.6 | 15x |
| Public API change in `User` (imported by 1,540 files) | 63.9 | 33.1 | 1.9x |
| Clean compile | 60.7 | 41.9 | 1.4x |
| Clean compile, cold Gradle daemon (as on CI)* | | 64.4 | none |
| Clean compile, from Gradle build cache* | | 2.2 | 28x |
| Unit tests, 1 fork (test execution only) | 130.2 | 132.5 | none |
| Unit tests, 4 forks | 98.3 | 68.0 | 1.4x |
| Unit tests, nothing changed | always reruns | 1.2 | |

\* Measured once, before #1350, which cannot affect them. Speed-ups compare against Maven's clean
compile.

Why the results differ:

- **Incremental compilation:** maven-compiler-plugin recompiles the whole module, all 2,467 main
  or 1,350 test files, when any source in it changes. Gradle recompiles only the classes a change
  affects, and tracks class ABIs, so a private or method-body edit does not recompile dependents.
- **Up-to-date checks and caching:** Gradle skips tasks whose inputs are unchanged, including
  `test`, and its build cache restores outputs after `clean` or a branch switch.
- **Clean builds and single-fork test runs:** these are close, because javac and JUnit do the
  same work in both. Without a warm daemon, as on CI, Gradle's clean build matches Maven's.
