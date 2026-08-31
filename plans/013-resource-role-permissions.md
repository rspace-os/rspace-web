# Plan 013: Ship generic resource-role permissions with Booking as the first adopter

> **Executor instructions**: Read this plan fully before changing code. Follow
> the steps in order, run every verification command, and confirm the expected
> result before continuing. Preserve unrelated and user-authored work. Never run
> a Maven `install`, `install:install-file`, or deploy goal. If a STOP condition
> occurs, stop and report it; do not substitute the legacy ACL or invent a
> compatibility model. When done, update this plan's row in `plans/README.md`
> unless a reviewer says they maintain the index.
>
> **Drift check, run first**:
>
> ```bash
> plan_scope=(
>   CONTEXT.md
>   DevDocs/adr/0008-resource-role-permissions.md
>   DevDocs/DeveloperNotes/RestApiV2Collections.md
>   DevDocs/DeveloperNotes/SecurityAndPermissions.md
>   src/main/java/com/researchspace/model/resourceaccess
>   src/main/java/com/researchspace/dao/resourceaccess
>   src/main/java/com/researchspace/dao/hibernate
>   src/main/java/com/researchspace/service/resourceaccess
>   src/main/java/com/researchspace/model/collection
>   src/main/java/com/researchspace/dao/query
>   src/main/java/com/researchspace/api/v2
>   src/main/java/com/researchspace/model/booking
>   src/main/java/com/researchspace/booking
>   src/main/java/com/researchspace/model/inventory/Instrument.java
>   src/main/java/com/researchspace/service/inventory
>   src/main/java/com/researchspace/api/v1
>   src/main/resources/sqlUpdates
>   src/main/resources/hibernate.cfg.xml
>   src/main/webapp/ui/src/modules/booking
>   src/main/webapp/ui/src/modules/common/resource-access
>   src/main/webapp/ui/src/modules/common/i18n/locales/en-US
>   src/main/webapp/ui/src/Inventory/components/ContextMenu
>   src/main/webapp/ui/src/stores/models/Search.ts
>   src/test/java/com/researchspace
>   plans/013-resource-role-permissions.md
>   plans/README.md
> )
> git diff --stat 5f230b8da3..HEAD -- "${plan_scope[@]}"
> git diff --stat -- "${plan_scope[@]}"
> git diff --cached --stat -- "${plan_scope[@]}"
> git ls-files --others --exclude-standard -- "${plan_scope[@]}"
> ```
>
> This plan was written against a dirty Booking worktree. A non-empty diff is
> expected. The four commands separately expose committed, unstaged, staged,
> and untracked drift; do not treat a clean base-to-HEAD diff as a clean
> worktree. Compare the live symbols with "Current state" below. A material
> change to REST v2 resource registration, Booking's merged item page,
> configuration/bookings managers, Inventory owner transfer, or the selected
> Plan 012 verdict is a STOP condition.

## Status

- **Priority**: P1
- **Effort**: XL
- **Risk**: HIGH
- **Depends on**: Plan 012 must be DONE with a complete Prototype verdict
- **Category**: security
- **Planned at**: commit `5f230b8da3`, 2026-08-30, with the current dirty
  Booking worktree present
- **Status**: DONE

## Why this matters

Booking currently authorizes configuration writes by system-administrator role
and derives booking/calendar visibility from the target Instrument's Inventory
permissions. That cannot express a Booking Owner, Manager, Booker, or Viewer,
and it makes a Booking grant ineffective whenever Inventory says otherwise.

This plan introduces one reusable resource-role module with a versioned access
aggregate, user/group/dynamic-audience assignments, query-time effective access,
atomic replacement, typed capabilities, generic audit deltas, resource-scoped
grantee search, and a generic frontend editor. Booking is the first production
adopter. Its permissions become independent of Inventory while an explicit,
optional Inventory ownership-transfer command can coordinate both ownerships.

Keep this as one plan file. Execute its four work packages in order, but do not
merge or deploy a package independently. Package A builds the generic module.
Package B adopts it in the Booking backend. Package C adds the frontend and
accessibility coverage. Package D adds coordinated Inventory transfer. The
combined release gate follows all four.

## Non-negotiable architecture

The worktree's `DevDocs/adr/0008-resource-role-permissions.md` is still marked
proposed and contains terms superseded by this review. Step 1 reconciles it and
`CONTEXT.md`, then marks the ADR accepted before schema work. The accepted
decision must contain these rules:

- A protected entity has a foreign key to one generic, versioned
  resource-access aggregate. Do not create a global table keyed by arbitrary
  `resourceType` and `resourceId` strings.
- Each protected resource registers a role scheme. Every scheme includes Owner
  as the highest role and Manager immediately below it; only Owner can manage
  Owner assignments; every aggregate keeps a persisted Owner.
- Scheme validation must prove monotonic capability inclusion: each higher role
  contains every capability of every lower role. Resolution selects one highest
  role, so a non-monotonic scheme would silently discard a lower-role grant.
- A role assignment grants one role to one user, group, or dynamic audience.
  One grantee has at most one direct assignment per aggregate. Each scheme
  declares which grantee kinds are valid for each role and validates them on
  every write.
- Direct, active-group, dynamic-audience, and implicit sources resolve to the
  highest effective role. A direct lower role is not a deny.
- Access replacement is atomic and versioned separately from the protected
  resource. Reads return `ETag`; PUT requires `If-Match`; stale writes return
  412 without mutation.
- A controller authorization check is never the mutation's authority. The
  transactional manager reloads and locks every identity/membership fact used
  to authorize the represented subject, re-resolves capability after the
  resource lock, and only then mutates.
- Row access is applied before count and pagination. Direct unauthorized reads
  are indistinguishable from absence.
- The generic backend and generic frontend editor must each be proven with a
  second test-only scheme whose lower roles are not named Booker or Viewer.
- Keep the current Shiro security solution. Controllers adapt the authenticated
  subject and audit actor into service commands. The generic module must not
  import Shiro, inspect thread-local security state, or depend on REST v2 types.

Use the vocabulary in `CONTEXT.md:280-296`: **role scheme**, **role
assignment**, **grantee**, **capability**, and **effective role**. Avoid
"sharee" and avoid reusing `AccessPolicy` as the new module's name; that name
already belongs to REST v2 collection authorization.

## Fixed product decisions

### Booking roles and capability matrix

Use stable uppercase wire keys `OWNER`, `MANAGER`, `BOOKER`, and `VIEWER`.
Labels and descriptions are translated in the frontend adapter.

| Capability | Owner | Manager | Booker | Viewer |
|---|:---:|:---:|:---:|:---:|
| Read configuration and full schedule/event details | yes | yes | yes | yes |
| Create personal calendar subscription | yes | yes | yes | yes |
| Create a booking | yes | yes | yes | no |
| Edit/cancel own booking | yes | yes | yes | no |
| Edit configuration | yes | yes | no | no |
| View item audit log | yes | yes | no | no |
| Manage every booking and blockout | yes | yes | no | no |
| Assign Manager/Booker/Viewer | yes | yes | no | no |
| Add/remove/change Owners | yes | no | no | no |
| Archive configuration | yes | no | no | no |

System administrators receive implicit Owner only when the represented subject
is a system administrator. Under run-as, authorize `ApiV2Caller.subject()` and
record `ApiV2Caller.actor()` in audit. Never persist a fake sysadmin assignment,
and do not count implicit Owner toward the persisted-Owner invariant.

Use this Booking capability object on every readable configuration document:

```json
{
  "effectiveRole": "MANAGER",
  "roleSources": [
    { "kind": "DIRECT", "role": "VIEWER" },
    { "kind": "GROUP", "role": "MANAGER", "grantee": { "kind": "GROUP", "id": 41, "name": "Imaging" } }
  ],
  "capabilities": {
    "canEditConfiguration": true,
    "canArchiveConfiguration": false,
    "canViewAudit": true,
    "canViewAccess": true,
    "canManageAssignments": true,
    "canManageOwners": false,
    "canCreateBooking": true,
    "canManageOwnBookings": true,
    "canManageAllEvents": true,
    "canCreateBlockout": true,
    "canSubscribeCalendar": true,
    "canLeaveConfiguration": true
  }
}
```

`roleSources` describes only the caller's sources, not the resource's complete
assignment list. The server is authoritative; React must not derive a
capability from `effectiveRole`. `canLeaveConfiguration` is resource-state
dependent, not a rank grant: it is true only when the represented subject has a
direct assignment and removing it preserves the structural Owner invariant in
the current version. It is false for group/audience-only access, implicit-only
sysadmin access, and the final direct Owner. The server still rechecks under
lock and returns 409 if state changes before DELETE-me.

### Assignment semantics and invariant

- One user/group/audience has at most one direct role assignment on an
  aggregate. Reject duplicate grantee keys in one replacement document.
- Every Booking configuration must contain at least one persisted `OWNER`
  assignment row. A disabled user, a hard-deleted group retained by snapshot,
  or a group with no enabled members still satisfies this structural invariant.
- Booking permits `AUDIENCE` assignments only for Booker and Viewer. Owner and
  Manager accept users and supported groups only, so All users can never satisfy
  structural or effective Owner health.
- Derive principal status from the live User or Group FK. Do not persist a
  denormalized availability flag and do not fan identity lifecycle changes out
  across every protected resource. A disabled user, deleted-group snapshot, or
  zero-enabled-member Owner group supplies no effective Owner access, but its
  row remains visible, removable, auditable, and eligible to satisfy the
  structural invariant.
- Owner can change every assignment. Manager may add/change/remove only
  Manager, Booker, and Viewer rows; the before and after Owner sets must be
  byte-for-byte identical for a Manager replacement.
- System administrators use Owner's operation rules through implicit access.
- No per-user deny or exclusion exists. Group membership and All users apply
  dynamically at authorization time.

### Creation and default sharing

- Any ordinary user may create a configuration only for an active, concrete
  Instrument they own. A sysadmin may create one for any eligible Instrument.
- Creation atomically creates the resource-access aggregate, makes the creator
  a persisted Owner, and applies the instance default as Booker grants.
- Add `defaultSharedWith` to `BookingConfigurationDefaults` with wire values
  `ALL_USERS`, `SELECTED`, and `ONLY_ME`. Seed `ALL_USERS`.
- `SELECTED` means exactly the users/groups stored in the default list below
  the setting; it requires at least one selected grantee. `ALL_USERS` and
  `ONLY_ME` store no active selected list.
