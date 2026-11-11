# Gradle build

RSpace builds with Gradle only. `build.gradle` (Groovy DSL) compiles, tests, packages the WAR,
formats Java and runs the database tooling; `buildSrc/` holds its Java helper classes. Use the
wrapper, `./gradlew` (`gradlew.bat` on Windows), which downloads the pinned Gradle version
(`gradle/wrapper/gradle-wrapper.properties`).

`gradle.properties` turns on the build cache and the configuration cache, so unchanged tasks are
skipped, `clean` builds restore outputs from the cache, and a repeated command skips
configuration.

## Before you start

- **JDK:** run Gradle on Java 17 or 21 (`JAVA_HOME`). The build fails fast on any other version,
  because Spotless and the other plugins run in Gradle's own JVM. The code always compiles with
  `--release 17`, so a WAR built on either JDK runs on both.
- **Locale:** run Gradle under a UTF-8 locale, e.g. `LANG=en_US.UTF-8` (CI uses `C.UTF-8`), and
  never with `LC_ALL=C`. Some test resource file names are non-ASCII, and a daemon started under
  `LC_ALL=C` cannot read them. If one did, run `./gradlew --stop`.
- **pnpm:** frontend tasks (`-DgenerateReactDist`, `-DgenerateStorybook=true`) call standalone
  `pnpm` from `PATH`.
- **Database:** the Spring and integration tests and Jetty need MariaDB; see
  [GettingStarted.md](GettingStarted/GettingStarted.md).

## Commands

