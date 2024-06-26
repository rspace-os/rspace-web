# Getting started with RSpace source code

These instructions are for anyone wanting to run RSpace from source code, on their local machine.

## Initial Setup

### Recommended hardware

For production setup we recommend a Linux-based server with at least 8Gb RAM. More details: 
https://documentation.researchspace.com/article/q5dl9e6oz6-system-requirements-for-research-space

For development you should be able to run all the code and tests fine with Linux/MacOS.  

### Install required software
-   Install Java JDK17, use OpenJDK rather than Oracle.
-   Install MariaDB 10.3.39, or later (MariaDB 11.3 is used fine on dockerized RSpace version)

Historically we were running RSpace on MySQL 5.7 version, so some docs still mention it, but you should go with MariaDB.
Database installation can be skipped if your only goal is to compile/build the project.

### Recommended software

Install [Maven 3](https://maven.apache.org/download.cgi) (>=3.8.1).

Alternatively, you can run builds using the `./mvnw` (Unix/Linux/Mac) or `./mvnw.cmd` (Windows) which installs Maven for you
if you don't have it already.

### Set up Maven toolchain

This enables us to run builds & tests using different JDK versions.
Create a file called `toolchains.xml` in your `$HOME/.m2` folder.

Add the following, replacing the value of `jdkHome` with the absolute
path to your java installation.

```xml
<toolchains>
  <!-- JDK toolchains -->
   <toolchain>
     <type>jdk</type>
     <provides>
       <version>17</version>
       <vendor>openjdk</vendor>
     </provides>
     <configuration>
       <jdkHome>/usr/lib/jvm/java-17-openjdk-amd64</jdkHome>   <!-- path to your local installation -->
     </configuration>
   </toolchain>
</toolchains>
```

**NOTE:** to use a specific toolchain with maven specify the java properties -Djava-vendor=<vendor> -Djava-version=<version>

### Check out and compile the project

Current location of the codebase is https://github.com/rspace-os/rspace-web

It's best to use a Git client to download and update source code.
Alternatively, for one-off installation, you can download project directly from the page as a zip package.

#### Download required non-public RSpace dependencies (temporary solution before official open-source version)

Not all of RSpace dependencies are yet publicly available, you need to download an additional package
of dependencies for your release from https://github.com/rspace-os/rspace-web/releases page. 

The .zip archive contains folder `rspace_os_local_dependencies` with dependencies from com.researchspace and com.github.rspace-os namespace.
The archive need to be unpacked into location that maven will be able to read from, e.g. into your `.m2` folder.
Note this location, as it will be required in next step.

#### Configuring local repository for required non-public RSpace dependencies (temporary solution before official open-source version)

In your `.m2` home folder create a file called `settings.xml` (if it's not there yet) and insert this content.
E.g. on a Mac this would be in `Users/myusername/.m2/settings.xml`.
If you already have a `settings.xml`, add the 'profile' or 'profiles' tag from the fragment above into your file, and save it.

```xml
<settings>
  <profiles>
    <profile>
      <id>rspacelocalrepo</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <repositories>
        <repository>
          <id>rspace-os-local-dependencies-repo</id>
          <url>file://path-to-unzipped-dependencies-folder>/rspace_os_local_dependencies</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
</settings>
```

Then update repository url in the fragment above so it point to the `rspace_os_local_dependencies` folder you unzipped before. 

#### Sanity check

As the 1st goal, you should be able to compile the java side of the code. 
In command line, navigate to RSpace project top dir and run `mvn clean compile`.

Note that on first run it'll take a while (a few minutes) to download artifacts/dependencies 
from configured maven/jitpack repositories into your local maven repository.

If you get BUILD FAILURE message pointing to some missing jar, that could be intermittent issue
so re-run mvn command with -U option which forces re-download on missing dependencies 
(i.e. run `mvn -U clean compile`).

Eventually you should see the BUILD SUCCESS message, which means maven was able to resolve
all code dependencies and compile the code.

### (Optional) Build RSpace application package

The application .war file can be built with the following maven command:  

```
mvn clean package -DskipTests=true -DgenerateReactDist=clean -DrenameResourcesMD5=true \
  -Denvironment=prodRelease -Dspring.profiles.active=prod -DRS.logLevel=WARN -Ddeployment=production \
  -Djava-version=17 -Djava-vendor=openjdk
```

You can also check top-level Jenkinsfile file to see how internal tests builds are created by
ResearchSpace dev team (check 'Build feature branch' stage script).  

### Set up MariaDB/MySQL database

#### MariaDB/MySQL initialisation

Take these steps if you do not have MariaDB/MySQL or have a fresh installation of MariaDB/MySQL.

```bash
# These steps are specific to Linux and MySQL5.7, adapt as needed

# 1. Install the database package (your package manager might be apt, dnf, yum, ...)
nix-env --install mysql57

# 2. Initialise the database before starting the systemd service
mysqld --initialize

# 3. Change the temporary password created in the above step to "password" (or anything you like)
mysqladmin --user=root --password="PASSWORD-FROM-STEP-2" password "password"

# 4. Start the systemd service
mysqld

# 5. By default, MySQL listens on 0.0.0.0, for security reasons let's replace this with localhost
echo "
[mysqld]
bind-address = 127.0.0.1
" > ~/.my.cnf
```

Finally, there may be a configuration change to set up - in MySQL prompt run:

       SELECT @@sql_mode;

If the result contains 'ONLY_FULL_GROUP_BY' option, that option needs to be removed,
otherwise some pages will fail to load (e.g. /system page for sysadmmin).
That option doesn't seem to be present on MariaDB 10.3.39, so if you run that, you can move on.

The sql_mode can be overridden for a single session by running the following command in MySQL prompt, e.g.

       set global sql_mode = "STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION"

You can make this permanent / applied on startup by adding a line to a MySQL configuration file (e.g. /etc/mysql/my.cnf on Linux, you may need to create this file):

       sql_mode = STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION

On windows, these instructions should work for Mysql 5.7X. After installation locate the hidden folder PROGRAMDATA : 'alt + r' (run) -> search for %PROGRAMDATA%.
Then look for the file my.ini inside the MYSQL install - mine was at "C:\ProgramData\MySQL\MySQL Server 5.7\my.ini". **This file already contains an entry for
sql-mode** - locate and edit that entry, do not just attempt to add a new entry. After editing my.ini, restart the Mysql service and the changes can be queried
as mysqladmin.

After adding/updating the file and restarting mysql confirm that `SELECT @@sql_mode;` no longer contains the 'ONLY_FULL_GROUP_BY' option.

#### RSpace table setup

1.  Make sure MariaDB/MySQL is installed and running on your machine.
2.  Set up an 'rspacedbuser' user and 'rspace' database on your MariaDB/MySQL using this command:

```bash
mysql -u "root" -p"password" -e "
  CREATE USER 'rspacedbuser'@'127.0.0.1' IDENTIFIED BY 'rspacedbpwd';
  CREATE DATABASE rspace collate 'utf8mb4_unicode_ci';
  GRANT ALL ON rspace.* TO 'rspacedbuser'@'127.0.0.1';
"
```
**NOTE:** on FIRST startup of Rspace with an empty rspace, don't skip the tests else liquibase fails for unknown reasons;
i.e. do not have `-Dmaven.test.skip=true -Dmaven.site.skip=true -Dmaven.javadoc.skip=true`
in your launch config

**NOTE:** depending on OS and DB used, your may need to change the username in creation/grant commands 
from `'rspacedbuser'@'127.0.0.1'` to `'rspacedbuser'@'localhost'`. 
You will know if running the tests/app gets you db authentication error for user `'rspacedbuser'@'localhost'`

**NOTE:** if you subsequently drop the rspace DB, the rspacedbuser user will stay; do not try to recreate the user.

At the end of this, you should be able to connect to the rspace database from
your command line. with `bash mysql -urspacedbuser -prspacedbpwd rspace`.

'rspace' database is used for running acceptance tests and the application on localhost.

### Set up RSpace FileStore location

Add the location of a base folder for the file store as an environment
variable (or in a shell login script). Media files such as videos,
images etc., are stored outside the DB in the file store. (Optional)
e.g., `export RS_FILE_BASE=~/.researchspaceFStore` (**Note:** startup will fail
if this folder does not exist)

### Run full suit of unit tests (recommended)

To run tests: `mvn clean test -Denvironment=drop-recreate-db` will drop
and recreate the database tables and run JUnit tests. This will run all
unit and Spring integration tests and may take over 10 mins to run.
If these pass, or there are just a few failures, then your setup is basically fine.

### Running fast tests
This just runs plain Junit tests and is much faster to run:

`mvn clean test -Dfast=true`

## Running RSpace server

### Launch RSpace with Maven and Jetty

When starting RSpace for the first time use the following command:

```bash
mvn clean jetty:run -Denvironment=drop-recreate-db -DRS.devlogLevel=INFO \
-Dspring.profiles.active=run -Dliquibase.context=dev-test \
-DgenerateReactDist -Dlog4j2.configurationFile=log4j2-dev.xml
```
This command clears and sets up the database, so it takes a few minutes
to run. This command also applies liquibase changes that are configured to
run with the 'dev-test' profile (liquibase changes that are only configured
to run with the 'run' profile will only run with a 'prod' launch config)

**NOTE:** dont skip the tests compilation when cleaning the DB this way else existing data will not be deleted.
- you can skip test compilation phase by adding -Dmaven.test.skip=true
- you may find this useful if you want to apply new liquibase updates without deleting existing data.

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
following to your mvn launch command:

Enterprise Standalone: this is the default, no extra configuration is needed.

Enterprise Institutional SSO: `-Ddeployment.sso.type=TEST -Dmock.remote.username=user -Ddeployment.standalone=false`
(where the username is for a user already in the database)

Community: `-Ddeployment.cloud=true`

#### Configure logging levels for debugging purposes

Adding the following to the command line:

`-Dspring.logLevel=[DEBUG, WARN,INFO,ERROR or FATAL]`
`-Dhibernate.logLevel=[DEBUG, WARN,INFO,ERROR or FATAL]`
`-DRS.devlogLevel=[DEBUG, WARN,INFO,ERROR or FATAL]`

alters the logging verbosity for various frameworks or RSpace code.

#### Set deployment properties through command line

`-Djdbc.url=jdbc:mysql://localhost:3306/databasename`
specifies a specific database different from the default, rspace

`-DRS_FILE_BASE=/path/toFileStore`
overrides the default filestore location.

`-Drs.httpcache.enabled=true`
true/false will enable/disable the ability to set HTTP response headers
to cache.

#### Running RSpace in full production mode locally

This describes how to run RSpace in full production mode locally from
Eclipse. In most cases you don't need to do this unless you are
developing features that are stubbed in development version.

##### Setting up Aspose document conversion (for MSOffice files preview)

**NOTE:** while the following steps are for Eclipse, Intellij setup is almost identical

In production, we use 3rd party library, Aspose, to preview MSOffice
documents. This is installed as a separate product and is not available
in a development or test environment.

There are several options to set up a dummy Aspose conversion service
when running in 'run' profile, i.e. where RSpace is not expecting Aspose
converter to be available. The following 3 properties should be set to
paths that point to files, as if generated by Aspose converter. E.g add
these to your launch configuration (paths are for Windows). Anywhere conversion
to png, doc or pdf is required these files will be returned.

`-Dsample.pdf=C:\\path\\to\\workspace\\rspace-web\\src\\test\\resources\\TestResources\\smartscotland3.pdf`
`-Dsample.doc=C:\\path\\to\\workspace\\rspace-web\\src\\test\\resources\\TestResources\\letterlegal5.doc`
`-Dsample.png=C:\\path\\to\\workspace\\rspace-web\\src\\test\\resources\\TestResources\\biggerLogo.png`

##### Compiling Aspose converter

This converts documents to/from Word format and in production runs as a
separate standalone app.

- Checkout the Aspose project from
  `https://github.com/ResearchSpace-ELN/aspose-documentconversion`
- Build the standalone converter using `mvn clean package -Pdist`
- This will put a large jar, probably called
  `aspose-app-[version].jar` (50Mb) in the target folder of the project.
- Ensure this jar is executable ( e.g.
  `chmod 755 aspose-app-[version].jar` on Unix )
- Test it runs OK using `java -jar aspose-app-[version].jar -v`
- Now run RSpace using the following launch configuration:

```
mvn clean jetty:run -Denvironment=keepdbintact \
-Dliquibase.context=dev-test -Dspring.profiles.active=prod \
-DgenerateReactDist -Dlog4j2.configurationFile=log4j2-dev.xml \
-Daspose.license=/full/path/to/aspose-documentconversion/Aspose-Total-Java.lic \
-Daspose.logfile=/full/path/to/any/file/logfile.txt -DRS.logLevel=INFO
# Daspose.app=/full/path/to/aspose-documentconversion/target/aspose-app[version].jar
```
altering paths of the 'aspose' properties to point to files in the
Aspose converter project.

The 1st time you run this you might need to run with
`-Denvironment=drop-recreate-db` set to clean the DB.

### Developer docs on specific subjects

Developer docs on specific subjects are as follows:

| Document                                                              | Explanation |
| ----------------------------------------------------------------------|-------------|
|[Java.md](Java.md)                                         | How to setup your machine for back-end development. |
|[CodingStandards.md](CodingStandards.md)                   | Our conventions. |
|[Logging.md](../Logging.md)                                | How to configure and use logging. |
|[Caching.md](../Caching.md)                                | About server-side and client-side caching. |
|[Transactions.md](../Transactions.md)                      | Set up of how transaction boundaries are defined. |
|[AddingANewMessageType.md](../AddingANewMessageType.md)    | How to add a new message type. |
|[SecureConnectionConfig.md](../SecureConnectionConfig.md)  | How to set up Jetty to use HTTPS. |
|[SecurityAndPermissions.md](../SecurityAndPermissions.md)  | How permissions and roles work. |