- The selected default list contains users and Lab/Collaboration/Project groups,
  never communities or the All users audience. Existing selected principals
  that later become unavailable remain visible until a sysadmin removes them.
- Defaults are copied only at configuration creation. There is no later sync.
  If creator is also selected directly, the creator has only the Owner row.

### Leave and own-booking access after role loss

- `DELETE /api/v2/{resource}/{id}/access/me` removes only the represented
  subject's direct assignment under a pessimistic aggregate lock. It is a no-op
  with 204 when no direct assignment exists.
- It fails without mutation if it would remove the final persisted Owner.
- Group and All users sources remain. There is no exclusion row.
- Do not create `BookingConfigurationDeparture` or any retained-access marker.
  Booking collection and item reads use one query predicate: current readable
  configuration access OR `requester.id == representedSubject.id` for booking
  rows. The requester branch applies after voluntary or involuntary role loss.
- A requester without a current configuration role receives the complete own
  booking row and the safe, non-navigable target label needed by My Bookings,
  but server capabilities set edit/cancel to false and
  configuration/calendar/audit/access/subscription links are absent. This rule
  does not reveal other users' bookings, blockouts, or the parent configuration
  document.

### Inventory independence and optional transfer

Booking authorization never reads, copies, intersects, or continuously syncs
Inventory sharing/ACL state after creation. A Booking role reveals only the
safe target summary required by Booking and grants no Inventory route access.

Inventory transfer gets an opt-in boolean, default false: **Also transfer
Booking configuration ownership**. When true and the Instrument has a Booking
configuration:

1. require the normal Inventory transfer permission and Booking
   `canManageOwners` for the represented subject;
2. lock the Instrument, configuration, access aggregate, and its assignment
   rows in a consistent order, then recheck both Inventory and Booking
   authorization from locked identity/membership state;
3. add/change the incoming Instrument owner's direct assignment to Owner;
4. remove the outgoing Instrument owner's direct assignment only if that direct
   assignment is Owner; preserve a lower direct role and every other assignment;
5. save both ownership changes or neither and publish both audit events after
   commit under the platform audit semantics described below;
6. for a bulk request, keep this atomicity per Instrument. An unconfigured
   Instrument transfers normally; a configured item without Booking permission
   fails that item before either ownership changes.

Bulk Booking administration remains separate: the sysadmin-only admin page may
bulk enable, disable, or archive configurations, but no bulk action changes
access assignments.

### Why continuous Inventory permission sync is rejected

The apparent benefits are real: users would configure access once, initial
Booking access would feel familiar, Inventory ownership/group changes would
automatically reach Booking, and orphaned Booking ownership would be less
likely. A one-time copy at creation would also be straightforward.

Those benefits do not survive the mismatched models. Inventory/workspace roles
do not map one-to-one to Owner, Manager, Booker, and Viewer; Inventory propagation
and group rules can change without Booking intent; and either system can become
the hidden authority when the two disagree. Continuous sync would require
direction, conflict, deletion, group-membership, retry, and audit rules, and a
Booking-only Viewer would either gain unwanted Inventory access or lose a valid
Booking grant. A one-time copy looks simpler but becomes misleading immediately
after either side changes.

The selected compromise keeps the useful intent points without coupling the
authorities: require Instrument ownership only when an ordinary user creates a
configuration, make the creator Booking Owner, use the Booking-specific global
default for initial access, and offer one explicit atomic ownership-transfer
option. Everything after those commands is independently administered and
independently audited.

### Privacy and concealment

- There is no event privacy distinction in this iteration. Any current Booking
  role sees full authorized event details; an unauthorized caller sees none.
- Apply configuration access before list count and pagination. Apply booking
  access before list count and pagination, including the requester-own-booking
  disjunct described above.
- An unauthorized direct request for configuration, access document, calendar,
  audit, booking, or blockout returns the same 404 as a missing resource.
- A caller who can read a resource but attempts a forbidden mutation receives
  403. Invalid invariants receive a typed 409. Stale `If-Match` receives 412.
- Never use a post-query Java filter, a per-row permission lookup, or an
  unrestricted Inventory relationship resolver.

## REST API v2 contract

### Resource access document

Register access support on an `ApiV2ResourceSpec`; do not accept arbitrary
resource types merely because a row exists. Only registered protected resources
receive these routes:

```text
GET    /api/v2/{resource}/{id}/access
PUT    /api/v2/{resource}/{id}/access
DELETE /api/v2/{resource}/{id}/access/me
GET    /api/v2/{resource}/{id}/access/grantees?query=<text>&limit=<1..50>
GET    /api/v2/booking-settings/access-grantees?query=<text>&limit=<1..50>
GET    /api/v2/booking-configuration-targets?query=<text>&limit=<1..50>
```

For Booking, GET is available to Owner and Manager. Manager sees Owner rows but
cannot mutate them. DELETE-me is available to any current direct assignee, even
when that role cannot GET the complete access document.

GET returns a strong ETag such as `"7"` and this conceptual body:

```json
{
  "scheme": "booking-configuration",
  "version": 7,
  "assignments": [
    {
      "grantee": {
        "kind": "USER",
        "id": 12,
        "key": "user:12",
        "name": "Ada Lovelace",
        "detail": "ada",
        "available": true
      },
      "role": "OWNER"
    },
    {
      "grantee": {
        "kind": "AUDIENCE",
        "id": "ALL_USERS",
        "key": "audience:all-users",
        "name": "All users",
        "available": true
      },
      "role": "BOOKER"
    }
  ],
  "caller": {
    "effectiveRole": "OWNER",
    "roleSources": [],
    "capabilities": {
      "canManageAssignments": true,
      "canManageOwners": true,
      "canLeave": true
    }
  }
}
```

Every assigned `USER` row includes `effectiveRole` and `roleSources`, computed
in one batch, so the editor can explain that a direct Viewer is a Manager
through a group. For another user, include only resource-assignment sources:
the direct row, All users, and groups that already appear as assignments in
this same access document. Do not expose unrelated memberships, email
addresses, or implicit system roles. This bounded explanation is mandatory,
not an optional renderer enhancement.

PUT takes one complete assignments array, not a patch. Its strict write DTO is
only `{ "granteeKey": "user:12", "role": "OWNER" }`; read-side `grantee`,
name/detail/status snapshots, `effectiveRole`, and `roleSources` are never
writable. Reject unknown or read-only fields with 400. Resolve every new key to
a live supported principal on the server and derive its current snapshot.
Existing unavailable assignments may be submitted unchanged or removed, but
their role and snapshot cannot be changed. The document must receive the exact
GET ETag in `If-Match`. Missing `If-Match` returns 428; malformed returns 400;
stale returns 412; a no-op returns the unchanged version/ETag and emits no
audit. The response is the updated access document and ETag. Do not use the
Booking configuration's `configurationVersion` here.

PUT may change the caller's direct role but must reject a replacement that
removes the represented subject's direct row; the client must use DELETE-me for
that semantic operation. This prevents bypassing the resource-specific leave
hook and avoids returning the complete access document after the caller has
removed their final role.

DELETE-me locks and validates current state without requiring a prior access
GET or `If-Match`; this is necessary for Booker/Viewer leave. Return 204. A
last-Owner failure uses a typed 409 problem.

### Resource-scoped grantee directories

The resource-scoped grantee endpoint requires the represented subject's
assignment-management capability for that resource and returns safe identity
fields only. The Booking-settings variant remains sysadmin-only and is used
only to edit creation defaults. Both may share one bounded DAO query. Require a
trimmed query of at least two characters, escape wildcard characters, cap
results, and use one database query per principal type rather than loading all
accounts.

- Ordinary callers can find active users who share any of their groups and the
  caller's own valid Lab/Collaboration/Project groups.
- Sysadmins can find every active user and valid Lab/Collaboration/Project
  group through either authorized route.
- Communities and the All users audience never come from search. The editor
  supplies All users as its single known dynamic audience.
- Disabled users, removed groups, and unsupported principal kinds are not new
  search results. They remain renderable when already assigned.

### Registered access support and OpenAPI

Do not add a broad caller-scoped-field framework for one adopter. Add one
optional `ResourceAccessSpec<T, ID>` to `ApiV2ResourceSpec`. That small REST
adapter contributes the row constraint, batched caller decoration, fixed schema
fields, and registered access routes for a protected resource. It adapts a
service-level protected-resource interface and does not leak REST registration
types into the generic service module.

The fixed fields are `effectiveRole`, `roleSources`, `capabilities`, and the
sysadmin-only Owner-health indicator. They must appear in collection
metadata/OpenAPI and may be selected with ordinary
`fields[booking-configurations]`. They must not appear in custom-field discovery
or use runtime-field query syntax.

The concrete Booking target-search endpoint is not the relationship target's
generic collection route. It returns only safe summaries for active concrete
Instruments that do not already have a configuration: owned Instruments for an
ordinary caller and every eligible Instrument for a sysadmin. Bound and escape
its search like the grantee directory, and recheck eligibility/ownership under
the configuration-creation transaction because search results can become
stale.

The access spec receives the page's resources and represented subject once,
loads assignments in bulk, and decorates documents without N+1 queries. Extend
`ApiV2OpenApiGenerator` to document access paths only for registered access
specs, including ETag/If-Match, 404 concealment, 409 invariant, 412 stale, and
428 precondition responses.

### Safe Booking target relationship

Do not call `RelationshipReadAccess.unrestricted` for Instrument relationships.
Instead register a relationship-only REST v2 target named
`booking-instruments`, backed by `Instrument` but described by a safe allowlist:

- id, globalId, name, deleted.

It has no generic collection/item route, no custom/runtime fields, no Inventory
permissions, no owner, parent/container location, fields, samples, identifiers,
audit, or nested includes. Only Booking configuration relationships can resolve it. Change Booking's
relationship wire reference to `relationTo: "booking-instruments"` and update
the Booking frontend schemas. Configuration creation still performs the
authoritative owner/sysadmin check in the Booking service.

## Persistence and module boundaries

### Generic entities

Create `com.researchspace.model.resourceaccess` with:

- `ResourceAccess`: generated id, non-null `schemeKey`, `@Version long version`,
  audit timestamps/actor references following local conventions, and an
  orphan-removing collection of assignments.
- `ResourceRoleAssignment`: generated id, parent aggregate, non-null `roleKey`,
  non-null immutable `granteeKey`, grantee kind, nullable User FK, nullable Group
  FK, optional audience key, and safe name/detail snapshots. FKs use
  `ON DELETE SET NULL`; snapshots and `granteeKey` preserve an unavailable row
  and audit identity after hard deletion. There is no availability column.
