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

New users are persisted in `UserManager#saveUser` and salted password
hashes generated. Plain text passwords are not stored.

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

### Resource-role permissions

New domain resources that need Owner/Manager-style collaboration can use the generic
resource-access module instead of adding another Shiro wildcard-permission dialect. This does not
replace Shiro: Shiro still authenticates the request and supplies the effective subject and, for
run-as, the originating actor. The resource-role service is the authority for the protected
resource operation.

The protected entity owns a non-null foreign key to one versioned `ResourceAccess` aggregate.
Each resource type registers a `ResourceRoleScheme`, a service-layer
`ProtectedResourceAccess<T, ID>` adapter, and a REST API v2 `ResourceAccessSpec`. A valid scheme has
Owner as its highest role, Manager immediately below Owner, and monotonic capabilities: every
higher role includes every lower role's capabilities. One highest effective role is selected from
direct, active-group, dynamic-audience, and implicit sources; a lower direct role is not a deny.

Every aggregate retains at least one persisted Owner assignment. Only the manage-owners capability
can alter that set, so Manager cannot add or remove Owners. Implicit sysadmin access does not count
toward the persisted-Owner invariant. A transaction that replaces access locks the protected
resource and re-resolves the represented subject from current identity, membership, and assignment
state before it mutates. A controller check is not mutation authority.

Collection authorization uses a server-created trusted query constraint. It restricts list,
count, item, and bulk queries before pagination and uses correlated membership checks that cannot
duplicate rows. Page-level access and capability fields are batch-resolved. Do not expose this
constraint as client query syntax, filter unauthorized rows in memory, or add one permission query
per result. An unreadable direct resource is concealed as 404.

Access replacement is an atomic complete-set `PUT`: clients read an `ETag`, send it in
`If-Match`, and receive 412 for a stale version. Assignment snapshots retain enough identity text
for audit after a principal is hard-deleted, but snapshots never grant access. Availability,
disabled-user status, group membership, and effective Owner health are derived from live identity
state. Consequently, required Owner rows may remain while no effective Owner exists; a sysadmin
can locate and repair this state. User and group lifecycle changes do not fan out to rewrite every
resource aggregate or fabricate resource-level audit events.

Generic backend, REST, OpenAPI, and frontend changes must continue to pass a second test-only role
scheme whose lower roles have domain-neutral names. This is the guard against Booking-specific
branches in the reusable module. A new production adopter also needs a reviewed migration plan for
all existing rows and their previous permission semantics before its schema or registration is
enabled.

## Logging security errors

Security events and exceptions should be logged by a security logger
(defined in `log4j2.xml`). By default, any `AuthorizationExceptions` thrown
out of a controller will be logged correctly using the
`ControllerExceptionHandler` wired into controllers.

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
