# Coding standards and conventions

A small team should not be encumbered by complex bureaucratic processes,
but there are a few conventions / coding practices that will help the
overall development effort, listed below.

## Git

- Every commit should have a brief comment included as to the nature
  of the changes. If the commit relates to a Jira issue, add in the
  issue number - Jenkins will create a link automatically in the
  Changelog.
- If you've made changes to Java or server-side code, ensure at least
  that JUnit 'fast' tests are passing on your local machine before
  committing.

## Input validation

- Validation of user-supplied input to be performed by the controller
  URL handler methods as early as possible in the method. Service
  methods should be able to assume that input is valid (e.g., a
  supposed number is indeed a number) and throw a runtime exception if
  this is not the case.

## Logging and Exception handling

- We should log all caught exceptions at level WARN or ERROR depending
  on severity.
- No empty catch blocks that suppress the exception. E.g., no code like
  ```
  try {
  // code that might throw exception
  } catch (Exception e) {} // exception squashed, code carries on regardless.
  ```
- All file handles or streams that are opened should be closed in a
  `finally` block, so we don't end up with lots of open streams if
  exceptions are thrown. We should use `try-with-resources` blocks where
  possible to minimize boiler-plate code.

## Testing
- Code coverage of JUnit tests should not decrease from one iteration
  to the next; code coverage can be inspected on
  [Jenkins](https://jenkins.researchspace.com/job/rspace-web/) or via a
  developer tool such as the [EclEmma](http://www.eclemma.org/) plugin for Eclipse.

### Unit tests and Integration tests

There are three sorts of tests that are run:
1. Regular JUnit classes that test a single class with no requirement
   for Hibernate or Spring beans to be loaded.
   - Run quickly.
   - Isolated from interactions with other classes using mocks/stubs etc.
2. Spring transactional unit tests that load Spring Context but
   rollback all transactions.
   - Run slower.
   - Test code that invokes Spring beans can be used.
   - All tests run within a transaction, so lazy-loading exceptions
     can be masked.
   - Code that depends on DB commits (e.g., caching, permissions,
     auditing, archiving, anything that listens to Hibernate
     postCommit events) will not work as expected.
3. Transactional tests that make real commits to the database.
   - Run slowest.
   - Transactions managed manually within test.
   - Most realistic way of testing behaviour (e.g., caching,
     permissions, auditing) __will__ work as expected.

In practice the two sorts of Spring tests do not seem to run well
together; there are possibly some problems with AOP transactional proxy
behaviour with the two types of test using the same Spring context causing
unreliable and hard-to-reproduce behaviour. So the transactional tests
are named with a name ending in 'IT', and run in the *Verify* Maven
phase, distinct from the non-transactional tests.

Spring 3.2 introduced WebMVC tests; the test `DashboardControllerMVCIT`
illustrates the setup for this. These test the full web stack of
filters,  except for JSP processing.

#### Spring config files

There are several Spring beans that differ between application and test
configurations. Currently, we have duplicated config files in
`src/test/resources` and `src/main/resources` so that both the application
and the tests run properly.

There are three profiles:
1. `dev` configuration for running tests. This is set automatically when
   you call `mvn test` or `mvn verify`, or run tests through eclipse.
2. `run` configuration for running the application on localhost. This is
   set automatically by default when you run the app through `mvn jetty:run`
   either from command line or Eclipse.
3. `prod` configuration for production environment. To switch this on,
   switch the line `<param-value>run</param-value>` in `web.xml` to
    `<param-value>prod</param-value>`. Or run from the command line
    with `-Dspring.profiles.active='prod'` argument.

##### Further detail

Spring profiles are defined in the package `com.axiope.service.cfg` and are
activated by the `spring.active.profiles` and `spring.profiles.default` java
system properties.

The idea is that bean wiring for different environments is set up
explicitly in one place.

`dev` profile for tests is set in Spring JUnit
base classes via the `@ActiveProfiles` annotation.

Ideally we would choose profile through a ~~D option in Maven to set
the Spring property in a property file~~ this would work for building a
war file but not for running in Jetty so we haven't implemented this yet.

#### Maven integration

Test categories 1 and 2 (from above) are bound to the regular Maven test
lifecycle phase. If these tests fail, the war package will not be
generated.

Test category 3 (from above) is bound to the integration-test phase,
run after _package_. If these tests fail, a war is generated, but the
build will be reported as a failure.

#### Writing category 3 acceptance tests

Test class should inherit from `RealTransactionSpringTestBase.java`,
which provides utility methods to run transactions and tidy up the
database afterwards.

The test class name should end with `IT` which is the convention for
defining `IntegrationTest` classes in Maven.

### Suggested convention for running tests:

- Run `mvn test -Dfast` during development; these should run relatively
quickly.
- Run `mvn verify` before committing; this will run all tests and is
what is run on Jenkins.

We can review this periodically to see how it works in practice.

#### Testing classes that are only loaded in prod or run profiles

Some classes are only wired in by Spring if a certain profile is active.
For running tests, the profile is `dev`, therefore, the classes aren't loaded.
So, how to test in this case?

The answer is to annotate your test class with one of the annotations
`@ProdProfileTestConfiguration` or `@RunProfileTestConfiguration` which
will configure your environment for that specific test.

There is a special profile 'prod-test' that is used for testing
production classes in a development environment, e.g., to test the
license server client without loading the full production environment.

#### Testing classes that are only loaded in certain product types

To run JUnit tests that depend on Spring wiring up beans correctly for
the 'Community' product variant, add the annotation `@CommunityTestContext`
to your test. __All__ tests in the class need to run in this configuration,
you cannot switch between product configurations within the same test class.

#### The suggested flowchart for deciding what unit tests to write

1. Are you testing a DAO class?
   Yes -> write a test, that extends `SpringTransactionalTest` (realistically,
   we're not going to mock Hibernate behaviour, and we do need to actually
   test our Hibernate mappings etc). All DAOs execute in a single
   transaction so SpringTransactionalTest is fine for this.
   No -> step 2.
2. Are you writing a unit test, or an integration test?
   Unit -> step 3.
   Integration -> step 4.
3. Does it have any external dependencies on web services, or other
   services that might not be available in test environment, or that might
   be slow, or are covered by unit tests elsewhere?
   Yes -> write plain JUnit, with Mockito mock objects to simulate
   collaborating classes (e.g., a Controller JUnit would mock Service
   classes).
   No -> Write plain JUnit (e.g., entity test classes, utility test classes,
   e.g., `DateUtilTest`).
4. Are you testing a Service?
   Yes -> step 5.
   No -> step 6.
5. Do you need real transactions that actually write to the DB? (Needed
   to test revision history, anything that depends on post-commit
   behaviour, or you want to test lazy loading, or a method that makes
   multiple DB calls, and you need them to occur in separate DB transactions).
   Yes -> extend `RealTransactionSpringTestBase` (e.g. `AuditManager2IT`).
   No -> extend `SpringTransactionalTest` (e.g. `RecordManagerTest`).
6. Are you testing a Controller?
   Yes -> write an MVC test extending `MVCTestBase`.
   No -> What are you testing???

## Documentation

We should aim to provide simple Javadoc, at least for:
- Service interface methods
- Non-trivial entity methods in particular with regard to permissible argument
  values and possible return values - i.e. the preconditions and postconditions.
  These should be tested in unit tests anyway.

## Internationalization

The main label translation file is `ApplicationResources.properties` file in `src/main/resources`.

There are also page-specific property files in `src/main/resources/bundles`;
these are registered as spring message bundles in `applicationContext-resources.xml`

To add a new property, add it to `ApplicationResources.properties` or a
file in the `bundles` folder. These message keys are loosely organised by
the page in which they appear.

### Usage in JSP pages

This is easiest, make sure the JSP `fmt: tag` library is accessible
```
<@ include file="/common/taglibs.jsp">
```
Access the message by its key as follows
```
<fmt:message key="menu.templates"/>
```
Alternatively you can use Spring's message tag which handles arguments more cleanly.
```
<spring:message code="group.created.success.nominate.msg1" arguments="${groupName},${principalEmail}"/>
```

### Writing Strings directly from a Controller.

An example of this is provided by the StructuredDocumentController class.

1.  Autowire MessageSourceUtils class.
2.  Call the message source where appropriate - there are various overloaded methods.
    e.g. `messageSourceUtils.getMessage("errors.required", new Object []{"Id"}));`

### From Javascript

For Javascript that is included in a JSP (and is therefore processed by
the JSP engine) we can probably use the same mechanism as for HTML
content in a JSP page described above.

For `.js` files that are loaded directly into the browser, there are
various options available including
http://code.google.com/p/jquery-i18n-properties/ - we need to
investigate and choose this.

## Secure coding practices

There are several areas of the application where security needs to be
maintained. See [GoogleDoc](https://docs.google.com/document/d/1\_QP94epZWxQj531JHlUAUagqypfYHaN9tKhsB6AVc2c/edit)

### Authentication

- An anonymous user should not be able to access functionality (see
  `security.xml` for role-based access to URLs).
- Protection of user login data (currently passwords are stored as a
  SHA-256 hash that is salted as well).
- Sign up page should enforce minimum username / password lengths.
- Failed logins should not indicate the reason for failure.
- Logging failed logins.
- Not logging passwords on login / sign-up / password change / etc.

### Browser caching

- The policy can be influenced by the `BrowserCacheAdviceInterceptor` class
- set `autocomplete=off` in form elements for forms that contain sensitive data.

### Validating user input.

We need to validate all user input at the Controller level to deal with
possibly malicious input.

This could include:
- Extremely large text lengths for text input.
- URL guessing to attempt to access unauthorised resources.
- Enormous sized file uploads or zip bombs (small zip files that
  expand to vast files).

### Access to authorised resources

Any services that access a resource should check permissions, that the
current subject has permission to view the resource. (E.g., by calling
PermissionUtils.isPermitted(BaseRecord))

### Securing the database

#### SQL injection attacks

We can easily prevent these by reviewing DAO code to ensure that all
queries use prepared statements, and we never put user input directly
into a DB query.

### Cross-site scripting attack prevention

JSPs and Javascript should escape any data that has been input by an end
user. Currently, tinyMCE escapes `<script>` tags.

- The `EscapeXMLREsolver` class is called automatically by JSP processors
and escapes HTML.
- Javascript code should call `RS.escapeHTML(text)` in `global.js`.

## Sending emails

There are various times we need to send emails; we use Velocity
templates for email templates so that the text is not encoded in Java.
The syntax is similar to JSPs, so it's straightforward to use.
Templates are kept in `src/main/resources/velocityTemplates`.

In dev and test profiles, we don't send real emails - a dummy email
implementation just logs the email content. In prod profile, real emails
are sent. Mail server configuration is set up in mail.properties.

## Code Formatting conventions

### Java
The Java codebase adheres to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
Code formatting is applied via the [Spotless Maven Plugin](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md).

The formatting is applied (if there are any changes) each time rspace-web is compiled, as the plugin is tied to the Maven compile phase.
Doing so ensures that before a PR is created the formatting is as expected and changesets should therefore only contain actual code changes rather than formatting/whitespace changes.

To sync Intellij formatting with the formatting applied by Spotless, import the [intellij-java-google-style.xml](/intellij-java-google-style.xml) file:
1. Open File -> Settings
2. Navigate to Editor -> Code Style
3. Click the settings icon next to 'Scheme', then Import
4. Locate the `intellij-java-google-style.xml' file

### React
For React, code style conventions are enforced automatically. Please follow
the [React getting started guide](React.md) for set-up steps.

## SonarQube Code Analysis
SonarQube is used for static analysis of the codebase to highlight potential bugs as well
as code quality and security issues.

A Jenkins job runs after each successful build of the develop branch on the main
rspace-web project. If there are any new issues introduced in the build the
SonarQube job fails and a message is sent to the build-notifications slack channel.