- `ResourceGranteeKind`: `USER`, `GROUP`, `AUDIENCE`.
- `ResourceAudience`: only `ALL_USERS` in this iteration.

Use unique indexes on `(resource_access_id, grantee_key)` and on the protected
entity's resource-access FK. Database checks must reject mixed-kind references
but allow the expected User or Group FK to become null after `ON DELETE SET
NULL`. The service rejects every new user/group assignment without a live FK
and every audience assignment other than `ALL_USERS`. Do not put a protected
resource type/id on either generic table.

`BookingConfiguration` owns a mandatory one-to-one FK to `ResourceAccess`.
Because the product decision says there are no existing configurations to
migrate, the Liquibase changeset must HALT if `BookingConfiguration` is
non-empty before adding the non-null FK. Do not infer Owners from `createdBy`.

Create `BookingDefaultAccessGrantee`, attached to the singleton defaults, using
the same user/group identity/snapshot approach but no role column because all
selected defaults grant Booker. Do not create a departure table or marker.

Add the matching Envers tables/columns where the touched entities are audited.
Follow the existing `changeLog-rsdev-1187-*.xml` style and include the new file
near the other Booking changesets, before recurring/custom updates.
Register `ResourceAccess`, `ResourceRoleAssignment`, and
`BookingDefaultAccessGrantee` explicitly in
`src/main/resources/hibernate.cfg.xml`; annotations alone do not add entities to
this repository's SessionFactory.

### Generic service interface

Create `com.researchspace.service.resourceaccess` as the deep module. Its public
surface should be small:

```java
interface ResourceRoleScheme {
  String key();
  List<ResourceRole> roles();
  Set<String> capabilities(String roleKey);
  Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey);
  Optional<String> implicitRole(User subject);
  void validate();
}

interface ResourceAccessManager {
  ResolvedResourceAccess resolve(ResourceAccess access, User subject);
  Map<Long, ResolvedResourceAccess> resolveAll(Collection<ResourceAccess> access, User subject);
  <T, ID> ResourceAccessDocument get(ProtectedResourceAccess<T, ID> resource, ID id, User subject);
  <T, ID> ResourceAccessDocument replace(ProtectedResourceAccess<T, ID> resource,
      ReplaceResourceAccess<ID> command, User subject, User actor);
  <T, ID> void removeSelf(ProtectedResourceAccess<T, ID> resource,
      RemoveSelfResourceAccess<ID> command, User subject, User actor);
}
```

Use records/value types for roles, sources, grantees, capabilities, replacement
commands, and audit deltas. Service interfaces and implementations used as
transactional Spring beans must end in `Manager`; DAOs assume an active
transaction. Add Javadoc to service methods and non-trivial entity methods.

`ProtectedResourceAccess<T, ID>` is the service-level adapter. It loads and
locks the protected entity and aggregate, supplies the scheme and protected
audit target. It contains no route name, HTTP status, field projection, REST
identifier parsing, or optional lifecycle hook. REST v2 wraps it in
`ResourceAccessSpec<T, ID>`. This code registration is not a polymorphic
database relation.

The resolver must:

- ignore a user FK whose account is disabled, a null group FK retained after
  hard deletion, and a group source the subject no longer belongs to;
- use the represented subject's current active group ids;
- include the All users audience dynamically;
- include implicit roles from the scheme;
- return the highest rank plus every applicable source;
- validate all role/capability keys at application startup;
- reject a scheme unless capabilities are monotonic across its ordered roles;
- reject an assignment whose grantee kind is not allowed for that role;
- batch assignments for page decoration;
- contain no Booking role name or Booking-specific branch.

The transaction that replaces access or removes self must re-authorize rather
than trust the controller's earlier read. Lock the protected resource, access
aggregate, and its assignment rows; reload the represented User; then acquire
database read locks on its enabled/system-role state and on every current
UserGroup row that could supply a role for this aggregate. Re-resolve the
requested capability from those locked rows before applying a change. User
disable/role changes, group deletion, and membership deletion require
incompatible writes to the locked rows; prove this protocol with concurrent
integration tests. If the live mapping cannot lock every authorization fact
consistently, STOP instead of retaining a check-then-mutate window.

### Query-time filtering

`FilterExpression` is a sealed caller-filter tree with only And, Comparison,
and Or variants. Do not smuggle a server-only EXISTS through a fake public
field or make the RSQL parser capable of constructing it. Introduce a typed
`QueryConstraint` accepted by `AccessResult.AllowedWhere`: caller-parsed
`FilterExpression` remains one implementation, while a trusted internal
resource-membership constraint is another. Update every exhaustive compiler,
rewriter, and test that currently assumes `FilterExpression` is the complete
constraint type.

The internal constraint must compile ResourceRoleAssignment
membership as SQL `EXISTS` against the protected row's `resourceAccess.id` for:

- direct active user id;
- any current active group id;
- `ALL_USERS` audience.

System administrators as represented subjects receive the scheme's implicit
role and therefore no row constraint. The scheme supplies the set of role keys
that carry the generic read-resource capability; Owner and Manager must be in
that set. Include `roleKey IN :readableRoles` in the correlated membership
predicate. This establishes visibility without encoding Booking ranks in SQL
and still permits a future scheme to define a non-reading workflow role.

Wire the resulting `AccessFunction`/`AccessResult.AllowedWhere` into the
registered collection description so `AbstractCollectionManager` and Booking's
custom DAO fold it into the query before pagination/count. If the live query
framework cannot express a duplicate-safe correlated EXISTS through this trusted
constraint, STOP. Do not join the assignments collection or filter results in
Java. Add a parser test proving no supported RSQL input can construct the
trusted constraint.

### Audit

Publish one generic `ResourceAccessAuditDelta` per semantic change and deliver
it through the existing `AFTER_COMMIT` `AuditTrailService` convention used by
`BookingAuditTrail`. The delta contains protected resource identity, actor,
represented subject, action, affected grantee key/snapshot, old role, new role,
timestamp, and a reason among assignment add/remove/change, All users on/off,
direct leave, and ownership transfer. Emit nothing for a no-op replacement or
a rolled-back transaction.

This is deliberately the platform's current best-effort, after-commit audit
semantics, not an atomic database audit or transactional outbox. An audit-sink
failure cannot roll back an already committed access change. Tests and release
notes must not claim exactly-once or durable delivery; adding that guarantee
requires a separate cross-cutting audit-outbox design.

Write deltas against the protected resource's existing audit identifier so the
standard `/api/v2/{resource}/{id}/audit` route includes them. Use SHARE,
UNSHARE, WRITE, or TRANSFER consistently and a structured, escaped description
that the Booking audit UI can render without parsing untrusted HTML. Extend the
generic audit route's resource hook so only callers with the scheme's
view-audit capability can read it; conceal others as 404.

Do not emit resource-level deltas when a user is disabled/enabled, a group is
deleted, group membership changes, or a group reaches zero enabled members.
Those actions do not mutate assignments, and the existing user/group audit is
the source of truth. `ON DELETE SET NULL` plus snapshots preserves hard-deleted
identity display without a resource-wide lifecycle fanout.

## Current state

### Booking authorization is not yet role based

`src/main/java/com/researchspace/model/booking/ApiV2BookingConfigurationResource.java:69-109`
currently declares:

```java
private static final AccessPolicy ACCESS = AccessPolicy.authenticatedReadsSysadminWrites();
```

Its `target` points at the normal `instruments` resource. Replace both decisions
with the registered Booking role access and safe relationship-only target.

`BookingConfigurationManagerImpl:60-90` authenticates reads and sends only
target-relationship access to the DAO. At `:204-207`, every mutation requires
`Role.SYSTEM_ROLE`. At `:94-139`, creation copies settings but creates no access
aggregate. These are the primary configuration seams to replace.

`TimeSlotBookingManagerImpl:76-95` reads by target access;
`:158-175` authorizes creation with `InventoryPermissionUtils`; and
`:280-323` lets requester, Instrument owner, or sysadmin edit and always
prepares `BookingPrivacy.FULL`. Replace Inventory ownership with Booking
capabilities while retaining full details for every authorized Booking role.

`BookingCalendarManagerImpl:340-364` rechecks configuration and Instrument
readability for subscriptions. Replace that pair with current Booking role
visibility. Keep the feed's read-time recheck so losing Booking access makes an
existing bearer feed unavailable.

`TimeSlotBooking.java:47-54` already has a non-null `requester` FK, and
`ApiV2TimeSlotBookingResource.java:47-50` already exposes `requester.id` as a
filterable selector. Use that existing relation for the own-booking read
disjunct. A separate departure table would duplicate it.

### REST v2 already has the correct filtering and registration seams

`AccessFunction.java:10-19` documents that collection access may return an
`AllowedWhere` and may not do I/O per invocation.
`AbstractCollectionManager.java:61-100` applies the read constraint before
collection count/page and constrains single-resource reads. Preserve this
behavior.

`ApiV2ResourceSpec.java:18-28` already collects a description, operations, and
runtime-field providers. Add one optional `ResourceAccessSpec` here rather than
a standalone hard-coded Booking controller registry or a second general field
provider interface.

`ApiV2ResourceRegistration.java:108-121` authorizes before rendering a list and
already batches runtime values at `:173-199`. Follow that batching shape for
the access spec's fixed fields, but keep their schema/selection separate from
custom runtime fields.

`ApiV2AuditLog.java:81-91` currently checks only that the resource is readable.
Add the optional access-registration capability check there. Do not fork a
second Booking audit endpoint.

### Booking frontend currently derives controls from sysadmin status

`BookableItemPage.tsx:179-203` sets:

```tsx
const { data: currentUser } = useCurrentUserQuery();
const canEdit = currentUser.hasSysAdminRole;
```

At `:319-397` that boolean controls Details editing while Audit is always a
tab. Replace it with the configuration document's capabilities, add the
selected Plan 012 Access-tab interaction, hide Audit without `canViewAudit`, and
show leave according to `canLeaveConfiguration`.

`AllBookableItemsPage.tsx:175-229` currently exposes only View details and Book
row actions. Add capability-driven Settings/Access/Create booking actions and an
Add item action; Viewer must not receive Book.

`BookableItemsPage.tsx:37-43,311` restricts the sysadmin administration list to
`createdBy.value == me`. Remove that filter so sysadmins see every
configuration. Retain its existing bulk enable/disable/archive actions and do
not add access bulk operations.

`BookingPage.tsx:89-91` already hides the Administration section from ordinary
users. Keep that rule. Ordinary management belongs on All Items/item pages.

