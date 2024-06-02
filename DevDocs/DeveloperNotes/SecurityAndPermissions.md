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
