# Debugging the staging server remotely

> Note: While the following steps are Eclipse specific, Intellij setup is nearly identical

Eclipse can be configured to run in debug mode on the pangolin servers,
as follows:

ssh to $TEST, then cd to the relevant Tomcat; e.g.,
`cd /rspace/tomcats/rs_acceptance/bin`
Open `setenv.sh`, which looks like this:

```
JAVA_OPTS=" -Xms256m -Xmx512m
CATALINA_OPTS="-Djava.awt.headless=true -Dspring.profiles.active=prod -DRSRequest.log.file=/home/ecatsetups/tomcats/dev_ecat5/RSLogs/RequestLog.txt -DRS_FILE_BASE=/home/ecatsetups/dev_filestore"
```

add this line to javaopts for debugging
```
-Xdebug -Xrunjdwp:transport=dt_socket,address=8083,server=y,suspend=n
```
and add the commented line into the JAVA_OPTS definition, so that the
file now looks like this:

```bash
JAVA_OPTS="-Xms256m -Xmx512m -Xdebug -Xrunjdwp:transport=dt_socket,address=8083,server=y,suspend=n"
CATALINA_OPTS="-Djava.awt.headless=true -Dspring.profiles.active=prod -DRSRequest.log.file=/home/ecatsetups/tomcats/dev_ecat5/RSLogs/RequestLog.txt -DRS_FILE_BASE=/home/ecatsetups/dev_filestore"
```

Now, restart Tomcat. This will now emit debug information on port 8083

In Eclipse, create a new 'Remote java application' Debug configuration:

![](../images/kuduRemoteDebug.png)
and run it. Now, insert some breakpoints in your code, logon to the test server
and start using RSpace - Eclipse should stop at breakpoints as usual, allowing
you to step through code that is executing on the server!

You can also use this to debug production servers on AWS. See
https://ops.researchspace.com/globalId/SD3065

#### Points to note, and tidying up

- For this to work best, your code needs to be the same as is running
  on Jenkins.
- After you've finished, return setenv.sh to its original state.
  Run `netstat -tulpn` to identify the process id running on port
  8083, then kill it:
  ```bash
  kill --9 processId
  ```
  Then restart Tomcat.