`BookingSettingsPage.tsx:28-99` edits the existing singleton settings and
`configurationVersion`; extend this form with Default shared with and selected
grantees. This settings version remains separate from every resource-access
`version`.

### Inventory owner transfer is a legacy bulk path

`Inventory/components/ContextMenu/TransferAction.tsx:40-126` selects a recipient
and submits immediately. Add the opt-in checkbox and explanatory mixed-selection
state here.

`stores/models/Search.ts:869-927` calls the v1 bulk endpoint with
`operationType: CHANGE_OWNER`. Extend the request body rather than issuing a
second Booking request from React.

Before showing the checkbox, make one existing REST v2 collection request for
the selected Instrument ids, at most 100 by
`ApiInventoryBulkOperationPost.records`, using
`/api/v2/booking-configurations?where=target.id=in=(...)` and a fixed field list
containing only target identity and caller capabilities. The server omits
inaccessible configurations. Show the checkbox only when at least one returned
configuration has `canManageOwners`. Do not add a preflight endpoint. The
transfer command still authorizes every configured Instrument after locking it.

`InstrumentEntityApiManagerImpl.java:533-560` changes owner and publishes
`InventoryTransferEvent`. Make the Booking-aware transfer part of this service
transaction. `InventoryBulkOperationHandler.java:107-109,317-345` already runs
CHANGE_OWNER per record and reports per-record failures; propagate the new
option through this path.

`InventoryBulkOperationsApiController.InventoryBulkOperationConfig` currently
carries one `User`, so downstream code cannot distinguish the represented
subject from the original actor. Carry both. At the v1 controller seam derive
the actor once with `UserManager.getOriginalUserForOperateAs(subject)`, which
returns the subject for a direct request. Do not let the generic
resource-access module call Shiro or `IActiveUserStrategy`.

### Identity lifecycle is not an assignment mutation

`User.java:143,674-676` keeps enabled state on the live user row.
`Group.java:722-738` derives enabled-member count from current membership and
user state. `GroupManagerImpl.java:794-841` removes memberships and then calls
`groupDao.remove(group.getId())`; there is no archived-group state. Resource
assignments therefore read current User/Group state when resolving access, and
group FKs use `ON DELETE SET NULL` to retain only the assignment snapshot after
hard deletion.

### The intent documents still need reconciliation

`DevDocs/adr/0008-resource-role-permissions.md` is untracked and marked
`status: proposed`. `CONTEXT.md` still mentions archived groups and
departed-requester access. The decisions in this plan supersede those terms,
but source/document edits are deferred to the executor. Step 1 must make the
ADR and vocabulary agree with this plan before implementation starts.

## Commands you will need

Run frontend commands from the repository root. Never put a standalone `--`
after a pnpm script. Never use Maven install/deploy goals.

| Purpose | Command | Expected on success |
|---|---|---|
| Generic backend unit tests | `mvn test -Dtest=ResourceRoleSchemeTest,ResourceAccessResolverTest,QueryConstraintTest -Dfast=true` | selected tests pass |
| Booking service unit tests | `mvn test -Dtest=BookingConfigurationManagerTest,TimeSlotBookingManagerTest,BookingCalendarManagerTest,BookingConfigurationDefaultsManagerTest -Dfast=true` | selected tests pass |
| Spring/DAO integration | `mvn test -Dtest=ResourceAccessManagerIT,BookingConfigurationTargetFilterIT,TimeSlotBookingManagerIT` | selected tests pass with configured test DB |
| REST MVC integration | `mvn test -Dtest=ResourceAccessControllerMVCIT,AccessGranteeControllerMVCIT,BookingConfigurationTargetControllerMVCIT,BookingSettingsControllerMVCIT,BookingCalendarSubscriptionControllerMVCIT` | selected MVC tests pass |
| Inventory tests | `mvn test -Dtest=InstrumentEntityApiManagerTest,InventoryBulkOperationsApiControllerMVCIT` | selected tests pass |
| OpenAPI tests | `mvn test -Dtest=ApiV2OpenApiGeneratorTest,ApiV2OpenApiContractMVCIT` | generated contract tests pass |
| Frontend unit tests | `pnpm test src/modules/common/resource-access src/modules/booking src/Inventory/components/ContextMenu/__tests__/TransferAction.test.tsx` | selected tests pass |
| Browser inner loop | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx src/modules/booking/pages/my-bookings/MyBookingsPage.spec.tsx` | selected files pass in Chromium |
| Browser final | `pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx src/modules/booking/pages/my-bookings/MyBookingsPage.spec.tsx` | selected files pass in Chromium, Firefox, WebKit |
| Live-stack acceptance | `E2E_BROWSER=chromium pnpm run test-e2e src/modules/booking/__tests__/bookingPermissions.e2e.ts --reporter=list` | the narrow Booking permission journey passes against the worktree stack without opening a blocking HTML reporter |
| i18n extraction | `pnpm run i18n:extract --sync-primary` | new keys appear only in primary English catalogs |
| i18n types | `pnpm run i18n:types` | generated types are current |
| i18n lint | `pnpm run i18n:lint` | exit 0 |
| i18n catalog check | `pnpm run i18n:check` | exit 0 |
| Type check | `pnpm tsc` | exit 0, no errors |
| Lint | `pnpm lint` | exit 0, no fixes applied |
| Patch check | `git diff --check` | no output |

## Suggested executor toolkit

- Read `DevDocs/DeveloperNotes/RestApiV2Collections.md` and
  `DevDocs/DeveloperNotes/SecurityAndPermissions.md` before backend work.
- Use the `codebase-design` skill if available when defining the generic
  registration/manager boundary; keep the module deep and Booking-free.
- Use `tdd` if available for the resolver, invariant, query filtering, and
  controller contracts.
- Use `react-testing-library` for generic editor and page unit tests.
- Read and follow the `rspace-browser-tests` skill and its reference before
  editing any `*.spec.tsx` file.
- Read `src/main/webapp/ui/src/__tests__/e2e/AGENTS.md` and use the
  `rspace-dev-stack` skill before adding or running live-stack Playwright
  acceptance. Start the full stack only for the final gate. Select Chromium
  with `E2E_BROWSER=chromium`; this suite does not honor `--project` through
  pnpm. Always pass `--reporter=list` from an agent to prevent a failing test
  from serving a blocking HTML report.
- Before editing `routes.ts`, `router.tsx`, or links to the moved Add item route,
  run these repository-required TanStack guidance commands:

  ```bash
  pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core
  pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/navigation
  pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/type-safety
  ```

## Scope

### In scope

Create or modify only these areas and their directly corresponding tests:

- `CONTEXT.md` and `DevDocs/adr/0008-resource-role-permissions.md`, only to
  reconcile the accepted decisions already fixed in this plan
- `src/main/java/com/researchspace/model/resourceaccess/`: new generic entities
  and value types
- `src/main/java/com/researchspace/dao/resourceaccess/` and
  `src/main/java/com/researchspace/dao/hibernate/`: new generic DAO plus the
  generic correlated-membership query support
- `src/main/java/com/researchspace/service/resourceaccess/`: scheme registry,
  resolver, transactional manager, protected-resource adapter, and audit deltas
- `src/main/java/com/researchspace/model/collection/`: only the trusted query
  constraint and access-spec declarations needed by this plan
- `src/main/java/com/researchspace/dao/query/`: only duplicate-safe compilation
  of that internal selector
- `src/main/java/com/researchspace/api/v2/resource/`,
  `src/main/java/com/researchspace/api/v2/controller/`,
  `src/main/java/com/researchspace/api/v2/openapi/`, and
  `src/main/java/com/researchspace/api/v2/config/`: generic registration,
  access/directory routes, caller fields, audit capability, OpenAPI
- `src/main/java/com/researchspace/model/booking/`,
  `src/main/java/com/researchspace/booking/`, and the existing Booking REST v2
  controllers: Booking scheme, adapter, defaults, own-booking authorization
- `src/main/java/com/researchspace/model/inventory/Instrument.java` only if the
  safe relationship summary needs an existing property accessor; do not add
  Booking permissions to Inventory entities
- `src/main/java/com/researchspace/service/inventory/InstrumentEntityApiManager.java`
  and `impl/InstrumentEntityApiManagerImpl.java`,
  `impl/InventoryBulkOperationHandler.java`, the v1 Instrument/bulk request
  controllers/models, and actor/subject propagation
- one new Liquibase changeset under `src/main/resources/sqlUpdates/` and its
  include in `liquibase-master.xml`
- `src/main/resources/hibernate.cfg.xml`, only to register the three new mapped
  entity classes
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.core.json`
  for backend problem/validation text consumed by `JsonMessageSource`
- `src/main/webapp/ui/src/modules/common/resource-access/`: new generic editor,
  transport, schemas, hook, and tests
- existing Booking domain/page/story/mock/test/browser files under
  `src/main/webapp/ui/src/modules/booking/` that consume roles, capabilities,
  defaults, leave, safe targets, or the selected editor
- `src/main/webapp/ui/src/Inventory/components/ContextMenu/TransferAction.tsx`,
  its test, `src/main/webapp/ui/src/stores/models/Search.ts`, and the relevant
  Inventory English catalog
- Booking/common frontend English catalogs and generated i18n types
- corresponding new/updated tests under `src/test/java/com/researchspace/`
- `src/main/webapp/ui/src/modules/booking/__tests__/bookingPermissions.e2e.ts`
  and only module-local Booking page objects/fixtures required by that narrow
  live-stack test
- `DevDocs/DeveloperNotes/RestApiV2Collections.md` and
  `DevDocs/DeveloperNotes/SecurityAndPermissions.md`
- `plans/README.md`, status update only

### Out of scope

- Migrating Inventory, workspace, Gallery, forms, records, notebooks, or any
  other existing ACL to resource-role permissions
- Reading legacy ACLs as a fallback, dual-writing them, or adding an adapter
  that makes them a second Booking authority
- Continuous Inventory-to-Booking synchronization or read-time permission
  intersection
- Communities, invitations, link sharing, per-user denies/exclusions, multiple
  direct roles, role expiry, or custom role creation
- Event privacy levels, booking approval, recurrence, notifications, or changes
  to scheduling policy
- Bulk access replacement on either All Items or the admin page
- A generic database table containing arbitrary protected resource names/ids
- Reusing or modifying the legacy `components/ShareDialog.tsx`
- Promoting Plan 012 prototype files; production is a tested rewrite
- Replacing Shiro or implementing any part of Plan 001
- A denormalized assignment-availability column, a resource-wide principal
  lifecycle listener, or per-resource audit deltas for user/group lifecycle
