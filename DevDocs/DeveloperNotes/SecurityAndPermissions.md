# Security and permissions

This document describes how security and permissions work in RSpace.

## Basics

We use Apache Shiro security library for authentication and
authorisation of resources. Originally we used Spring Security but moved
to Shiro because of ease and simplicity of its instance-based permission
features.

## Apache Shiro

Is configured in `WEB-INF/security.xml`, which defines role based access
to URLs and the various Spring bean integrations. There is some good
documentation in the Shiro project web docs which describes the basics.

## Authentication

### Standalone application

Authentication is handled by a filter,
ShiroFormAuthenticationFilterExtension.java that in collaboration with
`ShiroRealm.java`, consults the User entity for username/password match.
Once this information is loaded, it is cached in ehcache second-level
cache and is only reloaded once the cache has expired (or after session
expiry/termination).

The configuration of this caching behaviour is in `ehcache.xml` and
annotations on the User entity class.

New users are persisted in `UserManager#saveUser` and password hashes
generated. Plain text passwords are not stored.

### Password storage

The design and its trade-offs are in
[ADR 0011](../adr/0011-argon2id-password-hashing-and-login-concurrency-limit.md).

- **Encoder.** `RSpacePasswordEncoder` (bean in `SecurityBaseConfig`) wraps
  Spring Security's `DelegatingPasswordEncoder`. Every stored login and
  SSO/Community verification password carries an `{id}` prefix. Only
  three ids are registered:
  - `argon2@rspace_v1`, the default for new passwords: Argon2id,
    m=19456 KiB, t=2, p=1, 16-byte salt, 32-byte hash. The salt is
    inside the encoded value and the `salt` column is null.
  - `argon2-legacy-sha256@rspace_v1`, pre-Argon2 hashes wrapped at rest
    by the Liquibase change `WrapLegacyPasswordHashes_RSDEV894`. Stored
    as `<base64 salt>$<argon2 of Base64(SHA-256(salt || utf8 password))>`,
    with an empty salt for unsalted rows. The SHA-256 is fed to Argon2 as
    the Base64 of its decoded bytes, so the case of the old hex never
    matters. These values are permanent: a check never rewrites them,
    and they change only when the user sets a new password.
  - `bcrypt`, verification passwords set before RSDEV-894, prefixed at
    rest by the Liquibase change
    `PrefixBcryptVerificationPasswords_RSDEV894`. It runs after the
    login wrap, skips values already carrying a registered id, and
    clears (sets to null) and logs at ERROR by username any other
    non-blank value, since that could never verify. This id only
    reads: it never encodes, and `UsernamePasswordCredentialsMatcher`
    refuses it for login passwords. Prefixed values are permanent too;
    new verification passwords are Argon2id.

  An unknown or missing prefix throws `IllegalArgumentException` and the
  check fails closed.
- **Verification.** Shiro login (`ShiroRealm`) and default-realm
  reauthentication (`ReauthenticatorImpl`) both go through
  `UsernamePasswordCredentialsMatcher`, which calls
  `BoundedPasswordVerifier`. Verification password checks
  (`VerificationPasswordValidatorImpl#authenticateVerificationPassword`)
  call the same verifier and share its permits. Only LDAP
  reauthentication, which checks against the directory, skips it. That verifier holds a fair semaphore
  (`login.passwordVerification.maxConcurrent`, default 8) and a
  per-username lock, so one account holds at most one permit. A single
  deadline (`login.passwordVerification.waitSeconds`, default 5) covers
  both waits. On timeout it throws `LoginVerificationBusyException`,
  which the login filter and `ReauthenticatorImpl` deliberately do not
  count toward lockout. Encoding new passwords and the migration are not
  bounded.
- **Guess spacing.** After a mismatch the verifier keeps the per-username
  lock for `login.passwordVerification.failureDelayMillis` (default 1000)
  with the permit already returned, so one account gets at most one guess
  per second at login, reauthentication or a verification password check.
  No database state is involved. A password check never writes to the
  `User` row: an earlier version re-encoded legacy hashes on login and
  counted reauthentication failures on the row, and both broke callers
  that held the same row in their own transaction (ADR 0011, considered
  options). Failed reauthentication is not counted toward the login-form
  lockout. Signing and witnessing go through
  `IReauthenticator#reauthenticate`. Changing a verification password
  checks the current one through
  `IReauthenticator#reauthenticateWithVerificationPassword`, which applies
  the same busy handling and security logging but never substitutes an
  operating-as sysadmin.

## Authorization

Authorization is configured at several levels:
1.  Role based access control (RBAC) - User, PI, Community Admin or
    Sysadmin roles
2.  Path based control - resources at given URLs are accessible based on
    role
3.  Permissions - a role encapsulates a set of permissions of what
    actions may be performed in a particular domain, e.g., Create group,
    Create Form, Delete Form
4.  Instance-based permissions - access based on the id or other
    property of an object.
    For example, a user may have 'Read Record' permission but can only
    read his own records and those that are shared with him.

### Roles

A Roles entity table holds information about the 4 roles. A user may
have more than 1 role (e.g., User, PI) but not all combinations should
be used. Each role has a set of permissions associated with it.

### Calculating permissions

Permissions can be associated with a role or an individual user or
group. When deciding whether authorization is permitted, Shiro calls a
method in `ShiroRealm` class, `doGetAuthorizationInfo`. This is cached after
an initial DB load for performance reasons. This loads all permissions
relevant to the user.

