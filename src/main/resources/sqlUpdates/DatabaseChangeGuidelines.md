Guidelines for database changes
-------------------------------

Checklist before adding a changeset. If you're not  sure about what is the right thing to do,
please ask rather than 'commit-and-hope'.

This document is written in Markdown format.

### Are you creating a new table? If so: 
- Update `DatabaseCleaner cleanup ()` to clean up the table during test runs. 
- Explicitly set charset and collation:  `character set utf8mb4 collate  utf8mb4_unicode_ci`.
  (As an example, see src/main/resources/sqlUpdates/changeLog-1.76.xml : ```<sql>alter table ClustermarketEquipment engine=InnoDB, CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;</sql>```)
  This is to overcome variations in collation between different OS/MariaDB/MySQL/ countries.
- Does it need an accompanying _AUD table to record revision history?   
- If it has FK references to any BaseRecord or User/Group table, we need to update UserDeletionManagerImpl to support the 'Delete User' use case.
- Is this a table that is defined as a Hibernate entity? Sometimes a 3rd party library (e.g. liquibase, spring-social, chemistry tables) requires its own table that is not created or managed by Hibernate. In this case you may need to edit scripts in the *maven* folder.
- Ensure your Java @Entity-annotated class implements Serializable. 
- If rows are likely to be read more often than written to, consider registering the class with Hibernate's second-level cache 
- Review Hibernate association mappings - even though @ManyToOne is eager by default, consider using a lazy fetch strategy if queries don't usually need data from the associated entity.
- consider adding javax.validation annotations on persisted fields. These are asserted by Hibernate before the
 data is flushed to the database, thus saving load on the database handling bad data.(this validation is only performed in Hibernate's pre-commit event handler).

### Are you adding a new column with FK reference? 
- You may need to update order of tables in `RealTransactionSpringTestBase cleanup ()`, or in  'Delete User' scenario of UserDeletionManagerImpl.

### Does a new column need indexing?
If adding an index to a  varchar column, consider length of index - it should be < 191 chars (see RSPAC-932)
 and ideally the minimal length needed to give good discrimination.

### Are you adding data to the table?
If so, make sure to make the id column set to auto-increment, and ensure the
Hibernate ID mapping is set to `@GeneratedValue(strategy=GenerationType.AUTO)` so that
ID generation in code and in the DB are compatible.

### Are you adding reference data for a new integration (App)?
There are various naming conventions, please [IntegrationAndAppNotes](integrationAndAppNotes.md).

### Have  you added a human-readable comment to each changeset?
- If altering a table structure ( add /remove change columns) is there an audit (_AUD) table
 that also needs to be changed in the same way?
- when adding/modifying audit table columns, remember that they don't generally have not-null constraints (apart from id/REV columns).
-  If you are adding such constraint make sure you know the consequences, and double check if constraint is actually applied on your local database when testing.

### Have you set a context? Values are:
- no value. Will run on all databases in all environments. E.g. static  reference data
- Suitable for adding lookup static data to all test/dev/production databases. E.g., permissions.
  - `run` for general schema changes - adding/removing columns/ tables /keys
  - `dev-test` change should only be applied during unit test runs - e.g., loading up test data.
  - `cloud` change should only be applied for Community version.

###  Should the new data be included in RSpace exports?

### Is it a Java update? 

I.e., are we calling a Java class to perform the update?
 This is only necessary if we're doing some complicated manipulation that is impractical to do in Liquibase.
 If so, then this means that the application and DB cannot be updated multiple versions at once, so we need to 
 mention this in the release notes.
 
 E.g., consider the following sequence:
 
 Version N - 1 - customer's current version
 
 Version N -> runs a liquibase update using Java/Hibernate mappings
 
 Version N + 1 -> adds a new property to  a table that was referenced by Hibernate in  Version N update.
 
If the customer tries to update directly from N-1 -> N+1, this will fail when performing update N, as the Hibernate mappings are for Version N+1 which refers to a property that does not exist in database when changesets from Version N are being executed.
The customer can remedy this by performing sequential updates, i.e. update to N before updating to N + 1. This must be mentioned in the release notes.

When deploying RSpace liquibase changes there are 3 scenarios to consider:
1. A new deployment on-prem where we run all changesets from 0.15 onwards on a database created from the baseline.sql (this is the case with pangolin8086 as well)
- to remedy java update problem the update could have a precondition stopping it from running on a new database. Java updates are meant to fix pre-existing production data, they are generally not necessary for newly created data.
2. A new deployment onto an AMI that has been cumulatively updated over time and might have some content on it, but only in sysadmin account. This covers both FB versions and also AWS customer deployments
- to remedy java update problem the AMI used for FB/AWS should be updated to start on version N, rather than N-1. 
3. Updating any customer already on version N. 

#### Considerations for Java updates w.r.t. large databases.

If the Java update is making changes to data, e.g. altering or processing field data, this can take a long time
 if the database is big, e.g. community.r.c. So
 
 * log progress if iterating in a loop, so that the person updating can see that the update is proceeeding OK and not hanging.
 * Consider using batch updates
 * Consider writing a specific database query to load only the necessary columns, rather than use Hibernate entities which
   load many other related tables and data.
   
Java updates should be tested first on community-test using a realistic database dump from community.r.c


## Using Liquibase 

We use Liquibase to apply schema updates to the database
Steps 1 - 3 are one-off operations the first time you set this up.
 
1. cd to `src/main/resources/sqlUpdates/liquibaseConfig`
2. Run the MySQL script createTestUpdateDB.sql with 2 arguments: the username and password of your MySQL admin user (who can grant permissions on new database).
E.g.,
 
    `mysql -v  -uroot -ppassword < createTestUpdateDB.sql`

This should create a database called **testLiquibaseUpdate** and populate it with your baseline schema from rs-dbbaseline-utf8 schema.

**Note:** if the database is already populated then drop it before running the above script. Login to mysql shell, then:
`drop database testLiquibaseUpdate;`
    
This database will resemble a production setup, in that we won't be deleting and recreating it all the time. 
*Don't delete this database or edit its schema other than through Liquibase or it will be a mess*.

3. Now run RSpace using the testLiquibaseUpdate database, and apply all existing changes that have been made since the baseline script was generated:


    mvn clean jetty:run -Denvironment=liquibase -DRS.logLevel=INFO \
                        -Dspring.profiles.active=run -Dliquibase.context=run,dev-test \
                        -Dlog4j2.configurationFile=log4j2-dev.xml


At this point, the testLiquibase update will be largely the same as that generated  from Hibernate mappings.
You can repeat this step 3  at any future time as well, so that when you run step 4 onwards, you will only see new changes made in the Hibernate mappings.


4. If you make, or think you've made changes to the database schema in development (via Hibernate annotations, or adding a non-transient property to an existing entity), continue to drop/recreate rspace database as usual by running
 maven with `-Denvironment=drop-recreate-db` flag set.

When you're ready to commit, (i.e., unit tests are passing against the altered schema in rspace database) run:
 
    mvn -e process-resources -Denvironment=keepdbintact -PgenerateDiff

(for Windows users: look into pom.xml generateDiff task, and remove timestamp, otherwise nothing is generated)

The command will compare your new rspace database with the testLiquibaseUpdate database and generate a time-stamped changeset file in folder *generatedDBChangeSets/* in your project. This changeset describes the changes that must be applied to convert the testLiquibase database schema to be the same as that generated from Hibernate mappings. There will likely be some existing differences relating to indexes etc.

This is just a temporary 'holding' folder for the diff output - the _diff_ tool may not always 'guess' the right changes to make, so they need to be reviewed. They're not put in SVN (set contents of this folder to 'SVN ignore' and can deleted once you've reviewed them.

You may  also see other changes made by other developers that have already been applied 
 to kudu, you can ignore these - just look for your own changes.

5. Review these proposed changes, and edit if necessary. Changes that need to be made are :

* Add a comment describing what the change is.
* For schema changes, add `context=run` attribute - this enables the change to be applied only at runtime.
* If you're creating a table make sure you applied steps described at the beginning of this doc (set engine to InnoDB / update RealTransactionSpringTestBase / review need for AUD table)
* (for Windows users only) double check that names are properly capitalized, as MySQL is case-sensitive on Unix-based systems 

E.g.,

    <changeSet author="radams (generated)" id="1390843344549-1" context="run">
        <comment>Add PasswordChange tracker table</comment>
        <createTable tableName="UserPasswordChange">
            <column name="id" type="BIGINT(19)">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <sql>alter table UserPasswordChange engine=InnoDB;</sql>
    </changeset>

In  addition you might want to merge some of the new changesets together - the auto-generated changesets are very fine-grained.


Then copy the contents of the changeset  and append them into the current version's changeset file in *src/main/resources/sqlUpdates/changeLog-xxxx.xml*

*DON'T EDIT EXISTING CHANGESETS!!!!!* Liquibase uses checksums to determine if changesets have altered and complains - it cannot tell if the database is inconsistent or if it's just an altered changeset.

*If you're not sure what changes to include, then please seek advice before committing the changes to Git.*


Now, run:
 
    mvn -e test -Denvironment=keepdbintact -PtestLiquibase

This will attempt to apply new liquibase changes to the testLiquibaseUpdate and run JUnit tests.
All the updates are applied via Spring at startup - since all database access in both tests and the application is through Spring beans, this should work fine.

4. After they pass, it's OK to commit. If the tests fail, read the maven output to see what went wrong, edit the liquibase changesets that didn't run and try again. Don't edit changesets that completed successfully - Liquibase might try to run them again. 

5. IF you've written some JUnit tests that extend from RealTRansactionSpringTestBase (i.e., test  cases that run real transactions) then remember to add to the 'cleanUp' method the table name 
 in the list of tables to delete after a test run - this keeps the database clean between test runs.
 The order in which rows are removed is important, to avoid FK constraint errors. 

## If there is a problem...

*Problem 1 - checksum problems*
When testing the changeset, if any of the changesets have changed since they were applied,
this may be an error, but can be ignored here by running:
 
    update DATABASECHANGELOG set MD5SUM=NULL;

from an SQL prompt on the testLiquibaseUpdate database. This prevents liquibase from comparing the 
checksums of the changesets and it just sets in the new value.

*If all else fails*
Delete your testLiquibaseUpdate database and recreate:

    drop database testLiquibaseUpdate;
    create database testLiquibaseUpdate collate 'utf8mb4_unicode_ci';

and reimport from the baseline in `src/main/resources/sqlUpdates/liquibaseConfig`:

    mysql -urspacedbuser -prspacedbpwd --database testLiquibaseUpdate < ecat5dev-baseline2014-1-24.sql

Now try  running 

    mvn -e test   -Denvironment=keepdbintact -PtestLiquibase

again.

*Debugging liquibase changesets*

If you get a liquibase error that you need to fix, it might be faster to use the command-line client rather
 than running the application or `mvn test` each time to test a new change.
 
 E.g. download liquibase ( the same version as is declared in pom.xml) and run e.g.
 
    ./liquibase --changeLogFile=/path/to/rspace/src/main/resources/sqlUpdates/changeLog-0.25.xml --username=rspacedbuser --password=rspacedbpwd --url=jdbc:mysql://localhost:3306/rspace  --contexts=dev-test update
 
You'll need to add the jdbc connector.jar into the lib/ folder of the liquibase install folder to make this work.
Some links in your changesets may need to be temporarily altered to absolute paths to make this work.

### After you've committed....

5. Jenkins will apply Liquibase  changes each evening into its own 'testLiquibaseUpdate' database.
6. If the database update run passes on Jenkins, it will build for the staging server.

In this way, the staging application at $STAGING will only update if database changes have been integrated successfully.