- A `BookingConfigurationDeparture` table or any equivalent retained marker
- New dependencies, unrelated i18n conversion, or unrelated dirty worktree files

## Git workflow

- Work on the operator's current delivery branch unless instructed otherwise.
- Commit in reviewable slices aligned with the work packages below. No package
  is independently mergeable or deployable.
- If asked to commit, use imperative messages such as
  `Add generic resource role permissions` and
  `Adopt resource roles in Booking`.
- Do not push or open a pull request unless instructed.

## Steps

The four work packages are dependencies inside this one plan file:

| Package | Result | Depends on | May deploy alone? |
|---|---|---|---|
| A | Generic resource-access module, persistence, trusted query constraint, REST adapter | Plan 012 verdict | no |
| B | Booking backend, defaults, own-booking fallback, safe targets, sysadmin repair data | A | no |
| C | Generic editor, Booking UI, automated accessibility and human review | A, B | no |
| D | Atomic Inventory and Booking ownership transfer, including UI | A, B, C | no |

Only the final combined release gate after Package D permits merge or
deployment.

## Work package A: Generic resource-access module

### Step 1: Confirm the prototype decision and freeze executable contracts

Read Plan 012's Prototype verdict. Copy its selected base variant, borrowed
elements, mobile findings, keyboard behavior, and final copy into a short
implementation checklist at the top of the new generic editor test file. This
is test guidance, not a runtime comment; remove the checklist once every item
has a named test.

Before writing schema or production code, update
`DevDocs/adr/0008-resource-role-permissions.md` and `CONTEXT.md` to match this
plan. Mark ADR 0008 accepted. Remove the departure-marker and archived-group
model, record live principal-state derivation plus snapshot retention, add the
resource-scoped directory and sysadmin Owner-repair rule, and state that current
Shiro remains the identity boundary. Do not add new product decisions while
doing this reconciliation.

Before changing schema, confirm the no-migration assumption against the
database used for implementation:

```sql
SELECT COUNT(*) FROM BookingConfiguration;
```

Expected: zero in the upgrade fixture/database. Do not delete rows to make this
true. If the implementation environment intentionally contains disposable test
data, use a drop/recreate test database rather than mutating a shared database.

Write contract tests first for:

- role-scheme validation and the fake Owner/Manager/Contributor/Reader scheme;
- monotonic capability validation, including a higher role that accidentally
  omits one lower-role capability;
- effective-role precedence across direct, several groups, All users, and
  implicit sysadmin sources;
- the Booking capability matrix;
- access response/replacement/leave endpoint status, ETag, and problem codes;
- strict replacement decoding that rejects forged snapshots and unknown
  read-side fields;
- the expected Booking configuration `effectiveRole`, `roleSources`, and
  capability schema in generated OpenAPI.

Use exact error keys and externalize their user-facing text, including:

```text
errors.api.v2.resourceAccess.forbidden
errors.api.v2.resourceAccess.ownerRequired
errors.api.v2.resourceAccess.stale
errors.api.v2.resourceAccess.ifMatchRequired
errors.api.v2.resourceAccess.invalidGrantee
errors.api.v2.resourceAccess.invalidRole
errors.api.v2.resourceAccess.duplicateGrantee
errors.api.v2.resourceAccess.selfRemovalRequiresLeave
```

For concealed absence/denial, reuse the existing
`errors.api.v2.notFound` problem exactly; do not create an access-specific 404
code or detail that reveals which check failed.

**Verify**:

```bash
mvn test -Dtest=ResourceRoleSchemeTest,ResourceAccessResolverTest,ApiV2OpenApiGeneratorTest -Dfast=true
```

Expected at the initial red gate: the new tests compile and fail only because
the named generic contracts are not implemented. After the contracts exist in
later steps, this exact command must pass.

### Step 2: Add generic persistence and the Booking foreign key

Create the entities and Liquibase schema described in "Persistence and module
boundaries". Use an explicit `granteeKey` (`user:<id>`, `group:<id>`, or
`audience:all-users`) as the uniqueness boundary. Store principal snapshots at
assignment time and refresh them only when that principal is deliberately
selected/saved again; do not rewrite history merely because a display name
changed.

The changeset order is:

1. HALT unless `BookingConfiguration` is empty.
2. Create `ResourceAccess`, `ResourceRoleAssignment`, and any Envers tables.
3. Add indexes for access/scheme, access/grantee key, user, group, and audience
   lookup.
4. Add `resourceAccess_id` to `BookingConfiguration`, its audit table where
   required, the unique/FK constraints, and non-null constraint.
5. Add `defaultSharedWith` to the defaults singleton and seed `ALL_USERS`.
6. Create `BookingDefaultAccessGrantee` with uniqueness, FKs, and indexes. Do
   not create departure or assignment-availability storage.

Map `BookingConfiguration.resourceAccess` with cascade persist and no accidental
cascade delete into users/groups. The aggregate owns its assignment rows with
orphan removal. Validate scheme key, role key, grantee key, and exactly one
principal kind in Java even if the deployed MariaDB version does not enforce a
CHECK constraint.

Add all three entity mappings to `src/main/resources/hibernate.cfg.xml`. Extend
`HibernateConfigurationTest` (or the nearest SessionFactory startup guard) to
assert that `ResourceAccess`, `ResourceRoleAssignment`, and
`BookingDefaultAccessGrantee` are present in the production mapping metamodel.

Add focused entity/DAO tests for duplicate grantee rejection, version bump,
nullable principal FK after hard deletion, snapshot retention, mandatory
Booking FK, and absence of denormalized lifecycle state.

**Verify**:

```bash
mvn test -Dtest=HibernateConfigurationTest,ResourceAccessDaoTest,BookingConfigurationTargetFilterIT
```

Expected: the changeset applies to the test schema and all selected persistence
tests pass. The Booking row count precondition is exercised in a Liquibase test
or documented test fixture and fails safely when non-zero.

### Step 3: Implement the generic scheme registry, resolver, invariants, and audit deltas

Create the generic role/value interfaces and transactional manager. Register
schemes by unique key at startup and fail startup for:

- missing/duplicate Owner or Manager;
- Owner not highest or Manager not second;
- duplicate ranks/role keys/capability keys;
- any higher role that does not include every lower-role capability;
- Owner or Manager without the generic read-resource capability, or a scheme
  with no readable role;
- a required persisted role not declared by the scheme;
- an empty allowed-grantee-kind set, or an assignment kind forbidden for its
  role;
- a production scheme without a registered protected-resource adapter.

Implement resolution as one batched DAO read. The result must preserve every
applicable source for explanation while selecting only the maximum-rank role as
effective. The resolver takes a `User subject`, never an ambient session user.
Use `subject.hasSysadminRole()` for implicit Booking Owner; do not use PI/admin
roles and do not inspect `actor` for authorization.

Implement replacement under a pessimistic aggregate lock:

1. lock the protected resource, access aggregate, and assignment rows;
2. reload and read-lock the represented user's enabled/system-role state and
   relevant UserGroup rows, then re-resolve manage capability inside the
   transaction; return concealed 404 if read access is gone, or 403 if read
   remains but manage access is gone;
3. compare current `version` with parsed `If-Match`; authorization must precede
   this check so a stale-version response cannot become an existence oracle;
4. decode only `granteeKey` and `role`, normalize the complete list, resolve new
   principals server-side, and reject forged/read-only fields;
5. compare semantic before/after maps to detect a no-op;
6. enforce one persisted Owner, per-role grantee kinds, and scheme assignment
   rules;
7. preserve unavailable rows/snapshots only when unchanged;
8. persist once, flush, and return the new version;
9. publish one best-effort after-commit audit delta per semantic assignment
   change using the existing audit convention.

Implement self-removal through the same invariant code and protected-resource
adapter. Reject self-removal inside PUT with the typed
`selfRemovalRequiresLeave` problem so a caller cannot bypass the explicit leave
command by saving a draft that omits their own row.

Add the test-only Contributor/Reader scheme in test sources. Assert the generic
main-source package contains none of `BOOKER`, `VIEWER`,
`BookingConfiguration`, or `Instrument`.

**Verify**:

```bash
mvn test -Dtest=ResourceRoleSchemeTest,ResourceAccessResolverTest,ResourceAccessManagerTest -Dfast=true
mvn test -Dtest=ResourceAccessAuditTrailTest -Dfast=true
mvn test -Dtest=ResourceAccessManagerIT
! rg -n 'BOOKER|VIEWER|BookingConfiguration|Instrument' \
  src/main/java/com/researchspace/model/resourceaccess \
  src/main/java/com/researchspace/dao/resourceaccess \
  src/main/java/com/researchspace/service/resourceaccess
```

Expected: all selected tests pass, including precedence, sysadmin/run-as,
Manager Owner-set immutability, last Owner, unavailable Owner, no-op/version,
stale replacement, direct leave, forbidden audience roles, and fake-scheme
cases. Integration barriers prove that membership removal committed after the
outer controller check but before the manager's locked recheck causes no
mutation, while a concurrent removal waits if the manager already holds the
relevant lock. Audit tests prove no notification for rollback/no-op and one
after-commit notification for a committed semantic delta. The final command
exits 0 with no matches in generic production backend source; test-only fake
schemes live outside the searched paths.

### Step 4: Add duplicate-safe query constraints and registered access support

Introduce the `QueryConstraint` split described above and implement the trusted
correlated-membership constraint and SQL compiler. Callers cannot name it in
RSQL or collection metadata. Compile it to `EXISTS`, correlated on the current protected row's
`resourceAccess.id`, rather than an assignment collection join. Index use must
cover user, group, and audience disjuncts.

Create a documented `ResourceRoleReadAccess` that returns:

- authentication-required for anonymous caller;
- allowed without constraint for an implicit scheme role (Booking sysadmin);
- otherwise `AllowedWhere` containing active direct user, active current-group,
  or All users membership.

Wire it into Booking configuration collection/list/count/single reads. Ensure
the soft-delete constraint remains ANDed. Add DAO integration data with at least
two assignments on one aggregate to prove results/counts are not duplicated.
Test a page boundary where inaccessible rows sort before accessible ones; total
and page must include only accessible rows.

Add one optional `ResourceAccessSpec` to `ApiV2ResourceSpec`, renderer,
collection metadata, and OpenAPI. It must:

- declare fixed field names/types/documentation;
- honor ordinary field selection;
- adapt the service-level `ProtectedResourceAccess` interface without exposing
  REST types to that module;
