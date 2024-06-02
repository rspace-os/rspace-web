This was done in the context of RSPAC-2660

There are actions which will run only once (when an RSpace instance is first created) and others that will run on app
startup. I needed a class to update the user's default folder structure for existing RSpace instances in order to give
all users new folders for sharing snippets. My class has to call

```
groupManager.createSharedCommunalGroupFolders
```

The purpose of this is to create a <groupname>_snippets_shared folder for each group.

### 1. The snippets shared folder that belongs to a group:

(The update mechanism runs on EVERY app startup)
See:

```
GroupSharedSnippetsFolderAppInitialiser
```

This is then made a bean in BaseConfig under the name of

```
sharedSnippetsFolderCreator()
```

ProductionConfig (prod profile) and TestAppConfig(run profile) (used in dev profile, NOT IN TESTS!) now add this to a
list of inits - see

```
GlobalInitManager.setApplicationInitialisors() 
```

For tests that startup a Spring container, RSDevConfig (dev profile) sets up
GlobalInitManager.setApplicationInitialisors() AND IT DOES NOT populate with the same list of beans that the real config
classes do. (It does not add GroupSharedSnippetsFolderAppInitialiser)

There are two automated tests for APP startup (note - these must run in a unique Integration test phase, else we get
multiple Spring Containers running with bean clashes. See the pom file for how this is done, we exclude tests that use
the run and prod profiles from the standard test and Integration test runs as these all use the dev profile. Then we
have a special execution for the run and prod profile tests):

```
<execution> <!-- for 'dev' profile IT tests -->
						<id>integration-tests</id>
						<phase>integration-test</phase>
						<goals>
							<goal>test</goal>
						</goals>
						<configuration>
							<includes>
								<include>**/*IT.java</include>
							</includes>
							<excludes>
								<!-- run/prod tests  -->
								<exclude>**/RunConfigWiringTestIT.java</exclude>
								<exclude>**/SysAdminConfigControllerMVCIT.java</exclude>
								<exclude>**/SysAdminSupportControllerMVCIT.java</exclude>
								<!-- other problematic tests -->
								<exclude>**/ExportApiControllerMVCIT.java</exclude>
								<exclude>**/CreateMissingUserFoldersForLabAdminsAndPIsIT.java</exclude>
							</excludes>
						</configuration>
					</execution>
					<execution> <!-- for 'run' profile IT tests -->
						<id>integration-tests-run</id>
						<phase>integration-test</phase>
						<goals>
							<goal>test</goal>
						</goals>
						<configuration>
							<includes>
								<include>**/RunConfigWiringTestIT.java</include>
								<include>**/SysAdminConfigControllerMVCIT.java</include>
								<include>**/SysAdminSupportControllerMVCIT.java</include>
							</includes>
						</configuration>
					</execution>
```

RunConfigWiringTestIT and ProdConfigWiringTest. ProdConfigWiringTest does not run as part of the standard test
executions - see the pom file. These two tests use the 'run' and 'prod' profiles and therefore can be used to test the
real set of initialiser beans
(such as GroupSharedSnippetsFolderAppInitialiser) that actually run on a real app startup.

GroupSharedSnippetsFolderAppInitialiser is not present in the test context on App startup for the non Integration unit
tests that use the 'dev' profile - this is compensated by the MockFolderStructure class which actually explicitly
calls :

```
DefaultUserFolderCreator folderCreator = new DefaultUserFolderCreator(rfac, permFac, folderDao, utils, folderManagerMock);
return folderCreator.initStandardFolderStructure(subject, root);
```

Lifecycle:
Note that GroupSharedSnippetsFolderAppInitialiser extends

```
AbstractAppInitializor
```

and this has a hook

```
onAppStartup
```

which runs on every startup. NOTE: it also has a hook that runs only once:

```
onInitialAppDeployment
```

### 2. The default set of folders given to a user

The class DefaultUserFolderCreator sets up user folders. I want new folders for each user that will hold shared snippets
and I then want the snippets_shared_labgroup folder to add the <groupname>_snippets_shared created by
GroupSharedSnippetsFolderAppInitialiser as a child.

