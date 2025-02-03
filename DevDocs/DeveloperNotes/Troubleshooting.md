# Troubleshooting

Here are some notes for investigating bugs/ problems relating to RSpace updates.

## Problems with liquibase updates

### Scenario

Version X was released some time ago and developers are working on the version X+1.
A customer reports problems with the liquibase updates when updating from the version
X-1 to X.

### Reproducing on localhost:

1. Checkout version X-1 from Git
2. Drop your `rspace` database, and recreate.
3. cd into src/main/resources/sqlUpdates/liquibaseConfig and run the script
   `mysql -urspacedbuser -p < rs-dbbaseline-utf8.sql` which will populate the DB
   with the baseline tables.
4. Launch with `mvn clean jetty:run -Denvironment=keepdbintact -Dlog4j2.configurationFile=log4j2-dev.xml -DRS.logLevel=INFO -Dspring.profiles.active=run -Dliquibase.context=run`.
   Note `-Dliquibase.context=run`, which will execute DB scripts from liquibase.
5. Now reproduce the state of the DB to reproduce the bug.
6. Now stop, checkout version X , relaunch as in step 4 - try to reproduce the error.

### After identifying a fix

Repeat relaunch version X - it should now run DB updates OK.

## Explanations for some errors that can be confusing

### Too much data in a single document field:
***The data limit for a document field is 200kb for local development
using Jetty and 2MB for a deployed RSpace instance using Tomcat.***

You can configure the ***local*** form size using the system property:
```
-Dorg.eclipse.jetty.server.Request.maxFormContentSize=2000000 (which gives 2MB, like Tomcat)
```

If a document has too much data in a single field of the document it will fail on autosave (autosaveField
will be visible as an ajax call) and as a result there will be a redirect to /workspace which will then try to serialise
several hibernate entities (StructuredDocument,User,SearchResults and many others).
The attempted serialisation will fail with a JsonMappingException and an error dialog GUI will be
shown in the UI that shows a large json structure. Sometimes, this GUI will be blank
and will only contain the message 'Errors null'. RSpace will log the following
```
[WARNING] /workspace/editor/structuredDocument/ajax/autosaveField org.eclipse.jetty.http.BadMessageException: 400: Unable to parse form content
```
and also:
```
[WARNING] /workspace com.fasterxml.jackson.databind.JsonMappingException: Infinite recursion (StackOverflowError) (through reference chain:....etc
```

Also note, if the limit is
exceeded on a deployed RSpace, the error is different - the UI fails to send
*ANY* data when autosave is attempted and the user sees an error message that required
parameter 'dataView' is missing.