- batch the current page in one assignment query;
- render fields for list and item responses;
- stay absent from runtime/custom-field catalogs and syntax.

Do not add per-resource conditionals to `ApiV2CrudController` or
`ApiV2ResourceRegistration`; dispatch through the optional spec.

**Verify**:

```bash
mvn test -Dtest=ResourceRoleCollectionQueryTest,ApiV2ResourceAccessTest,ApiV2QueryContractMVCIT,ApiV2OpenApiGeneratorTest
```

Expected: all tests pass; query-count assertions show one page query plus a
bounded caller-field batch query, no N+1, correct count, and no duplicate rows.

### Step 5: Expose generic access, directory, audit authorization, and OpenAPI routes

Create the generic access controller under `/api/v2`, backed only by
`ResourceAccessSpec` registrations in `ApiV2ResourceCatalog`. Parse resource ids
with the registered id parser. For each route:

- resolve the protected resource through the caller-constrained read path first;
- return 404 for unknown resource registration, unknown id, or unreadable id;
- pass represented subject and actor separately to the manager;
- map validation/invariant/concurrency errors to the fixed status/problem codes;
- set/parse strong ETags without accepting wildcards for replacement.

Build GET assignment explanations in bounded batches: load the assigned users'
direct, All users, and same-document assigned-group sources without one query
per row. Return no unrelated group membership or implicit-role detail for
another user.

GET/PUT require the scheme's view/manage capabilities. GET permits Manager;
PUT passes the full before/after comparison so Manager cannot change Owners.
DELETE-me does not expose the assignment list and is safe for lower roles.
The controller DTO for PUT accepts only `granteeKey` and `role`; MVC tests send
forged name/detail/availability/source fields and assert 400 with no mutation.
All three mutation paths rely on the manager's locked authorization recheck,
not solely on the caller-constrained controller lookup.

Create the bounded grantee-directory DAO/manager/controller. The protected
resource route requires assignment-management capability; the Booking-settings
route requires sysadmin. Test ordinary group/co-member scope, sysadmin scope,
current caller, no communities, inactive exclusion, wildcard escaping, minimum
query, cap, stable ordering, and safe fields. Do not route this through generic
`/users` or invent a generic groups collection.

Extend resource audit authorization to require `VIEW_AUDIT` when the resource
has an access registration. Its existence check and denial must still look like
404. Add resource-access audit descriptions to the existing audit result; keep
plain text/structured data free of HTML.

Extend OpenAPI generation and validation with access paths, access schemas,
separate read/write assignment DTOs, headers, errors, and grantee search. Assert
resources without an access spec do not receive access routes.

**Verify**:

```bash
mvn test -Dtest=ResourceAccessControllerMVCIT,AccessGranteeControllerMVCIT,ApiV2AuditControllerTest,ApiV2OpenApiGeneratorTest,ApiV2OpenApiContractMVCIT
```

Expected: all selected tests pass, including 404 concealment, ETag/no-op/stale,
428 missing precondition, last Owner, Manager restrictions, run-as audit actor,
strict write-body rejection, authorization-state races, and a non-access
resource without generated routes.

## Work package B: Booking backend adoption

### Step 6: Register Booking and make configuration creation/access authoritative

Create `BookingConfigurationRoleScheme` and a Booking access registration. Keep
role ordering/capability mapping in this one backend adapter; callers consume
resolved capabilities. Declare users and supported groups valid for every
Booking role, but allow the `AUDIENCE` kind only for Booker and Viewer. Reject
All users Manager/Owner assignments in service and controller tests.

Change `ApiV2BookingConfigurationResource` from a static authenticated policy
to a `description(AccessFunction readAccess)` factory, following
`ApiV2InstrumentResource.description(readAccess)` and its injection from
`InstrumentResourceOperations.java:88-103`. Expose one qualified
`CollectionDescription<BookingConfiguration>` Spring bean built with the
Booking `ResourceRoleReadAccess`; inject that same bean into
`BookingConfigurationResourceOperations`, `BookingConfigurationDaoHibernate`,
the caller-field provider, and safe relationship metadata. Replace the DAO's
static `COLLECTION_QUERY` with an instance constructed from the injected
description. This prevents the REST policy and DAO selector catalog from
drifting. Do not look up Spring beans from the static model class.

Replace Booking configuration authorization:

- list/count/get use the query constraint from Step 4;
- single update requires `canEditConfiguration`;
- single archive requires `canArchiveConfiguration`;
- REST bulk update/archive remains sysadmin-only and never edits assignments;
- create requires an owned eligible Instrument, except sysadmin may use any;
- create persists configuration + aggregate + creator Owner + default Booker
  assignments in one transaction;
- selected defaults use one direct row per selected principal and All users uses
  one audience row;
- direct calls and bulk create use the same creation command and validation.

Add a batched, sysadmin-only Owner-health projection such as
`needsOwnerAttention`. It is true when the structural Owner row exists but no
Owner assignment currently resolves to an enabled user or a group with at least
one enabled member. This field does not weaken the persisted-row invariant.
Implicit sysadmin Owner access lets the administration UI open Repair access and repair
the assignments.

Register the relationship-only `booking-instruments` summary. Update
`ApiV2BookingConfigurationResource` and relationship tests so a Booking Viewer
without Inventory access receives the safe summary, while direct
`/api/v2/instruments/{id}` remains inaccessible and requesting extra Inventory
fields/includes through the Booking relationship is rejected or ignored by the
allowlist without data leakage.

Add a concrete `BookingConfigurationTargetController`/Manager using the same
safe summary for bounded create-target search. It must exclude templates,
deleted Instruments, and Instruments that already have a live configuration,
and enforce owner-versus-sysadmin scope in the query rather than filtering a
page in Java.

Extend `BookingConfigurationDefaults`, its manager/controller, and validation
with `defaultSharedWith` and the selected list. PATCH uses the existing
`configurationVersion`; reject an empty SELECTED result, All users in selected
list, communities, new unavailable principals, and duplicates. Preserve a
previously stored unavailable default grantee until explicitly removed.

**Verify**:

```bash
mvn test -Dtest=BookingConfigurationManagerTest,BookingConfigurationDefaultsManagerTest,BookingConfigurationResourceOperationsTest -Dfast=true
mvn test -Dtest=BookingSettingsControllerMVCIT,BookingConfigurationTargetControllerMVCIT,ApiV2RelationshipContractMVCIT,BookingConfigurationTargetFilterIT
```

Expected: all selected tests pass for ordinary owner creation, non-owner
refusal, sysadmin creation, all three defaults, one-role deduplication, safe
target summary, capability projection, single mutation matrix, and sysadmin-only
bulk behavior.

### Step 7: Apply Booking roles to bookings, blockouts, calendars, subscriptions, and own-booking reads

Replace every `InventoryPermissionUtils`, owned-Instrument, and unrestricted
target permission decision in Booking operations with the Booking configuration
access resolver/query constraint.

For bookings and blockouts:

- list/count/get: current role sees every event on the configuration;
- a requester sees their own `kind=BOOKING` rows even without a current role;
- Viewer cannot create or mutate;
- Booker creates Booking only and edits/cancels own Booking only;
- Owner/Manager create/edit/cancel any Booking and create/manage blockouts;
- direct unreadable id is empty/404, not 403;
- prepare every authorized event with full details and capability-derived
  `canEdit`; do not infer from Instrument owner;
- for a role-less requester, include the safe non-navigable target label but
  omit the configuration relationship/link and every management capability;
- keep scheduling, collision, state-transition, and maintenance policies after
  the authorization gate.

Implement this as a documented `BookingEventReadAccess` returning one database
constraint: role membership correlated through
`bookingConfiguration.resourceAccess.id`, OR
`kind=BOOKING AND requester.id=representedSubject.id`. Convert
`ApiV2TimeSlotBookingResource` to the same injected-description pattern as the
configuration resource and inject that description into
`TimeSlotBookingDaoHibernate`; do not fetch all events and filter them in the
manager. The ordinary role disjunct must work for list, count, item, calendar
source, and relationship reads, while the requester disjunct must be accepted
only on the bookings collection/item endpoints used by My Bookings, not on
configuration/calendar/audit endpoints.

For calendar and subscriptions:

- configuration/schedule/calendar source require any current Booking role;
- subscription status/create/rotate require any current role and personal
  caller as today;
- the public feed continues to resolve the subscription owner and recheck that
  owner's current Booking role on every fetch;
- Viewer can subscribe; a requester without a current role cannot;
- reset-all-subscriptions remains sysadmin-only unless a separate capability is
  explicitly added later.

Register Booking for the generic self-removal command without a marker. Test direct leave
with/without inherited access, direct removal by another user, group membership
loss, All users removal, Inventory transfer, last Owner, rejoin, and read-only
own bookings after every loss path. A role-less requester gets no
configuration/calendar link and `canEdit=false` from the server.

**Verify**:

```bash
mvn test -Dtest=TimeSlotBookingManagerTest,BookingCalendarManagerTest,BookingCalendarFeedGeneratorTest -Dfast=true
mvn test -Dtest=TimeSlotBookingManagerIT,BookingCalendarSubscriptionControllerMVCIT,BookingCalendarFeedControllerMVCIT
```

Expected: all selected tests pass for every role and denial path, query totals
are prefiltered, event details are full for current roles and the requester's
own booking rows, role-less own rows are read-only, and feeds stop after role
loss.

## Work package C: Frontend and accessibility

### Step 8: Build the generic production ResourceAccessEditor from the selected verdict

Create `src/main/webapp/ui/src/modules/common/resource-access/` with:

- Valibot schemas/types for grantees, assignments, sources, caller access,
  version, and problems;
- one transport module for GET/PUT/DELETE-me and directory search;
- React Query keys/hooks; server data stays in React Query, not Zustand;
- `ResourceAccessEditor` implementing Plan 012's selected variant plus only the
  recorded borrowed elements;
- a resource adapter interface supplying scheme key, ordered role keys,
  allowed grantee kinds per role, translated labels/descriptions, resource
  name, item label, and resource-specific leave/notices;
- pure draft/reconciliation helpers with unit tests.

The editor must maintain saved server state and a separate local draft. Save
sends the last ETag in `If-Match`. On 412, retain the user's draft, fetch the
latest document, show the conflict/diff, and require explicit review before a
new save; never automatically replay or overwrite. Cancel restores the saved
document. A successful no-op closes without a misleading change announcement.