DefaultUserFolderCreator will run on FIRST time an RSpace runs: (use new db profile to see this in action locally).
However, there are also 14 Classes which call the init() method of

```
AbstractContentInitializer
```

that in turn goes on to call DefaultUserFolderCreator.

DefaultUserFolderCreator is registered as a Bean in BaseConfig as

```
userContentCreator()
```

And then called in AbstractContentInitializer init()

```
folders = userContentCreator.initStandardFolderStructure(user, rootForUser);
```

There are two concrete classes for which extends AbstractContentInitializer,

```
ProdContentInitializerManager
```

and

```
ContentInitializerForDevRunManager
```

The latter is the one we run in a standard local RSpace run, the former is the one that gets deployed (not sure if
pangolin 86 etc is prod profile or not). We can run prod locally just by using prod profile. Prod is also used on AWS
instances if we deploy from a branch.

ProdContentInitializerManager is tested by the Integration test:

```
ProdContentInitializerTestIT
```

Therefore we do have tests for our DefaultUserFolderCreator being used in an initial app startup (ie - only the very
first time).

The AbstractContentInitializer init method if called in 14 places!! One of these is a post login hook:
PostLoginHandlerImpl

```
private InitializedContent initializeUserContent(User user) {
   if (!user.isContentInitialized()) {
      return contentInitializer.init(user.getId());
   }
   return null;
}
```

However, the above is only ever called on FIRST user login.

I decided to add my update mechanism for user content into PostLoginHandlerImpl on EVERY user login: because the updates
need to done AS the user and I did not want to slow down RSpace startup any further by having to list all Groups and
users as sysadmin and then login as each user to do the updates. See below for the update mechanism

### A Mechanism to update user content for existing users

I created the new class

```
UserContentUpdater
```

Note that new content added here for existing users should ALSO be created in DefaultUserFolderCreator. In
UserContentUpdater there will be checks to ensure that the content is only created if it does not already exist, whereas
in DefaultUserFolderCreator the content is created inside an action that only ever runs on first startup of an RSpace
instance and therefore does no checking to see if the folders are needed. To date, UserContentUpdater is called in
PostLoginHandlerImpl for EVERY login

```
handlePostLogin()
```

There could be a problem if UserContentUpdater were called during Application startup instead of on Post User Login.
This is because it calls a method which creates the user's SNIPPETS/SHARED folder. We could get into a race condition
with the GroupManagerImpl bean which create the Group-wide snippets shared folder and then looks for individual user's
SNIPPETS/SHARED folders to save the group wide folder into as a child. (This is another reason why I chose the
postloginhandler to run UserContentUpdater).

### Current scenarios for SNIPPETS

1. New RSPace instance. On first APP startup, Init method of AbstractContentInitializer will create SNIPPETS and then
   also SNIPPETS/SHARED folder. Subsequently the GroupManagerImpl will create the Group-wide shared folder and save it
   as a child to each group member's SNIPPETS/SHARED/LabGroups folder.

2. Existing RSPACE instance. On App startup, GroupManagerImpl is called by

```
GroupSharedSnippetsFolderAppInitialiser
```

GroupSharedSnippetsFolderAppInitialiser invokes GroupManagerImpl.

```
createSharedCommunalGroupFolders
```

on every group in the RSpace instance. GroupManagerImpl checks whether a Group has a group-wide shared snippets folder
set and if it does not, then it creates one. It then checks for each USER in the Group if that user has a
SNIPPETS/SHARED/LabGroup folder and if it does it saves the group-wide shared snippets folder inside that as a child.

3. Existing RSpace. On user login, Postloginhandler calls UserContentUpdater. This then calls DefaultUserFolderCreator
   to create the SNIPPETS/SHARED/ folder (and subfolders) if they have not already been created. The code then checks to
   see if there is a group-wide shared snippets folder and if there is it saves that as a child of
   SNIPPETS/SHARED/LabGroup.