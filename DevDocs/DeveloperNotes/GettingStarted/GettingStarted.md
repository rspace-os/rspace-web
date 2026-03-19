# Getting started with RSpace source code

These instructions are for anyone wanting to run RSpace from source code, on their local machine. **Do NOT use these instructions if you want to run RSpace on a production server or even a pilot / trial server, use rspace-docker for this.**

## Initial Setup

### Recommended hardware

For production setup we recommend a Linux-based server with at least 8GB RAM. More details: 
https://documentation.researchspace.com/article/q5dl9e6oz6-system-requirements-for-research-space

For development you should be able to run all the code and tests fine with Linux/MacOS.  

### Install required software
Our build toolchain requires Java 17. Currently using a different Java version to run Maven is not supported because of [spotless not supporting the Maven toolchains plugin](https://github.com/diffplug/spotless/issues/1857).

-   Install Java JDK17 via [Adoptium](https://adoptium.net/temurin/releases/?version=17)
-   Optionally, install [jenv](https://github.com/jenv/jenv) to manage multiple versions of Java
-   Install MariaDB 10.6 or 10.11. If your OS pre-installs MariaDB 11.3 you can try it - it's used fine in the dockerized RSpace version. Later versions of MariaDB 11 are not tested yet and may have compatibility issues.

Historically we were running RSpace on MySQL database, so some docs may still mention MySQL, but go with MariaDB.
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
       <vendor>openjdk</vendor> <!-- Only vendor=openjdk is accepted at the moment, though it does not have to be actual OpenJDK -->
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

(if you are a member of ResearchSpace Dev Team, please clone our fork of the codebase located at https://github.com/ResearchSpace-ELN/rspace-web)

We recommend using a Git client to download and update the source code.

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
"
```
**NOTE:** depending on OS and DB used, your may need to change the username in creation/grant commands
from `'rspacedbuser'@'127.0.0.1'` to `'rspacedbuser'@'localhost'`. (Or you may need to just use `'rspacedbuser'` with no host)
You will know if running the tests/app gets you db authentication error for user `'rspacedbuser'@'localhost'`
or you see `'Access denied for user 'rspacedbuser'@'%' to database 'rspace'`

**NOTE:** if you subsequently drop the rspace DB, the rspacedbuser user will stay; do not try to recreate the user.

**NOTE:** on FIRST startup of Rspace with an empty rspace, don't skip the tests else liquibase fails for unknown reasons;
i.e. do not have `-Dmaven.test.skip=true -Dmaven.site.skip=true -Dmaven.javadoc.skip=true`
in your launch config

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

***(note - you may need to increase memory for NODE when doing `generateReactDist`
step below : set and export the environmental variable NODE_OPTIONS:
`export NODE_OPTIONS="--max-old-space-size=8192"`  in .zshrc file for OSX, or .bash_profile for linux)***

```bash
mvn clean jetty:run -Denvironment=drop-recreate-db -DRS.devlogLevel=INFO \
-Dspring.profiles.active=run -DgenerateReactDist \
-Dlog4j2.configurationFile=log4j2-dev.xml
```
This command clears and sets up the database, so it takes a bit longer to run. 

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
`-Djdbc.url=jdbc:mysql://localhost:3306/another_database -Djdbc.db=another_database -Djdbc.db.maven=another_database`
specifies to use a different database `another_database` at the url `jdbc:mysql://localhost:3306/another_database`.

`-DRS_FILE_BASE=/path/toFileStore`
overrides the default filestore location.

`-Drs.httpcache.enabled=true`
true/false will enable/disable the ability to set HTTP response headers
to cache.

#### Running RSpace in production mode locally

Run the usual `mvn jetty:run` command, just change the active spring profile to `prod`
i.e. pass a `-Dspring.profiles.active=prod` parameter.

Note that when running through jetty, the `defaultDeployment.properties` file is not used for some reason.
That means deployment properties that are not explicitly set in your `deployment.properties` file may have unexpected values. 

#### Compiling RSpace production mode package

Check the Jenkinsfile and mvn command run during "Build prodRelease-like package" stage:

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