Use server assignment-management capabilities to disable/hide actions. Do not
derive Owner/Manager rules by string comparison inside the generic component.
The adapter provides display order and grantee-kind choices so invalid audience
roles are unavailable before Save, but server capability, scheme validation,
and response errors remain authoritative.

Test with both Booking and fake Contributor/Reader adapters. Include direct plus
group effective-role explanation, All users with forbidden privileged role
choices absent, unavailable principal, Manager read-only Owner rows, last Owner,
add/change/remove, leave, conflict, pending, Save/Cancel, focus return, Escape,
accessible names/status announcements, and a narrow responsive render.

Author temporary English with literal `defaultValue` passed to `t()` only; do
not add raw JSX text to production code.

**Verify**:

```bash
pnpm test src/modules/common/resource-access
pnpm tsc
pnpm lint
! rg -n 'BOOKER|VIEWER|Booking|booking-configurations' \
  src/main/webapp/ui/src/modules/common/resource-access \
  --glob '!**/__tests__/**' \
  --glob '!**/*.test.*' \
  --glob '!**/*.spec.*' \
  --glob '!**/*.stories.*'
```

Expected: all selected tests pass, TypeScript exits 0, and lint exits 0 without
fixes. The final command exits 0 with no Booking match in generic production
source. Test/story files are excluded so fake adapters cannot create a false
failure; move any resource-specific text or keys into the Booking adapter.

### Step 9: Adopt capabilities and access flows across Booking UI

Create the Booking editor adapter from Plan 012's final terminology. Update the
Booking configuration schema/read projections to include `effectiveRole`,
`roleSources`, and `capabilities`, and change the target relation to
`booking-instruments`.

Update each surface:

1. **Bookable item page.** Capability-controlled Create booking, edit details,
   Audit tab, Access tab/editor, archive, calendar subscription, and Leave.
   Viewer has read-only schedule/details/subscription; Booker also has Create
   booking; Owner/Manager see management; only Owner archives/manages Owners.
2. **All Items.** Every visible configuration is already server-filtered.
   Add Settings and Access for Owner/Manager, Book only when
   `canCreateBooking`, View for all, and Add item when the caller can create.
   Do not add bulk access.
3. **Add item.** Move the ordinary route from
   `/booking/config/bookable-items/add` to `/booking/bookable-items/add`, update
   route/link tests, and use the concrete
   `/api/v2/booking-configuration-targets` picker; the backend remains
   authoritative. Sysadmin may search all eligible Instruments.
4. **Admin All bookable items.** Keep the sidebar/admin route sysadmin-only,
   remove the `createdBy == me` filter, retain current bulk actions, and use
   capabilities for row controls. Highlight/filter `needsOwnerAttention` and
   offer Repair access so a sysadmin can restore an effective Owner.
5. **Settings.** Add Default shared with radio choices and exact selected
   user/group list using the sysadmin-only Booking-settings grantee route.
   Explain that grants are Booker and apply only to newly created items. Default
   to All users.
6. **My Bookings.** Consume server `canEdit`, remove the item details link when
   the configuration is inaccessible, show a read-only role-loss explanation,
   and never offer edit/cancel without a current role.

Before editing routes or links, run all three TanStack Intent commands listed in
"Suggested executor toolkit". Keep the Administration sidebar rule at
`BookingPage.tsx:89-91` unchanged.

Update Storybook/MSW fixtures to cover Owner, Manager, Booker, Viewer, sysadmin,
unavailable rows, no-effective-Owner repair, stale access, defaults, leave, and
own bookings after voluntary and involuntary role loss. Do not restore the Plan
012 prototype files on the delivery branch.

**Verify**:

```bash
pnpm test src/modules/booking
pnpm tsc
pnpm lint
```

Expected: all selected tests pass and static checks exit 0.

### Step 10: Extract translations and add automated accessibility journeys

Wrap every new production string in semantic translation keys. Run extraction
once with `--sync-primary`, review that only the intended English Booking,
common, and Inventory keys changed, then remove every temporary `defaultValue`
from production calls. Never run `--sync-all`.

Extend the existing browser suites rather than creating another application
harness:

- `BookableItemPage.spec.tsx`: Owner and Manager editor behavior, Viewer/Booker
  controls, inherited-source explanation, last Owner, stale conflict,
  unavailable holder, Save/Cancel, keyboard/focus, live announcements, 320 px,
  forced colors, and reduced motion;
- `BookingPages.spec.tsx`: All Items capability actions, ordinary Add item,
  sysadmin full admin list and Owner repair, no bulk access action, and default
  setting flow where appropriate;
- `MyBookingsPage.spec.tsx`: voluntary leave, removal by another user, group
  loss, and All users loss all retain only the caller's own read-only rows with
  no item/edit link.

Automate every accessibility behavior specified by the selected Plan 012
verdict: accessible names/descriptions, keyboard-only
search/add/change/remove/save/cancel, tab order, focus containment, Escape,
focus return, save/dirty/conflict/error announcements, 320 px overflow/clipping,
forced colors, and reduced motion. Use `expectNoAxeViolations` in Browser Mode
and the existing media emulation helpers. A human still reviews information
clarity and real screen-reader behavior before the final gate.

Run Chromium during development, then all three engines. Use semantic locators,
MSW with strict unhandled requests, and existing page objects. Do not assert
implementation class names or raw query-cache state.

**Verify**:

```bash
pnpm run i18n:extract --sync-primary
pnpm run i18n:types
pnpm run i18n:lint
pnpm run i18n:check
pnpm tsc
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx src/modules/booking/pages/my-bookings/MyBookingsPage.spec.tsx
pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx src/modules/booking/pages/my-bookings/MyBookingsPage.spec.tsx
```

Expected: all commands exit 0; the final command passes in Chromium, Firefox,
and WebKit; no production `defaultValue` remains for the new keys.

## Work package D: Atomic Inventory and Booking ownership transfer

### Step 11: Extend the existing Inventory transfer command and UI

Extend `ApiInventoryBulkOperationPost` with
`transferBookingConfigurationOwnership`, default false. Propagate it through
`InventoryBulkOperationConfig`, the bulk manager/handler, the Instrument
controller, and the transactional Instrument manager. Carry represented
subject and original actor separately. At the v1 controller seam derive the
actor once with `UserManager.getOriginalUserForOperateAs(subject)`; direct
requests return the same user for both fields.

Do not put Booking writes in the controller, React, or an after-commit listener.
The transactional Inventory manager calls `ResourceAccessManager` through its
service interface. Acquire pessimistic locks in one documented order for every
item: Instrument, BookingConfiguration, ResourceAccess, its assignment rows,
then the represented user's role/membership rows used by both checks. Recheck
both the normal Inventory transfer permission and Booking `canManageOwners`
from that locked state. A lost read returns concealed absence; retained read
without ownership-management capability returns the normal per-item forbidden
error. Add/change the incoming owner's direct row to Owner. Remove the outgoing
owner only when that direct row is Owner. Preserve any lower outgoing role and
all unrelated assignments. Save both ownership changes or neither.

Retain current bulk behavior. With `rollbackOnError=true`, one failure rolls
back the request. With false, each record remains its own transaction, one
failure leaves both ownerships unchanged for that Instrument, and later records
continue. An Instrument without a Booking configuration transfers normally.
The new option has no effect on non-Instrument records in a mixed request.

In `TransferAction.tsx`, query the existing Booking configuration collection
once for all selected Instrument ids, capped at the existing 100-record bulk
limit:

```text
/api/v2/booking-configurations
  ?where=target.id=in=(<selected instrument ids>)
  &fields[booking-configurations]=id,target,capabilities
```

Use the normal typed RSQL serializer and URL encoding, not string concatenation.
Inaccessible configurations are absent. Show an unchecked "Also transfer
Booking configuration ownership" checkbox only when at least one returned
configuration has `canManageOwners`. Explain mixed selection. Do not add a
preflight endpoint and do not interpret checkbox visibility as authorization.
The command checks every configured Instrument and returns existing per-record
errors.

Test:

- option absent/false changes Inventory only and preserves old clients;
- true with no Booking configuration;
- incoming lower direct role becomes Owner;
- outgoing direct Owner is removed, outgoing Manager is preserved;
- incoming already Owner and outgoing absent is a semantic no-op for access;
- lack of `canManageOwners` rolls back that Instrument transfer;
- run-as uses subject for authorization and actor for both audits;
- mixed records under both `rollbackOnError` modes;
- the batch UI request is one bounded existing collection query with fixed
  fields, and the backend rejects stale or inaccessible selections.

**Verify**:

```bash
mvn test -Dtest=InstrumentEntityApiManagerTest -Dfast=true
mvn test -Dtest=InstrumentEntityApiManagerIT,InventoryBulkOperationsApiControllerMVCIT
pnpm test src/Inventory/components/ContextMenu/__tests__/TransferAction.test.tsx
pnpm tsc
pnpm lint
```

Expected: all commands pass, subject/actor audit assertions pass, and no failed
item leaves only one ownership model changed.

### Step 12: Document the extension seam and run final security verification

Update the REST v2 and security developer notes with:

- how a protected entity owns a ResourceAccess FK;
- how to define/register a role scheme, service adapter, and REST access spec;
- the mandatory Owner/Manager rules, monotonic capabilities, and persisted-Owner
  invariant;
- how trusted query constraints and batched access fields work;
- ETag replacement and 404 concealment;
- snapshot retention after hard deletion, live status derivation, Owner health,
  and the absence of resource-wide lifecycle fanout;
- the test-only second-scheme requirement for future changes;
- an explicit warning that adopting a new resource requires a migration plan
  for its existing rows and permission semantics.

Run a focused end-to-end security matrix. At minimum, prove each Booking role,
direct/group/All users/combined sources, sysadmin/run-as, unavailable
principals, voluntary versus involuntary loss, direct/list/count concealment,
safe target fields, concurrency, audit, subscription recheck, and atomic
transfer.

Add one narrow feature-local Playwright file at
`src/main/webapp/ui/src/modules/booking/__tests__/bookingPermissions.e2e.ts`.
Read `src/main/webapp/ui/src/__tests__/e2e/AGENTS.md`, use the existing
dynamic-user/API fixtures, and run it against the per-worktree stack. It must
prove the seams most likely to fail when the real backend, database, browser
session, and UI meet:

1. Owner changes assignments and a Booker creates a booking.
2. Viewer can read full event details but cannot create a booking.
3. After any role loss, the requester still sees only their own booking row,
   read-only and without configuration/calendar links.
4. A sysadmin repairs a configuration whose structural Owner rows provide no
   effective Owner.
