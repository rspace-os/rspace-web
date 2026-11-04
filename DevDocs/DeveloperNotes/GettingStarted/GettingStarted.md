# Getting started with RSpace source code

These instructions are for anyone wanting to run RSpace from source code, on their local machine. 

**Do NOT use these instructions if you want to run RSpace on a production server or even a pilot / trial server, use rspace-docker for this.**

## Initial Setup

### Recommended hardware

For production setup we recommend a Linux-based server with at least 8GB RAM. More details: 
https://documentation.researchspace.com/article/q5dl9e6oz6-system-requirements-for-research-space

For development you should be able to run all the code and tests fine with Linux/MacOS.  

### Install required software
Java 17 is the supported version. RSpace also builds and runs on Java 21; the code is compiled for 17 (`release 17`), so a WAR built on either JDK runs on both. Gradle itself must run on 17 or 21 (`JAVA_HOME`), not just the toolchain JDK, because Spotless and the other plugins run in Gradle's own JVM; the build fails fast otherwise.

-   Install Java JDK 17 via [Adoptium](https://adoptium.net/temurin/releases/?version=17). Add JDK 21 if you want to run on 21 (see toolchains below).
-   Optionally, install [jenv](https://github.com/jenv/jenv) to manage multiple versions of Java
-   Install MariaDB 10.11 or 11.8, the versions tested in CI and supported for production RSpace. The Docker dev stack
    defaults to 11.8, as does [rspace-docker](https://github.com/rspace-os/rspace-docker). For native installs, follow
    [rspace-docker's database settings][db-config], especially `innodb_snapshot_isolation=0`, adapting filesystem paths.

[db-config]: https://github.com/rspace-os/rspace-docker/blob/main/configs/db-config.cnf

Historically we were running RSpace on MySQL database, so some docs may still mention MySQL, but go with MariaDB.
Database installation can be skipped if your only goal is to compile/build the project.

### Recommended software

RSpace builds with Gradle. Run builds with the `./gradlew` (Unix/Linux/Mac) or `gradlew.bat` (Windows) wrapper,
which downloads the right Gradle version for you; there is nothing else to install.
Run it under a UTF-8 locale (e.g. `LANG=en_US.UTF-8`), never `LC_ALL=C`.
See [Gradle.md](../Gradle.md) for the full build documentation.

### Install pnpm

The Gradle frontend build requires standalone pnpm on `PATH`. Gradle invokes pnpm
from `PATH`; it does not install pnpm. The root `package.json` pins Node.js in
`devEngines.runtime`, and pnpm downloads that Node.js version for installs,
scripts and Gradle builds, checking it against the lockfile.

Install standalone pnpm once by following the [official pnpm installation
instructions](https://pnpm.io/installation). On macOS and Linux, the installer
command is:

```bash
curl -fsSL https://get.pnpm.io/install.sh | sh -
```

Restart your shell after installation. If a Corepack pnpm shim shadows the
standalone binary, run `corepack disable pnpm` and make sure the standalone
pnpm directory is first on `PATH`. The root `package.json` pins the pnpm version;
pnpm itself honors this pin.

### Set up JDK toolchains (optional)

This enables us to run builds & tests using different JDK versions.
Without any options, the JDK running Gradle compiles the code and runs the tests.
To use another installed JDK, pass `-Djava-version=<17|21>` and optionally
`-Djava-vendor=<vendor>` (e.g. `temurin`; `openjdk` matches any vendor). Gradle
finds JDKs in `JAVA_HOME`, SDKMAN, asdf, the usual OS locations and an existing
`~/.m2/toolchains.xml`; it does not download any. Run `./gradlew javaToolchains`
to see what it found, and see [Gradle.md](../Gradle.md#toolchains) to add a JDK it
doesn't find. So to run the tests on Java 21, install JDK 21 and pass `-Djava-version=21`.

To run a branch on Java 21 in CI, start the "Lint and Test" or "e2e (mocked)" workflow from the Actions tab and pick Java 21. For "Lint and Test", also tick the jobs you want, such as the fast or full Java tests.

### Enable git hooks (recommended)

Git pre-commit hooks are managed by [lefthook](https://lefthook.dev). Enable
them once per clone by installing the root dependencies (standalone pnpm
required):

```bash
pnpm install   # run from the repo root; the prepare script runs `lefthook install`
```

`pnpm-workspace.yaml` enables `virtualStoreType: global`
so local worktrees share the virtual store.
`addMissingPeerTypes` keeps peer type declarations available to TypeScript
when dependencies live outside the project tree.

The install command sets up a `pre-commit` hook that, on the relevant staged files:

- runs `./gradlew spotlessCheck -DspotlessStrict` on staged `*.java` files and **blocks the commit if
  any need reformatting** (run `./gradlew spotlessApply -DspotlessStrict` to fix), and
- runs `pnpm run tsc` when frontend `*.ts`/`*.tsx` files are staged and
  **blocks the commit if the type-check fails**.

To bypass the hooks for a single commit (e.g. a work-in-progress), set
`LEFTHOOK=0 git commit …`.

### Check out and compile the project

Current location of the codebase is https://github.com/rspace-os/rspace-web

We recommend using a Git client to download and update the source code.

#### Create your local deployment.properties

`src/main/resources/deployments/dev/deployment.properties` holds your local settings and
credentials, so it is gitignored. Spring will not start without it, so copy the checked-in
template once after cloning:

```bash
cp src/main/resources/deployments/dev/deployment.properties.example \
   src/main/resources/deployments/dev/deployment.properties
```

The Docker dev stack (`./docker/dev/rspace-dev up`) and CI do this for you. Defaults for every
property that has a sensible production value live in
`src/main/resources/deployments/defaultDeployment.properties`; only put dev-specific overrides and
your own credentials in the copy.

To add or change a property for the whole team, edit `deployment.properties.example` (and
`defaultDeployment.properties` if it has a production value) and copy it down again. Your own
`deployment.properties` is git-ignored and the pre-commit hook refuses to stage it, so a change
made only there never reaches anyone else or CI.

#### Sanity check

As the 1st goal, you should be able to compile the java side of the code. 
In command line, navigate to RSpace project top dir and run `./gradlew classes`.

Note that on first run it'll take a while (a few minutes) to download Gradle and the 
dependencies from Maven Central and JitPack into the Gradle cache (`~/.gradle`).

If you get BUILD FAILED message pointing to some missing jar, that could be intermittent issue
so re-run the command with `--refresh-dependencies`, which forces Gradle to check the
repositories again (i.e. run `./gradlew classes --refresh-dependencies`).

Eventually you should see the BUILD SUCCESSFUL message, which means Gradle was able to resolve
all code dependencies and compile the code.

### (Optional) Build RSpace application package

The application .war file can be built with the following Gradle command:  

```
./gradlew clean war -DgenerateReactDist \
  -Denvironment=prodRelease -Dspring.profiles.active=prod -DRS.logLevel=WARN -Ddeployment=production \
  -Dtimestamp=now
```

The WAR is written to `build/libs/`. The `-DgenerateReactDist` flag makes `war` run
`pnpm install --frozen-lockfile` and the Vite production build using
Node.js and pnpm from `PATH`, then bundles the resulting `dist/` files into the
WAR. Without this flag the frontend is not built.

Jenkins frontend builds install the pinned standalone pnpm under the
workspace's ignored `node/` directory; pnpm then downloads the pinned Node.js.
The Jenkins agent needs curl, tar and either openssl or sha512sum for this setup.

You can also check top-level Jenkinsfile file to see how internal tests builds are created by
ResearchSpace dev team (check 'Build prodRelease-like package' stage script).  

### Set up MariaDB database

**You can now use the Docker MariaDB setup guide [here](/DevDocs/DeveloperNotes/GettingStarted/dockerMariaDB.md)**

#### MariaDB initialisation

Take these steps if you do not have MariaDB, or have a fresh installation of MariaDB and ***are using standalone DB instead of docker image***.

```bash
# These steps are specific to Ubuntu and latest MariaDB, adapt as needed

# 1. Install the database package (your package manager might be apt, dnf, yum etc., here is apt)
sudo apt update && sudo apt install mariadb-server

# 2. Initialise the database before starting the systemd service
mysqld --initialize

# 3. Change the temporary password created in the above step to "password" (or anything you like)
mysqladmin --user=root --password="PASSWORD-FROM-STEP-2" password "password"

# 4. Start the systemd service
mysqld
```

#### RSpace table setup (***for standalone install - ignore this if using docker***)

1.  Make sure MariaDB is installed and running on your machine.
2.  Set up an 'rspacedbuser' user and 'rspace' database on your MariaDB using this command:

```bash
mysql -u "root" -p"password" -e "
  CREATE USER 'rspacedbuser'@'127.0.0.1' IDENTIFIED BY 'rspacedbpwd';
  CREATE DATABASE rspace collate 'utf8mb4_unicode_ci';
  GRANT ALL ON rspace.* TO 'rspacedbuser'@'127.0.0.1';
  GRANT ALL ON hibtest.* TO 'rspacedbuser'@'127.0.0.1';
"
```

The second grant covers a scratch `hibtest` database used by the
`HibernateTest` classes, which check the Hibernate mappings. They create that
database themselves on first run and Hibernate builds and drops its schema each
run, so nothing else is needed, but without the grant those tests skip rather
than fail and the mappings go unchecked. The `hibtest.db` system property
overrides the name. It must start with `hibtest` so it can never point at the
application database, and be a plain identifier of up to 64 characters;
otherwise the tests fail before touching the database. Jenkins sets it per branch so
concurrent builds on its shared MariaDB do not share a schema. A local override
needs a matching grant, for example ``GRANT ALL ON `hibtest%`.* TO
'rspacedbuser'@'127.0.0.1';``.

**NOTE:** depending on OS and DB used, your may need to change the username in creation/grant commands
from `'rspacedbuser'@'127.0.0.1'` to `'rspacedbuser'@'localhost'`. (Or you may need to just use `'rspacedbuser'` with no host)
You will know if running the tests/app gets you db authentication error for user `'rspacedbuser'@'localhost'`
or you see `'Access denied for user 'rspacedbuser'@'%' to database 'rspace'`

**NOTE:** if you subsequently drop the rspace DB, the rspacedbuser user will stay; do not try to recreate the user.

At the end of this, you should be able to connect to a standalone mysql rspace database from
your command line. with `bash mysql -urspacedbuser -prspacedbpwd rspace`.
For Mariadb use
`mariadb -urspacedbuser -prspacedbpwd rspace` (if using docker you need to run this after `docker exec -it rspace-db bash`)

'rspace' database is used for running acceptance tests and the application on localhost.

### Set up RSpace FileStore location

Add the location of a base folder for the file store as an environment
variable (or in a shell login script). Media files such as videos,
images etc., are stored outside the DB in the file store. (Optional)
e.g., `export RS_FILE_BASE=~/.researchspaceFStore` (**Note:** startup will fail
if this folder does not exist)

### Run the default test suite (recommended)

To run tests: `./gradlew test -Denvironment=drop-recreate-db` will drop
and recreate the database tables and run JUnit tests. This will run all
unit and Spring integration tests and may take over 10 mins to run.
If these pass, or there are just a few failures, then your setup is basically fine.

Tests tagged `chemistry` are excluded because they require the OSS chemistry
service. CI runs them in a separate job. See
[CodingStandards.md](CodingStandards.md#gradle-integration) for the local command
and the conventions for performance and live-service tests.

### Running fast tests
This just runs plain Junit tests and is much faster to run:

`./gradlew test -Dfast=true`

## Running RSpace server

### (Experimental) Run the whole stack in Docker

> **Experimental.** This is a newer, convenience-focused workflow and is not yet
> the officially supported way to run RSpace from source. If you hit problems,
> fall back to the manual Gradle/Jetty workflow described below. Feedback welcome.

If you want a complete, running RSpace (database + backend + frontend) without
installing Java, Node, or MariaDB locally, use the Dockerized dev stack
in [`docker/dev/`](/docker/dev/README.md). From inside your checkout (or any git
worktree):

```bash
./docker/dev/rspace-dev up      # build images, start db + backend + frontend
./docker/dev/rspace-dev logs app   # watch backend startup (first boot is slow)
```

It boots the same workflow described below, just containerized: the worktree
source is bind-mounted in (frontend changes hot-reload; Java changes apply via
`./docker/dev/rspace-dev reload`), and each git worktree gets its own isolated
instance with its own database and auto-assigned host ports, so several can run
at once. The launcher prints the URL to open and the test logins. See
[docker/dev/README.md](/docker/dev/README.md) for the full command set and
caveats.

Prerequisites: Docker Engine + Docker Compose v2 (Docker Desktop works). Only
needed for this option; the manual workflow below does not use Docker.

The rest of this section describes the manual (non-Docker) workflow.

### Launch RSpace with Gradle and Jetty

When starting RSpace for the first time use the following command: 

```bash
./gradlew jettyRun -Denvironment=drop-recreate-db -DRS.devlogLevel=INFO \
-Dspring.profiles.active=run -DreactDevMode=true \
-Dlog4j2.configurationFile=log4j2-dev.xml
```
This command clears and sets up the database, so it takes a bit longer to run. RSpace is then
at http://localhost:8080 (`-Djetty.http.port=<port>` changes the port). JVM options such as
`-Xmx` or a debugger agent go in `-PjettyJvmArgs='<args>'`; see
[Gradle.md](../Gradle.md#jetty).

When `-DreactDevMode=true` is set, Jetty proxies frontend asset requests to a
local Vite dev server for Hot Module Replacement (HMR). Start the Vite dev
server in a separate terminal:
```bash
pnpm install --frozen-lockfile
pnpm run serve
```

Legacy `rst:assetUrl` references under `/scripts/**` and `/styles/**` do not add the `?v=`
cache-busting token in dev mode by default. To opt back in while debugging legacy frontend asset
changes, add `-DlegacyAssetCacheBustingInDevMode=true` to the Gradle command. Production always
keeps cache busting enabled.

To keep the database intact, replace `-Denvironment=drop-recreate-db`
with `-Denvironment=keepdbintact`. Unless you're working on new database
tables then `-Denvironment=keepdbintact` is what you'll want to use most
of the time, to speed up launch times.

#### Connect to RSpace

Open your browser and navigate to http://localhost:8080. You should see
the login page.

### Default user accounts

When running server in localhost mode as describe above you'll be able
to login with a bunch of pre-set accounts:

User accounts:
user1a, user2b, user3c ... user8h (all with 'user1234' password)

System Admin account:
sysadmin1 (with 'sysWisc23!' password)

### Running RSpace in different modes

#### Running product-specific builds

There are 3 product variants:
1. Enterprise Standalone.
2. Enterprise Institutional (SingleSignOn).
3. Community (a slightly customized version that runs at community.researchspace.com)

These have slightly different behaviours. To run in development, add the
following to your Gradle launch command:

Enterprise Standalone: this is the default, no extra configuration is needed.

Enterprise Institutional SSO: `-Ddeployment.sso.type=TEST -Dmock.remote.username=user -Ddeployment.standalone=false`
(where the username is for a user already in the database)

Community: `-Ddeployment.cloud=true`

Each variant also selects a Liquibase context, which controls how database changesets
are applied. RSpace deployments use one of three context strings:

- **`run`** — Enterprise / production (the default, from `defaultDeployment.properties`)
- **`run,dev-test`** — local dev and test runs (from `dev/deployment.properties`); adds test/sample data
- **`run,cloud`** — Community / cloud

See `src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md` for what each context tag means.

#### Configure logging levels for debugging purposes

Adding the following to the command line:

`-Dspring.logLevel=[DEBUG, WARN,INFO,ERROR or FATAL]`
`-Dhibernate.logLevel=[DEBUG, WARN,INFO,ERROR or FATAL]`
`-DRS.devlogLevel=[DEBUG, WARN,INFO,ERROR or FATAL]`

alters the logging verbosity for various frameworks or RSpace code.

#### Set deployment properties through command line
`-Djdbc.url=jdbc:mysql://localhost:3306/another_database -Djdbc.db=another_database -Djdbc.db.maven=another_database`
specifies to use a different database `another_database` at the url `jdbc:mysql://localhost:3306/another_database`.

`-DRS_FILE_BASE=/path/toFileStore`
overrides the default filestore location.

`-Drs.httpcache.enabled=true`
true/false will enable/disable the ability to set HTTP response headers
to cache.

#### Capturing real emails locally with Mailpit

`dev/deployment.properties` sets `email.enabled=false`. To exercise a real mail flow (password reset, forgotten username, signup verification, sharing notifications) against a
local mailbox, run a [Mailpit](https://mailpit.axllent.org/) container and
point RSpace's mail properties at it:

```bash
docker run -d --name mailpit --rm \
  -p 1025:1025 -p 8025:8025 \
  -e MP_SMTP_AUTH_ACCEPT_ANY=1 -e MP_SMTP_AUTH_ALLOW_INSECURE=1 \
  axllent/mailpit:latest

./gradlew jettyRun -Denvironment=keepdbintact -DRS.devlogLevel=INFO \
  -Dspring.profiles.active=run -DreactDevMode=true \
  -Demail.enabled=true -Dmail.emailHost=localhost -Dmail.port=1025 \
  -Dmail.emailAccount=mailpit -Dmail.password=mailpit -Dmail.ssl.enabled=false \
  -Dmail.from=noreply@rspace-e2e.local
```

`EmailBroadcastImpl` always performs SMTP AUTH; Mailpit accepts any
credentials when started with `MP_SMTP_AUTH_ACCEPT_ANY=1`/
`MP_SMTP_AUTH_ALLOW_INSECURE=1`, so `mail.emailAccount`/`mail.password` can be
any placeholder; no code changes are needed. `mail.from` does need a
syntactically valid override though: `deployments/dev/deployment.properties`
doesn't set it, so it inherits `defaultDeployment.properties`' literal
`support@<your_server>.com` placeholder, which Mailpit's strict SMTP
validation rejects outright (`553 5.1.3 The address is not a valid RFC 5321
address`) on `MAIL FROM`. Read captured mail at
`http://localhost:8025`. If you're using the Docker dev stack instead of a
bare `./gradlew jettyRun`, use `rspace-dev up --mailpit` instead (see
`docker/dev/README.md`) — it wires the same properties automatically.

#### Running RSpace in production mode locally

Run the usual `./gradlew jettyRun` command, just change the active spring profile to `prod`
i.e. pass a `-Dspring.profiles.active=prod` parameter.

Note that when running through jetty, the `defaultDeployment.properties` file is not used for some reason.
That means deployment properties that are not explicitly set in your `deployment.properties` file may have unexpected values. 

#### Compiling RSpace production mode package

Check the Jenkinsfile and the Gradle command run during "Build prodRelease-like package" stage.

### Developer docs on specific subjects

Developer docs on specific subjects are as follows:

| Document                                                              | Explanation |
| ----------------------------------------------------------------------|-------------|
|[Java.md](Java.md)                                         | How to setup your machine for back-end development. |
|[Gradle.md](../Gradle.md)                                  | The build: commands, settings, tests and dependencies. |
|[CodingStandards.md](CodingStandards.md)                   | Our conventions. |
|[Logging.md](../Logging.md)                                | How to configure and use logging. |
|[Caching.md](../Caching.md)                                | About server-side and client-side caching. |
|[Transactions.md](../Transactions.md)                      | Set up of how transaction boundaries are defined. |
|[AddingANewMessageType.md](../AddingANewMessageType.md)    | How to add a new message type. |
|[SecureConnectionConfig.md](../SecureConnectionConfig.md)  | How to set up Jetty to use HTTPS. |
|[SecurityAndPermissions.md](../SecurityAndPermissions.md)  | How permissions and roles work. |
