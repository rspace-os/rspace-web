# Getting started as a Java developer

First, follow the [GettingStarted.md](GettingStarted.md) doc, which
describes local Gradle/MariaDB setup necessary to run RSpace. At the end
of that guide you should have RSpace instance running on your localhost.
The Gradle frontend build also requires standalone pnpm on `PATH`; pnpm
downloads the Node.js version pinned in `package.json`.

Second, you'll need an IDE of your choice to edit files. Instructions
below describe setup for Eclipse IDE.

## After checking out the project

Current location is https://github.com/rspace-os/rspace-web

Use a git client of your choice to checkout and manage source code.

If using Eclipse as an IDE you'll need the Eclipse Buildship Gradle
plugin to get Eclipse to download dependencies and build the project
(available from Eclipse marketplace). IntelliJ IDEA imports Gradle
projects without a plugin.

### Lombok Setup

We use a Java library tool called Lombok, which installs on top of
your IDE. Download it from https://projectlombok.org/ and point the
installer to your Eclipse directory.

### Sanity check (for Eclipse IDE):

Code should compile once checked out. (You may need to select the
project in your workspace, right-click to get the context Menu, then
Gradle->Refresh Gradle Project to get Eclipse to read `build.gradle` and
its dependencies.)
Eclipse may show some errors in `src/main/webapp` - but these can be ignored.

You may also need to set your Eclipse compiler settings to Java 17
compliance. In Eclipse, navigate Preferences->Java->Compiler to
change this if need be.

At this stage, running `./gradlew classes` (or the `classes` task from
the Gradle Tasks view) should compile the source code, after downloading
a large number of artifacts/dependencies. You should see an entry in the
rspace-web project called 'Project and External Dependencies' that lists
a large number (100+) of dependencies.

### Running fast unit tests with Gradle

To run just pure unit tests, run
```bash
./gradlew test -Denvironment=keepdbintact -DRSlog.level=ERROR -Dfast
```
which will not run any integration tests using DB connections etc.

### Resources on the classpath
The build filters the src/main/resources directory - only the directories listed in the
`sourceSets.main.resources` block of `build.gradle` are included in the classpath and the WAR.
See [Gradle.md](../Gradle.md#resource-filtering).


### Altering the build with different Gradle command-line options:

Package (builds web application):

```bash
./gradlew clean war
```
Run acceptance tests: 

```bash
./gradlew verify -Denvironment=drop-recreate-db
```

Launch in jetty:

```bash
./gradlew jettyRun -Denvironment=drop-recreate-db -DRS.devlogLevel=INFO \
-Dspring.profiles.active=run -DreactDevMode=true \
-Dlog4j2.configurationFile=log4j2-dev.xml
```

When `-DreactDevMode=true` is set, start the Vite dev server in a separate
terminal for Hot Module Replacement:
```bash
pnpm install --frozen-lockfile && pnpm run serve
```

With `-Denvironment=drop-recreate-db`, the DB is wiped and recreated from
the baseline schema, and RSpace applies the Liquibase changesets when it starts.

To keep the database intact, replace `-Denvironment=drop-recreate-db`
with `-Denvironment=keepdbintact`. This is useful when you want to keep
data between separate launches of RSpace, and also is faster to run.
Unless you're working on new database tables then
`-Denvironment=keepdbintact` is what you'll want to use most of the
time, to speed up launch times.

### Setting up test liquibase database

IF you're going to be working on the database, then cd to
`/src/main/resources/sqlUpdates/liquibaseConfig`, go and run the script:

```bash
mysql -uroot -p < createTestUpdateDB.sql
```

That will create additional database schema named `testLiquibaseUpdate`,
which can be used when testing database updates.

### Making changes to the database schema during development

See `src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md` for
documentation on this, including the database update process and how the
`run` / `dev-test` / `cloud` contexts are used.

### Coding standards

Please review [CodingStandards](CodingStandards.md).

### Code structure

#### Java code structure

The overall application architecture follows a standard Java Spring MVC
web application:
-   Controller classes are servlets configured by Spring to get HTTP
    requests from client.
-   Controllers call **service** classes that perform a unit of work,
    usually a single database transaction.
-   Service classes contain business logic and in turn call DAO (Data
    Access Object) classes, which interact with the database, perform
    queries etc.
-   The controller generally returns a path to a JSP to render the view,
    or a JSON object if called from Ajax, or direct to a response stream
    (e.g., for streaming images).
-   Entity classes that correspond to database tables live in this project
    under `com.researchspace.model` (they were previously the separate
    `rspace-core-model` github project, merged back in with full history).
-   The database stores all textual information stored in records,
    relations and meta-information. Images and media are stored in a
    FileStore and only their metadata is stored in the database.
    Programmatic access to the FileStore is through the File Store API.

#### UI and static content

All the static and UI content is stored `src/main/webapp` and includes JSP
pages, Javascript files and CSS stylesheets. We use Sitemesh
(http://wiki.sitemesh.org/display/sitemesh/Home) to provide a consistent
page structure and header/footer structure across the application. This
is a special JSP page in `/decorators/default.jsp` and configured via
`WEB-INF/decorators.xml`.

We are now using client side templates such as Mustache wherever
possible, Boostrap for CSS. We also make extensive use of JQuery,
JQuery UI, React.js. We use tinyMCE as a text editor.

### Launching from the embedded Jetty server in your IDE.

> While the following steps are specific to Eclipse, Intellij setup is almost identical.

This section is for setting up a Jetty launch from Eclipse. If just
using Gradle from the command line, then you can just use the 'Launch in jetty'
command listed above.

#### Jetty Eclipse Setup

You can configure to launch the application from jetty in Eclipse. It's
probably easier to Skype or do this F2F. The application will run at
http://localhost:8080/workspace. Here is a brief description.

1. Create a new 'External Tools configuration' that runs `./gradlew` in the
project directory with the `jettyRun` arguments listed above. The image below
shows the old Maven version of this configuration:

![](../images/extToolConfig.png)

Jetty runs in its own JVM, so `MAVEN_OPTS`-style settings don't reach it: put the debug agent in
`-PjettyJvmArgs` in the tool configuration's arguments, for example
`jettyRun -Denvironment=keepdbintact -Dspring.profiles.active=run
-PjettyJvmArgs='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=4000'`.

2. Create a new 'Debug' configuration of type 'External Java
application' and configure as follows:

![](../images/debugSetup.png)

You can configure other Gradle command lines to run through Eclipse as
well, e.g. tests

#### Launch RSpace from Eclipse

1. Make sure you have the Console View open in Eclipse, so you can view
error messages
2. In Eclipse, click Run -> External Tools -> Run As and run the configuration
you just set up.
3. You should see 'Listening for transport dt_socket at address: 4000'
in your console
4. Now 'Run->RunAs and choose the debug configuration from step 2
above.
5. Gradle should start building and launching the application
6. You should be able to login to localhost:8080