5. The optional Inventory transfer changes Inventory and Booking ownership
   together.

Keep this as one serial journey with isolated dynamic users. Multi-user browser
contexts must pass the existing `browserContextOptions` fixture to
`browser.newContext()`. Add module-local page objects or fixtures instead of
instantiating normal page objects inside tests. Use semantic locators.

Keep the detailed capability matrix in MVC, DAO, unit, and Browser Mode tests.
The live suite is a release smoke test, not a duplicate matrix.

For the live gate, run `./docker/dev/rspace-dev ps`. Reuse a ready stack or run
`./docker/dev/rspace-dev up` and wait for the app-ready message. Use the app URL
printed by `ps` as `RSPACE_BASE_URL` if it is not the E2E default. After the
test, report whether this plan started the stack and ask the operator before
running the reversible `./docker/dev/rspace-dev down`. Never run `nuke` here.

Then run final focused suites and static checks. If a full DB-backed suite is
available, run `mvn clean test`; otherwise report which DB-dependent suites
could not run rather than substituting `-Dfast=true` for them.

**Verify**:

```bash
mvn test -Dtest=ResourceRoleSchemeTest,ResourceAccessResolverTest,ResourceAccessManagerTest,BookingConfigurationManagerTest,TimeSlotBookingManagerTest,BookingCalendarManagerTest,InstrumentEntityApiManagerTest -Dfast=true
mvn test -Dtest=ResourceAccessManagerIT,ResourceAccessControllerMVCIT,AccessGranteeControllerMVCIT,BookingConfigurationTargetControllerMVCIT,BookingConfigurationTargetFilterIT,TimeSlotBookingManagerIT,BookingSettingsControllerMVCIT,BookingCalendarSubscriptionControllerMVCIT,InventoryBulkOperationsApiControllerMVCIT,ApiV2OpenApiContractMVCIT
pnpm test src/modules/common/resource-access src/modules/booking src/Inventory/components/ContextMenu/__tests__/TransferAction.test.tsx
pnpm tsc
pnpm lint
pnpm run i18n:lint
pnpm run i18n:check
pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx src/modules/booking/pages/my-bookings/MyBookingsPage.spec.tsx
E2E_BROWSER=chromium pnpm run test-e2e src/modules/booking/__tests__/bookingPermissions.e2e.ts --reporter=list
git diff --check
```

Expected: every command exits 0, all selected tests pass, and patch check prints
nothing. The Browser Mode command passes in Chromium, Firefox, and WebKit, and
the live-stack Playwright file passes in Chromium. Inspect `git status --short`:
only in-scope implementation,
documentation, test, generated i18n, and plan-index files may be new/modified.

## Test plan

### Generic backend

- Scheme validation: required Owner/Manager, ordering, monotonic capability
  inclusion, duplicate keys/ranks, read capability, allowed grantee kinds,
  implicit role, fake lower roles.
- Resolution: direct, multiple active groups, unavailable group, All users,
  direct-lower-than-inherited, sysadmin, run-as, no role, batched pages.
- Replacement: add/remove/change, one direct role, duplicate grantee, Manager
  Owner immutability, last persisted Owner, unavailable/zero-member Owner, no-op,
  version, stale, missing If-Match, self-removal, strict write DTO, forged
  snapshots, and audience-role restrictions.
- Query: filtering before pagination/count, duplicate assignments, direct item
  concealment, group membership changes, dynamic future All users.
- Identity state and audit: live disabled-user and group-membership resolution,
  hard-deleted-group snapshot retention, no assignment mutation or
  resource-level lifecycle delta, assigned-user explanation redaction,
  authorization-state race barriers, actor versus represented subject, no-op
  and rollback audit suppression, and one after-commit notification on success.
- REST/OpenAPI: route only for registered resources, safe errors/headers,
  resource-scoped directory, separate read/write assignment schemas,
  access-spec fields separate from runtime fields, fake scheme.

### Booking backend

- Every cell in the role matrix for configuration, Booking, blockout, calendar,
  subscription, audit, access, and archive.
- Creation by Instrument owner, refusal for shared non-owner, sysadmin any,
  creator Owner, all default modes, selected duplicates, unavailable defaults.
- Independence: Booking Viewer without Inventory access sees only the safe target
  summary and full authorized Booking events; Inventory endpoints remain denied.
- Own-booking fallback: own rows only, read-only without a current role,
  voluntary leave, group/All users loss, other-person removal, transfer removal,
  rejoin, and no configuration/calendar disclosure.
- Inventory transfer: option false/true, no config, lower incoming role,
  outgoing role variants, permission failure, per-item bulk atomicity, audits.
- No privacy/redaction branch for authorized roles.

### Frontend

- Generic editor with Booking and fake adapters, all draft operations,
  inheritance, unavailable status, capability restrictions, Owner invariant,
  ETag conflict recovery, explicit Save/Cancel, focus, announcements, 320 px,
  forced colors, and reduced motion.
- Item/All Items/admin/settings/Add/My Bookings/transfer surfaces consume server
  capabilities and never reproduce the role matrix.
- Strict MSW assertions verify field projections, If-Match, atomic PUT body,
  DELETE-me, selected defaults, and transfer option.
- Browser tests cover every named accessibility behavior in all three engines;
  the live-stack Chromium smoke test covers assignment, role enforcement,
  role-loss reads, sysadmin repair, and coordinated ownership transfer.

## Done criteria

- [ ] Plan 012 is DONE and every Prototype verdict field is complete.
- [ ] ADR 0008 is accepted and both intent documents match this plan's fixed
  vocabulary and security decisions.
- [ ] Generic production backend code contains no Booking role/resource branch;
  a second test-only scheme passes.
- [ ] Every registered role scheme passes monotonic capability validation.
- [ ] Booking rejects All users Owner/Manager while permitting its Booker/Viewer
  audience assignments.
- [ ] Generic frontend editor renders Booking and fake schemes without role-name
  conditionals.
- [ ] Every Booking configuration owns one versioned ResourceAccess aggregate
  and at least one persisted Owner.
- [ ] User/group/All users/implicit sources resolve correctly; one direct role
  never reduces inherited access.
- [ ] GET/PUT/DELETE-me and both resource-scoped grantee searches follow the
  documented status, ETag, concealment, and capability contracts.
- [ ] PUT accepts only grantee key plus role; forged snapshots/read fields are
  rejected, and unavailable assignments can only remain unchanged or be
  removed.
- [ ] Transactional mutation rechecks capability from locked user, role,
  assignment, and membership state; race tests prove a revoked grant cannot
  authorize a later write.
- [ ] List/count/item authorization happens in the database query before
  pagination; tests prove totals and no duplicates/N+1.
- [ ] Booking permissions no longer depend on Inventory read/share/owner state,
  except the creation-owner check and explicit opt-in transfer command.
- [ ] Safe Booking target responses cannot expose additional Inventory fields.
- [ ] Every capability-matrix operation is covered and enforced by backend;
  frontend controls use returned booleans.
- [ ] The requester retains read-only access to their own booking rows after
  every role-loss path, with no retained configuration permission or marker.
- [ ] Unavailable principals remain visible/auditable and can satisfy Owner
  invariant without granting access; sysadmins can identify and repair
  configurations with no effective Owner.
- [ ] Optional Inventory/Booking ownership transfer is atomic per item and
  audited; default false preserves old clients.
- [ ] Global Default shared with starts at All users and all three modes work.
- [ ] Ordinary management lives on All Items/item pages; admin list remains
  sysadmin-only, sees all items, and has no bulk access action.
- [ ] Audit, calendar, subscription, privacy, and run-as behavior match the plan.
- [ ] Audit tests prove no event on rollback/no-op and one after-commit notify on
  success without claiming atomic or durable sink delivery.
- [ ] Automated accessibility checks cover every named function, human
  screen-reader review is recorded, and all three Browser Mode engines pass.
- [ ] The narrow live-stack Playwright suite passes in Chromium.
- [ ] `git diff --check` prints nothing and only in-scope files changed.
- [ ] REST v2/security developer notes are updated.
- [ ] `plans/README.md` marks Plan 013 DONE.

## STOP conditions

Stop and report; do not improvise if:

- Plan 012 is not DONE, its verdict is incomplete, or prototype-only files are
  still on the delivery branch;
- any existing BookingConfiguration row must be migrated in a real target
  environment;
- a protected entity cannot own/enforce a real FK to ResourceAccess;
- a scheme needs non-monotonic capabilities while resolution still selects one
  highest role;
- the query layer cannot express duplicate-safe correlated membership without
  filtering after pagination/count and the generic EXISTS extension cannot be
  added in scope;
- safe Booking target details would require granting normal Inventory read or
  exposing unrestricted relationship fields;
- a Manager can change the persisted Owner set, or implicit sysadmin is needed
  to satisfy the Owner invariant;
- DELETE-me would require a prior access-document GET for Booker/Viewer;
- the access editor needs a Booking role-name conditional in generic source;
- Inventory and Booking ownership cannot be changed in one transaction per
  Instrument through the existing bulk path;
- hard-deleted group assignments cannot be retained through `ON DELETE SET
  NULL` plus snapshots without a resource-wide lifecycle fanout;
- an unauthorized direct access/audit/calendar/booking route returns a
  distinguishable 403 instead of 404;
- access fields can only be implemented through a second broad caller-field
  framework or by exposing them as runtime custom fields;
- any step's verification fails twice after one reasonable correction;
- implementation requires migrating another permission model, adding a
  dependency, or modifying an unrelated dirty file.

## Maintenance notes

- A future adopter must supply a role scheme, entity FK, registered adapter,
  query access binding, caller projection, UI adapter, migration strategy, and
  the same fake/contract coverage. Registering a scheme alone is insufficient.
- A future scheme may define a role without read access, but it must keep Owner
  and Manager readable and ensure every operation granted to a non-reading role
  is meaningful without resource visibility. The query predicate already
  filters on the scheme's readable-role keys.
- If communities become grantees, treat that as a new principal kind with
  lifecycle, directory, query, audit, and uniqueness design, not as a Group.
- Reviewers should scrutinize represented subject versus actor, query placement,
  unavailable-principal FKs/snapshots, safe Instrument projection, no-op audit,
  and per-item transfer transaction boundaries.
- Continuous Inventory sync remains deliberately rejected: it makes Booking
  assignments unstable under an unrelated authority, creates conflict/loop
  rules, and prevents a Booking-only Viewer. The opt-in transfer command covers
  the one user-intent event where coordinated ownership is useful while leaving
  every later permission change independent.
