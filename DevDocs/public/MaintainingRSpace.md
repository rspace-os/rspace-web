This document explains how to perform updates on an RSpace server that is already installed and running.

Application updates
------------------

Updating RSpace is a simple process of replacing the current ROOT.war file in Tomcat with the new version
 and restarting the server. 
 
New versions of RSpace are put on our download server https://operations.researchspace.com/software/rspace/rspace-%VERSION%.zip.

You can find the latest version in the file `latestVersion.txt`

```
    wget --user=USERNAME --ask-password https://operations.researchspace.com/software/rspace/latestVersion.txt
```

On a Linux server you can get the new distribution as follows. Credentials will be supplied to you by RSpace.

```
 wget --user=USERNAME --ask-password https://operations.researchspace.com/software/rspace/rspace-%VERSION%.zip
 unzip rspace-%VERSION%.zip 
```
## RSpace version numbers

RSpace releases a new feature release every few weeks, updating the minor version each time. E.g 1.61 -> 1.62
For each feature release, we may make patch releases with bug-fixes, e.g. 1.61.1, 1.61.2 etc.

You can usually, but not always, update multiple feature versions at once - e.g. updating from 1.70.0 to 1.73.3. Sometimes this is not possible, and you have to update via intermediate versions - the 'update critical path':

### Update critical path

1.51 -> 1.53.10 -> 1.59.2 -> 1.60.3 -> 1.69.35 -> 1.69.39 -> 1.69.54 -> 1.73.3 -> latest

For example, if your current version is 1.58.x, and you want to update to the latest version 
you would first upgrade to 1.59.2, then 1.60.3, then 1.69.x etc. In this way data integrity is safely maintained.

### Things you need to know before updating

* The location of the RSpace `error.log` file, for troubleshooting. These  are defined by `logging.dir` property in `deployment.properties`. If not set, then the  default is Tomcat working directory.
* MySQL connection details for the RSpace database: You can get the connection details for user, password and database name from the properties `jdbc.username`, `jdbc.password` and  the final part of the path defined in `jdbc.url` in `deployment.properties`.
* How to set up a maintenance downtime window: https://researchspace.helpdocs.io/app/content/article/b6w17h3sk7
 
### Preparatory work
 1. Obtain the latest release, unzip and read the Changelog file (a file named Changelog-1.62-to-1.63.md) or similar.
 In the Changelog will be guidance on any mandatory work required before updating, as well as guidance on the migration path if you attempting to update more than one version at a time, e.g. updating from 1.61 or earlier to 1.63. If you are updating multiple versions, and you have to update to an earlier version first, download that and  perform updates in sequence, earliest version first.
 2. If anything is unclear, ask ResearchSpace support for advice.
 3. Once you are ready to update, set up a maintenance downtime using the RSpace web interface System->Maintenance. Typically 10 minutes is ample time.
 
### The update
The exact commands may be different on your system depending on the version of Tomcat and its installation location.

 3. Shutdown Tomcat. E.g. `sudo systemctl tomcat8 stop`
 4. Backup your database. 
 
    `mysqldump -u$DBUSER -p$DBPWD --database $DB > RS_oldbackup.sql`
    
 5. Make a copy of old web application
    `cp -r $TOMCAT_HOME/webapps/ROOT   /backup/location/for/webapp`
  and remove old web application from webapps:
    `rm -rf $TOMCAT_HOME/webapps/ROOT*`
 6. Copy researchspace-VERSION.war to webapps
    `cp researchspace-VERSION.war $TOMCAT_HOME/webapps/ROOT.war`
 7. Restart Tomcat
   `sudo /etc/init.d/tomcat8 start` or `sudo service tomcat8 start`.
 8. Check log files and your URL for activity. Where is the log file? RSpace log files are written to the  location specified by the `logging.dir` property. By default this will be set to the current working directory of the Tomcat process, or can be overriden in deployment.properties file (see Logging section).
 
    `tail -f $RSpaceLogFile`
    
If installation is successful, you should see a line like:

    INFO \[-pool-2-thread-1\] 21 Apr 2014 12:34:13,044 - SanityChecker.onAppStartup(52) | Sanity check run: ALL_OK=true

and accessing RSpace from `curl -v localhost:8080/login` should return a 200 response.