These permissions are then matched against the 'query' permission. E.g.,
when creating a group, the permission `GROUP:CREATE` is searched for in
the user's permissions. If user has such a permission, this operation
proceeds, otherwise an AuthorizationException is thrown.

### Permission syntax.

We use a permission syntax derived from Shiro's wildcard permission
syntax.
This takes the form:
```
DOMAIN:ACTION:IDENTIFIER
```

Domains correspond to entity types (Record, Form, Group, etc) defined
in `PermissionDomain.java`.

Actions are operations (Read, Write,Create, Delete) defined in
`PermissionType.java`.

Identifiers is a variable string stating some property. For an
up-to-date list see `ConstrainPermissionResolver` which defines
parsers - e.g., by id, by property (e.g., a date range).

Most of the permission behaviour is defined in the classes in package
`com.axiope.model.permissions` and is intentionally internal to avoid
exposing too much complexity. Key classes include
`ConstraintBasedPermission` (an object representation of the
`DOMAIN:ACTION:IDENTIFIER` structure) and `ConstrainPermissionResolver`
which handles conversions between parsing of permission strings into
objects.

Various adapter classes adapt RSpace entities to the permissions API,
e.g., `GroupPermissionsAdapter`. These allow arbitrary objects to be
compared with user permissions to see if that user is authorised to
access them.

### Using permissions in code

The interface `IPermissionUtils` defines some high-level methods for
checking permissions. E.g., `isPermitted(domainObject, type, subject)`.
Collections, or an `ISearchResults` loaded from the database can also be
filtered by the various filter methods.

These methods provide an abstraction over Shiro's
`SecurityUtils.getSubject().isPermitted` methods.

**Note** Permissions checking is performed in application logic, not in
the DB query. Currently, this can introduce some performance issues if 
a large number of results are returned of which the majority are not 
accessible by the user.

In general, if writing a service level method that accesses or modifies
a resource, we should be checking for permissions, even if this is
configured at the URL path level. If permission check returns false,
it's generally OK to throw an `AuthorisationException`.

#### Refreshing permissions

Some permissions are dynamic. For example, sharing a record with a user
gives them permission to view that record. Because permissions are
cached, it takes some time for the user to acquire those permissions. To
get round this, there is a method in
`IPermissionUtils,notifyUserOrGroupToRefreshCache`, that if called will
force permissions refresh for that user, even if they are currently
active in the application.
**You only need to call this if writing code that manipulates a user's
permissions** .

## Logging security errors

Security events and exceptions should be logged by a security logger
(defined in `log4j2.xml`). By default, any `AuthorizationExceptions` thrown
out of a controller will be logged correctly using the
`ControllerExceptionHandler` wired into controllers.

## Sorting paginated listings

The `orderBy` request parameter on legacy listings is bound straight onto
`PaginationCriteria` and used to be concatenated into HQL or SQL. Each listing
now has a sort enum in `com.researchspace.model.sort` (`UserSort`, `GroupSort`,
`FormSort`, `RecordSort`, `CommunicationSort` and so on). Before database query
construction, the enum's `fromRequest(String)` method resolves the request
string: a blank value gives the listing default and an unknown value throws
`UnknownSortKeyException`. `ControllerExceptionHandler` turns that into HTTP
400 on an ajax request, which is how listings are re-sorted. A full page load
gets the error page with the 500 that `error.jsp` produces for every page
rendering it. DAOs decode the key on entry and build the order clause from a
`switch`, so no request text reaches query construction.

To add a sort key, add a constant to the listing's enum and a `case` to the
DAO's switch. Clients send the bare token (`owner`, `sender`, `fileUsage`), not
a property path. Never read `pgCrit.getOrderBy()` directly in query code: pass
it through the listing's enum first. This is a review rule, not something the
build checks.

In-memory result sorting (`SearchUtils.sortList` and `sortInventoryList`) still
compares the requested key directly. That is safe because the value never
reaches query construction.

## UI notes

There are some JSP tags in the Shiro: and rs: namespaces that can be
used to display/hide UI elements based on role or permissions.
Permissions checking in code should also be done so as to prevent URL
guessing attacks.

## Uploaded file content

A file's name and declared content type come from the client, so neither is
evidence of what the bytes actually are. `MediaFileContentValidator` checks the
content of uploads whose extension claims an image against the type that
extension implies, and `MediaManagerImpl` calls it on both the new-upload and
the new-version path, which every upload passes through.

A rejection is a checked `MediaContentMismatchException`, implemented as a
subclass of `IOException`. Existing file-handling callers can treat it as an I/O
failure, while callers that need to report a precise validation error can catch
the subtype. Nothing has been written at that point, so a caller inside a
transaction can catch it, report the file and carry on without marking the
transaction rollback-only. `MediaManagerImpl` owns and closes the supplied
stream on success, rejection, and detection failure.

Detection reads the leading bytes only, so a valid image with content appended
after it still passes. Treat stored files as untrusted bytes regardless.

Endpoints that serve stored file content should send
`Content-Type` and `X-Content-Type-Options: nosniff` together via
`ResponseHeaders.setContentTypeAndPreventSniffing`, so a response cannot opt out
of sniffing without also declaring the type the browser should honour. This
matters most where bytes are returned inline, without a `Content-Disposition`
header.