| Command | What it does |
|---|---|
| `./gradlew classes` / `testClasses` | Compile main / main and test classes |
| `./gradlew test -Dfast=true` | Run the pure unit tests (no database) |
| `./gradlew test` | Run the unit and Spring tests against the database as it is |
| `./gradlew test -Dtest=MyClassTest` | Run one test class (see [-Dtest](#selecting-tests)) |
| `./gradlew integrationTests` | Run the `*IT` tests: `integrationTest`, `integrationTestRun`, then `integrationTestProblematic` |
| `./gradlew verify -Denvironment=drop-recreate-db` | Recreate the database, run `test`, build the WAR, then run `integrationTests` |
| `./gradlew check` | `test`, `integrationTests`, `spotlessCheck` and `checkPinnedVersions` |
| `./gradlew war` | Build `build/libs/researchspace-<version>.war` |
| `./gradlew war -DgenerateReactDist` | Build the WAR with the React/Vite bundle |
| `./gradlew jettyRun` | Run RSpace in Jetty from the compiled classes (see [Jetty](#jetty)) |
| `./gradlew jettyRunWar -PwarFile=<path>` | Run a built WAR in Jetty |
| `./gradlew spotlessApply` / `spotlessCheck` | Format Java / check its formatting |
| `./gradlew liquibaseDiff` | Write a Liquibase changelog of `rspace` versus `rspace_hib` |
| `./gradlew checkPinnedVersions` | Check `gradle/pinned-versions.txt` against the classpaths |
| `./gradlew dumpClasspaths` | Write each classpath's modules to `build/classpaths/` |
| `./gradlew printVersion` | Print the project version |

`./gradlew tasks --all` lists every task, and `./gradlew help --task <task>` describes one.

Test tasks whose inputs have not changed are skipped as up to date. Add `--rerun` to run one
again (`./gradlew test --rerun`), or `--continue` to keep going after a failing task. Test tasks
always run when the build recreates the database.

## Build settings: `-D` and `-P`

Every `-D<name>=<value>` on the Gradle command line is a build setting. It:

- overrides the resource-filter property of the same name (see [Resource filtering](#resource-filtering));
- is passed as a system property to every test JVM and to the Jetty JVM.

So `-Dspring.profiles.active=run`, `-DRS_FILE_BASE=...` or `-Djdbc.db.maven=rspace` reach both
the filtered resources and the running code. A bare `-Dname` means `-Dname=true`. `-P<name>=<value>`
also works for the switches below and for the names in `gradle/resource-filter.properties` (plus
`jdbc.url`, `ssl.enabled` and `RS.devlogLevel`), but no other placeholder, and it is not passed to
test or Jetty JVMs. Use `-D` for anything else, such as `-Djdbc.username` or `-Dmail.host`.

Switches:

| Setting | Effect |
|---|---|
| `-Denvironment=drop-recreate-db` | Test tasks (and `jettyRun`) first drop the test database and reload it from the baseline schema |
| `-Denvironment=drop-recreate-hibernate-db` | As above, but for an empty `rspace_hib` (see [Database tasks](#database-tasks)) |
| `-Denvironment=keepdbintact` | Use the database as it is |
| `-Denvironment=prodRelease` | Release build: renders `htmlDocs` and runs `checkReleaseDependencies` before `war` |
| `-DgenerateReactDist` | `war` first runs `pnpm install --frozen-lockfile` and the Vite build |
| `-DgenerateStorybook=true` | `war` builds Storybook into `WEB-INF/storybook` |
| `-DenableTestCoverage` | JaCoCo on every test task (see [Coverage](#coverage)) |
| `-DspotlessStrict` | Spotless adds the CleanThat steps |
| `-DspotlessFiles=<regex>,...` | Limit Spotless to files whose absolute path matches one regex |
| `-Dtimestamp=<value>` / `-Dtimestamp=now` | Version stamp; defaults to `dev` |
| `-Ddeployment=<name>` | Package `src/main/resources/deployments/<name>/` (default `dev`) |
| `-Djava-version=<17\|21>`, `-Djava-vendor=<vendor>` | Pick a toolchain JDK (see [Toolchains](#toolchains)) |
| `-Dtest=<pattern>,...` | Select test classes or methods |
| `-Dfast=true` | Tests skip the Spring and database tests |
| `-DincludedTestGroups=<tags>` / `-DexcludedTestGroups=<tags>` | JUnit tags to include / exclude; `chemistry` is excluded by default, and `-DexcludedTestGroups=` excludes nothing |
| `-Dsurefire.rerunFailingTestsCount=<n>` | Retry each failed test up to n times |
| `-DforkCount=<n>` or `<n>C`, `-PmaxTestForks=<n>` | Parallel test JVMs (`1C` = one per core) |
| `-Dmaven.test.failure.ignore=true` | Test failures do not fail the build |

The `generateReactDist`, `enableTestCoverage` and `spotlessStrict` switches are on whenever they
are given, whatever their value; `generateStorybook` needs `=true`. An unknown
`-Denvironment` value fails the build. The `jdbc.*.maven`, `surefire.*` and `maven.*` names are
kept from the old Maven build so that existing commands and CI jobs keep working.

The version is `2.28.0-SNAPSHOT-<timestamp>`. The fixed `dev` default keeps resources up to date
between local builds; release builds pass `-Dtimestamp=now` or a build number.

## Tests

| Task | Classes |
|---|---|
| `test` | `Test*`, `*Test`, `*Tests`, `*TestCase` |
| `integrationTest` | `*IT`, apart from the two lists below |
| `integrationTestRun` | `RunConfigWiringTestIT`, `SysAdminConfigControllerMVCIT`, `SysAdminSupportControllerMVCIT` |
| `integrationTestProblematic` | `ExportApiControllerMVCIT`, `CreateMissingUserFoldersForLabAdminsAndPIsIT` |

`integrationTests` runs the three integration tasks in that order, and `verify` runs `test` and
`war` before them. Test tasks share one database, so they never run in parallel with each other.

Results: JUnit XML in `build/test-results/<task>/`, HTML reports in `build/reports/tests/<task>/`.
Failures print in full on the console.

Only `-Dfast=true` runs, which skip every test that needs a database, can be up to date or come
from the build cache. Any other test run always executes, because it reads a database Gradle cannot
see. `-Dfast` runs also track the files tests read straight from the source tree
(`src/main/webapp`, `src/main/java`, `src/main/resources`, `src/test/resources`). Add `--rerun` to
force a `-Dfast` run.

### Selecting tests

`-Dtest=<pattern>[,<pattern>...]` takes:

- a class name or glob: `MyClassTest`, `*Operation*Test`;
- a method: `MyClassTest#myMethod`, or `MyClassTest#m1+m2`;
- a path: `**/MyClassTest.java`;
- `!pattern` to exclude.

It replaces each test task's own class patterns, so `./gradlew test -Dtest=MyClassIT` runs an
`*IT` class under `test`. A task with no matching class passes. Gradle's own
`--tests '<pattern>'` also works, on one task at a time.

So `integrationTests -Dtest=MyClassIT` runs the class three times, once in each integration task,
as Maven's three Surefire executions did. Run one `*IT` class with `integrationTest -Dtest=...`.

### CI test partitions and shards

`test` without suite options keeps running all unit and Spring tests. CI partitions it by
JUnit's inherited `slow` tag: `-Dfast` excludes that tag and `-PtestSuite=slow` includes it.
`@WithSpringContext` and the non-Spring `HibernateTest` base carry the tag. The PDF/SVG tests previously skipped in fast mode carry
it directly. Add the tag to any new test that needs Spring, a database, or the slow-only setup.

The Spring and integration jobs each run two shards per MariaDB version, with an independent
database and filestore on every runner. `-PtestShard=1/2` and `-PtestShard=2/2` assign complete
top-level classes using duration estimates in `gradle/ci-test-durations.json`. Nested tests stay
with their parent. Unknown classes are assigned too; estimates affect balancing, never coverage.
The run-profile and problematic integration groups retain their ordering and run on shard 1.
The balancer reserves their estimated class durations and ten seconds per extra test JVM on
that shard before assigning ordinary integration classes.
JUnit XML and HTML report directories have a `-shardN` suffix; CI publishes only its current
shard, so switching shards locally cannot republish stale special-group results.
Sharding cannot be combined with `-Dfast` or `-Dtest`; shard `test` with `-PtestSuite=slow`.

Estimates retain the November 4 parity samples for unsampled classes and use the November 5
[Gradle CI reports](https://github.com/rspace-os/rspace-web/actions/runs/42038679444) for the
Spring and integration classes. Refresh them after
large test changes with full JUnit XML report directories, for example:

```bash
python3 scripts/gradle/update-ci-test-durations.py reports/spring reports/integration
```

CI uploads the shard JUnit XML reports as seven-day artifacts. Combine all shards from one
run into one report directory per suite and MariaDB version before refreshing estimates. Keep separate full-run
directories for repeated samples; the script takes the median per class.
To check selection without running test bodies, use `--test-dry-run` on each concrete test task,
and compare the combined class/method inventory against the unpartitioned task. `integrationTests`
is an aggregate; apply `--test-dry-run` to `integrationTest`, `integrationTestRun`, and
`integrationTestProblematic` individually.

### Retries

The `org.gradle.test-retry` plugin reruns failed tests when `-Dsurefire.rerunFailingTestsCount=<n>`
is set. A test that passes on a retry does not fail the build, and its JUnit XML matches
Surefire's: one test case with a `flakyFailure`, left out of the suite's failure count, so CI's
test-results checks and Jenkins treat it as passed. CI's integration job retries once.

### Database suites

Set the database up as CI does: start MariaDB, run the "Configure MariaDB" SQL from the
`spring-tests` job in `.github/workflows/lint-and-test.yml`, and seed
`src/main/resources/deployments/dev/deployment.properties` from its `.example`. Then run what CI
runs:

```bash
# Slow tests, including Spring tests; pure units run separately with -Dfast
./gradlew test -PtestSuite=slow -Denvironment=drop-recreate-db \
  -Djavax.xml.accessExternalDTD=all -Dlog4j2.configurationFile=log4j2-dev.xml \
  -Djdbc.db.maven=rspace -Djdbc.url=jdbc:mysql://localhost:3306/rspace \
  -DRS.devlogLevel=INFO -DRS_FILE_BASE=$RS_FILE_BASE

# Integration tests
./gradlew integrationTests --continue -Dsurefire.rerunFailingTestsCount=1 <same flags>
```

The Hibernate model tests use `jdbc.url.maven` for their scratch database too.
For a server on another port, change the port in `-Djdbc.url`, which the app and tests connect
to, and add `-Djdbc.url.maven=jdbc:mysql://localhost:<port>`, which `recreateDatabase` uses.

With `-Denvironment=drop-recreate-db`, every test task depends on `recreateDatabase`, which:

- drops and recreates `jdbc.db.maven` (default `rspace`) on `jdbc.url.maven`, as
  `jdbc.username.maven`;
- loads `src/main/resources/sqlUpdates/liquibaseConfig/rs-dbbaseline-utf8.sql`;
- leaves the Liquibase changesets for RSpace to apply when it starts.

`clean` then also empties the `archive/` and `pdfs/` folders of the attachment search index.

The chemistry tests need the OSS chemistry service; `scripts/run-chemistry-tests.sh` runs them.

## Jetty

`gradle/jetty.gradle` adds these tasks, which took over from the Maven build's `jetty:run` and
`jetty:run-war`:

| Task | Runs |
|---|---|
| `./gradlew jettyLaunchArgs <-D flags>` | builds the exploded app and writes `build/jetty/launch.args` for `java @argfile` |
| `./gradlew jettyRun <-D flags>` | the webapp exploded from the build outputs and `src/main/webapp` |
| `./gradlew jettyRunWar -PwarFile=<path> <-D flags>` | a WAR built earlier |
| `./gradlew jettyRunWar <-D flags>` | the WAR this build makes, building it first |

The run tasks execute `src/jetty/java`'s launcher in a separate JVM on Jetty 12.1 ee10, at context path `/`.
The Jetty jars stay in the `jetty` source set, so they never reach the WAR.

- **What `jettyRun` serves:** `build/classes/java/main`, `build/resources/main` and the WAR's
  libraries as the webapp classpath, with `src/main/webapp` as the exploded webapp. JSPs,
  `web.xml`, `ServletContainerInitializer`s and JNDI work as under `jetty:run`.
- **System properties:** every command-line `-D<name>=<value>` becomes a system property of the
  Jetty JVM, as it was of the Maven JVM that ran `jetty:run` in-process.
- **Port:** `-Djetty.http.port=<port>` (default 8080).
- **HTTPS:** `-Dssl.enabled=true` configures the server from `jetty/*.xml`: HTTP on 8080 (or
  `-Djetty.http.port`), HTTPS with HTTP/2 on 8443 (`-Dhttps.port`). The app's Shiro config
  redirects to 8443, so keep the default for the full flow.
- **Database:** `-Denvironment=drop-recreate-db` runs `recreateDatabase` first.
- **Frontend:** `-DgenerateReactDist` runs `buildFrontend` first (`jettyRun` only).
- **JVM arguments:** pass them in `-PjettyJvmArgs='<args>'`, or the
  `ORG_GRADLE_PROJECT_jettyJvmArgs` environment variable, separated by whitespace. These are the
  flags Maven took from `MAVEN_OPTS`, for example:

  ```bash
  ./gradlew jettyRun -Dspring.profiles.active=run ... \
    -PjettyJvmArgs='-Xmx4g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005'
  ```

  The `--add-opens` flags the app needs on Java 17 (formerly in `.mvn/jvm.config`) are always
  added. The Jetty JVM is the JVM that runs Gradle, unless a Java toolchain is selected.
- **Redeploy:** with `jettyRun`, each line read on stdin restarts the webapp and reloads its
  classes, like `jetty:run` with `<scan>0</scan>`. Run `./gradlew classes` (or `processResources`)
  in another shell first, then press Enter.
- **Hot swap:** under the JetBrains Runtime, pass the HotswapAgent flags in `jettyJvmArgs`. The
  agent can read its config from a file instead of the webapp classpath:
  `-XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=external
  -javaagent:<path>/hotswap-agent.jar=propertiesFilePath=<path>/docker/dev/hotswap-agent.properties`.
- **Stopping:** Ctrl-C or SIGTERM to the Gradle client stops Jetty cleanly.


A typical development run:

```bash
./gradlew jettyRun -Denvironment=keepdbintact -DRS.devlogLevel=INFO \
  -Dspring.profiles.active=run -DreactDevMode=true \
  -Dlog4j2.configurationFile=log4j2-dev.xml
```

The Docker dev stack (`docker/dev/`) builds with `jettyLaunchArgs --no-daemon`,
then execs `java @/var/rspace-build/app/jetty/launch.args`. The argfile and `jettyRun` share JVM
options, properties, classpaths and program arguments. The file can contain
credentials, so it is owner-readable only. Jetty becomes PID 1 and receives
SIGTERM directly. Its shutdown hook stops Jetty and Spring; Compose allows
120 seconds before killing it. Gradle exits before Jetty starts, so reload uses
the same project cache without a resident build daemon. Container outputs and
Gradle caches use per-stack volumes outside `/rspace`, under `/var/rspace-build`.
An external project view links to the live source and copies Gradle settings and
build scripts. An init script sets root and buildSrc output directories.
Both builds use separate default `.gradle` caches in the external view. Host clean/build commands cannot detach these mounts.
Host and container builds and other stacks use independent outputs and locks.
Reload keeps the startup environment and resource-filter flags. `classes` does
not depend on `recreateDatabase`, even with `drop-recreate-db`. Unchanged
classes/resources stay present; incremental compilation may briefly remove
changed classes. Changed resource inputs can still replace resource outputs.

SIGTERM during redeploy waits for Spring startup before shutdown. The Docker
stack allows 120 seconds for slow shared machines. Manual redeploy invalidates
sessions, matching Maven's clean redirect to login.

## Resource filtering

`processResources`, `processTestResources` and `war` replace `${name}` and `@name@` placeholders
with build settings. Values come from, lowest precedence first:

1. `gradle/resource-filter.properties`;
2. the `-Denvironment` value's settings (in `build.gradle`);
3. `-P<name>=<value>`, for the names in `gradle/resource-filter.properties` and `jdbc.url`,
   `ssl.enabled` and `RS.devlogLevel`;
4. `-D<name>=<value>`.

`buildSrc`'s `PlaceholderFilter` replaces only known names and leaves line endings alone. Binary
files (images, archives, Office documents and so on) are copied unfiltered, as is
`applicationContext-dao.xml`, whose placeholders Spring resolves from `deployment.properties` at
runtime. The WAR filters only `META-INF/**` and `WEB-INF/security.xml`.

Only the `-Ddeployment` folder of `src/main/resources/deployments/` is packaged.

## Coverage

`-DenableTestCoverage` puts JaCoCo on every test task and adds two report tasks, which run after
the tests:

```bash
./gradlew test -DenableTestCoverage                # also runs jacocoTestReport
./gradlew integrationTests -DenableTestCoverage    # also runs jacocoIntegrationTestReport
```

- Unit and Spring tests: `build/reports/jacoco/test/jacocoTestReport.{xml,csv}` and `html/`.
- Integration tests: `build/reports/jacoco/integrationTests/jacocoIntegrationTestReport.{xml,csv}`
  and `html/`.
- Execution data: `build/jacoco/<task>.exec`.

`.github/workflows/coverage.yml` merges the exec files of its shards with the JaCoCo CLI.

## Formatting (Spotless)

`spotlessApply` formats Java with google-java-format and removes unused imports;
`spotlessCheck`, part of `check`, fails on unformatted files. Code between
`// spotless:off` and `// spotless:on` is left alone.

- `-DspotlessStrict` adds the CleanThat steps. CI and the pre-commit hook check with it, so fix
  their failures with `./gradlew spotlessApply -DspotlessStrict`.
- `-DspotlessFiles=<regex>,...` limits both tasks to files whose absolute path matches one regex,
  e.g. `-DspotlessFiles='.*/MyClass[.]java'`. The lefthook pre-commit hook uses it to check only
  staged files.

## Database tasks

- `recreateDatabase` drops and recreates the test database. Test tasks run it under
  `-Denvironment=drop-recreate-db` or `drop-recreate-hibernate-db`.
- `./gradlew recreateDatabase -Denvironment=drop-recreate-hibernate-db` drops and recreates an
  empty `rspace_hib`, without the baseline schema. Nothing in the build generates a schema from
  the Hibernate mappings; see
  [DatabaseChangeGuidelines.md](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md).
- `liquibaseDiff` compares `rspace` with `rspace_hib` and writes the changes that would bring
  `rspace` in line to `generatedDBChangeSets/diffChangeset-<timestamp>.xml`. The connection
  settings come from `src/main/resources/sqlUpdates/liquibaseConfig/liquibase.properties`;
  override one with `-Dliquibase.<key>=<value>`, e.g. `-Dliquibase.referenceUrl=...`.

## Release builds

`-Denvironment=prodRelease`:

- renders `DevDocs/public/*.md` to `build/htmlDocs/` (`htmlDocs`);
- fails `war` if the version or any dependency is a snapshot (`checkReleaseDependencies`).

See the Jenkinsfile's "Build prodRelease-like package" stage for the full command, e.g.:

```bash
./gradlew clean war -DgenerateReactDist -Dtimestamp=now -Denvironment=prodRelease \
  -Dspring.profiles.active=prod -DRS.logLevel=WARN -Ddeployment=production
```

## Storybook

`./gradlew war -DgenerateReactDist -DgenerateStorybook=true` builds Storybook
(`buildStorybook`, `pnpm run storybook:build`) and packages it under `WEB-INF/storybook`, where
the deployment properties control access to it. See [Storybook.md](Storybook.md).

## Toolchains

Without `-Djava-version` or `-Djava-vendor`, the JDK running Gradle compiles and runs the tests.
With them, Gradle uses a matching installed JDK as a toolchain for compiling and testing:

- `-Djava-version` is `17` (the default) or `21`;
- `-Djava-vendor` is e.g. `temurin`, `zulu`, `corretto` or `oracle`; `openjdk` matches any vendor.

Gradle looks for JDKs in `JAVA_HOME`, `~/.m2/toolchains.xml`, SDKMAN, asdf, the OS's usual
locations and the other places it
[auto-detects](https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection).
Nothing is downloaded. `./gradlew javaToolchains` lists the JDKs it found. To add one elsewhere,
set `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.

Spotless and the other plugins always run on the JDK running Gradle, which must be 17 or 21.

## Dependencies

Repositories are declared once, in `settings.gradle`: Maven Central, JitPack for
`com.github.*` (the `rspace-os` libraries), and the DICE repository for `org.irods*`. Projects
cannot add their own, and `mavenLocal()` is never used, so a library built locally cannot
shadow the published one. To change an `rspace-os` library, push it, wait for JitPack to build
the release, then update the version here.

Versions are chosen by hand, in `gradle/pinned-versions.txt`: one line per module on any
classpath, `group:artifact=version scope`. `build.gradle` forces every pinned version, so no
transitive dependency drifts, and keeps each module off the classpaths its scope excludes:

| Scope | Classpaths |
|---|---|
| `compile` | main compile and runtime, tests |
| `provided` | main compile and tests; not the runtime classpath or the WAR |
| `runtime` | main runtime and tests |
| `test` | tests only |

`checkPinnedVersions` (part of `check`, and run by CI) fails if a module on a classpath has no
pin, or a pin matches no module, and lists them.

**To add a dependency:**

1. Declare it in `build.gradle`'s `dependencies` block, with the configuration for its scope
   (`implementation`, `runtimeOnly`, `providedCompile`, `testImplementation`, ...) and any
   `exclude`s.
2. Add a pin for it to `gradle/pinned-versions.txt`.
3. Run `./gradlew checkPinnedVersions` and add pins for the new transitive modules it lists.

**To upgrade or downgrade one:** change the version in the pin, and in `build.gradle` too if it is
declared there. The pin wins if they differ, so keep them equal. Transitive modules have only a
pin.

**To remove one:** delete its declaration, then remove the pins `checkPinnedVersions` lists.

To review a change, run `./gradlew dumpClasspaths` before and after and diff
`build/classpaths/*.txt`. `./gradlew dependencyInsight --configuration runtimeClasspath
--dependency <name>` shows why a module is on a classpath.

## CI

GitHub Actions jobs set up Java and Gradle with the local action
`.github/actions/setup-gradle-cached`. It:

- installs the JDK with `actions/setup-java`, and sets `LANG=C.UTF-8`;
- caches dependencies and wrapper files in `~/.gradle/caches` and `~/.gradle/wrapper`, keyed
  by job family and build/dependency declarations;
- separately caches task outputs in `~/.gradle/rspace-build-cache`, keyed by OS, JDK vendor/version,
  job family, build declarations and main commit. Restore prefixes first match build declarations,
  then fall back within the same OS/JDK/job family. Gradle validates individual task inputs.
  A new main commit saves new outputs even when dependencies have not changed; Actions
  cache entries are immutable, so dependency-only keys would leave compiled outputs stuck at
  the first seed;
- saves the cache only on `main`; other branches and pull requests restore `main`'s;
- runs `gradle/actions/setup-gradle` with `cache-provider: external`, which leaves caching to
  the step above and still validates the wrapper.

The first trusted main run seeds these global archives. PRs restore them and share new
compilation outputs within their own workflow run; they do not publish global caches.
Database-backed test results remain uncacheable. Frontend and Storybook bundles are explicitly cacheable across runners, with source,
config, public assets, build environment, OS/architecture, Node and pnpm versions as inputs.
The release Markdown renderer has its own source set and runtime classpath; ordinary builds
neither compile the renderer nor resolve Flexmark through build logic.

Run it after checkout and before `pnpm install`, so the cache key's `**/*.gradle*` glob does not
walk `node_modules`. Jenkins runs the same `./gradlew` commands; see the `Jenkinsfile`.

### Reusing work without additional runners

Change detection only selects jobs. Compilation runs independently on each Java runner,
using the main-only task cache where available. The experimental within-run compilation
producer was removed after CI showed that its position in change detection delayed frontend
jobs and increased elapsed time. No additional runners are used.

## CI optimization verification

Checked locally on November 5, Java 17 and MariaDB 11.8. The two full Spring shards and two
full integration shards passed. The final fast partition and Hibernate model tests were then
rerun after moving the non-Spring Hibernate base into the slow partition and making its scratch
connections respect `jdbc.url.maven`. This is combined verification, rather than one fresh full
suite run after that final fixture change.

| Check | Result |
|---|---|
| Final selector coverage | The old inventory is retained, plus two URL-setting tests. Fast and slow partitions are disjoint; both slow shards and both integration shards have complete coverage. |
| Pure unit partition | 4,777 cases, 16 skipped, no failures or errors |
| Full slow shard runs before the Hibernate-tag follow-up | 759 and 827 cases, no failures or errors |
| Hibernate model follow-up | All 41 cases execute against an isolated scratch schema, none skipped or failed |
| Full integration shards | 610 and 601 cases, 33 skipped in total, no failures or errors |
| Frontend cache | `FROM-CACHE` restores 1,300 files byte for byte; the local Gradle invocation took 3.6 seconds with warm dependencies. This is not a GitHub CI benchmark. |
| Release docs | All ten generated HTML files match their previous SHA-256 hashes |
| WAR | All 3,307 class and library entries match the previous WAR; no renderer or Flexmark library ships in it |
| Build checks | Strict Spotless, dependency pins, three build-helper tests and `pnpm tsc` pass |
| Dev stack | App starts and workspace login works through both browser MCPs; no console errors or failed inspected requests |

Backend CI runs its build-helper tests alongside formatting and pin checks in one Gradle
invocation. Warm-cache GitHub timings still need the first trusted main run to populate the
archives. Java 21 and MariaDB 10.11 were not rerun locally in this optimization round.

The follow-up without additional runners passed 3,521 frontend tests across all 507 source test
files, JUnit/blob reports and Istanbul report merging. TypeScript, Biome, strict Spotless,
dependency pins and build-helper tests passed. A fresh consumer restored both Java compile
tasks from the exported cache while regenerating its filtered resources; an unchanged producer
exported a valid empty archive. Both Spring and integration shard discovery inventories match
the unsharded suites with no overlap, and the special integration groups remain on shard one.
Database test bodies were not rerun for this balancing-only follow-up. The statistics-enabled
WAR builds successfully, emits fresh JSON without rebuilding the HTML report with `CI=true`, and excludes
bundle diagnostics. Job lists and runner matrices are unchanged. End-to-end CI timing and
runner-minute effects still need measurement after the push.

## Benchmark

Measured on 2026-11-04 while Maven still built RSpace alongside Gradle, before Maven was removed.
The benchmark script went with it.

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

- **Incremental compilation:** maven-compiler-plugin recompiled the whole module, all 2,467 main
  or 1,350 test files, when any source in it changed. Gradle recompiles only the classes a change
  affects, and tracks class ABIs, so a private or method-body edit does not recompile dependents.
- **Up-to-date checks and caching:** Gradle skips tasks whose inputs are unchanged, including
  `test`, and its build cache restores outputs after `clean` or a branch switch.
- **Clean builds and single-fork test runs:** these are close, because javac and JUnit do the
  same work in both. Without a warm daemon, as on CI, Gradle's clean build matches Maven's.
