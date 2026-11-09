# REST API v2 queries and access

## Compile and combine query predicates

`RsqlCollectionQuery.Subquery` keeps nested subqueries in the scope where their correlated aliases
are visible. Apply a compiled predicate with `Predicate.applyTo(query)`, or
`Predicate.applyTo(nestedBuilder, rootQuery)` inside an existing subquery. Both forms register
nested subqueries and bind parameters on the root query.

Combine compiled predicates with `Predicate.and(...)` or `Predicate.or(...)`. They ignore null
predicates and reject conflicting parameter or subquery bindings. Use one
`RsqlCollectionQuery.newCompilation()` session when compiling several predicates for the same
operation. Its allocator keeps generated names distinct across nested scopes. Use distinct prefixes
when combining independent compilations. Keep sessions local to an operation, never on a reusable
DAO.

## Signed global IDs

A global ID in a relationship filter or write is a prefix and an unversioned ID. The ID can be
negative, as in seeded and baseline rows such as `US-3`. The API rejects versioned IDs such as
`IN12v2`.

For booking domain rules and API examples, see [Booking](Booking.md).

### Inventory container read constraints

`InventoryContainerReadConstraint` is a trusted constraint, not caller filter syntax.
`ContainerReadAccess` excludes deleted containers and allows direct Inventory read access or access
through an active immediate child Container, Instrument or SubSample. Role visibility applies to the
container itself, not its children. System administrators can read every active container, including
workbenches. The query compiler uses correlated subqueries so filtering, totals and pagination use
the same rule.

Instrument location is the nullable `parentContainer` to-one relationship to `containers`, bound to
`parentLocation.container.id`. Use relationship expansion to read its name and global ID. An
authorization-aware target resolver returns no target for an unreadable container, which renders the
relationship as `null`. Instrument scalar summaries carry only the parent container ID; the target
registration controls its visibility and expansion.

## Query configuration

Use the [Table List adapter guide](TableListAdapters.md) to connect a frontend table to a collection
endpoint.

The resource definition also sets the allowed queries. Clients can use:

| # | Requirement |
| --- | --- |
| 1. | `where` filters with RSQL. |
| 2. | `sort` with public field names. Use `-` for descending order. |
| 3. | `page` and `limit` for pagination. |
| 4. | `fields[resource-name]` for an inclusive field list. |
| 5. | `exclude[resource-name]` for an exclusive field list. |
| 6. | `depth` for relationship expansion. |

Examples:

```text
?where=enabled==true;name==*rotor*&sort=name,-createdAt
```

```text
?fields[widgets]=name,enabled
```

The OpenAPI fieldset parameters include nested relationship targets. A depth-2 read can therefore
name both `fields[instruments]` and `fields[containers]` in one request.

Responses always contain the ID. Field access rules can hide fields. Clients cannot use hidden
fields in `where` or `sort`.

### Current user in relationship filters

On a relationship whose targets are all `users`, its ID selectors accept `me` as the authenticated
effective subject's numeric user ID:

```text
?where=createdBy.value==me
?where=createdBy.id=="me"
?where=createdBy.value=in=(me,42)
```

`.value` and `.id` mean the same thing. The alias works in eligible mixed `=in=` lists when the
selector's existing operator allowlist permits `=in=`. Quoted and unquoted exact lowercase `me` are
equivalent. Other spellings, including `ME`, are ordinary values and normally fail numeric ID
parsing.

During run-as, `me` means the effective subject, not the administrator acting for that subject.
Collection list, count, filtered bulk update, and filtered bulk delete requests all resolve it the
same way.

### Per-actor fields

A collection can publish caller-specific fields that cannot appear in OpenAPI. Each field kind has a
namespace, provider, identity scheme, and catalog route: `/api/v2/{resource}/fields/{namespace}`.
Use `q` for free-text catalog search. The document publishes these namespaces and routes in
`x-rspace-runtime-fields`. Clients must use the published routes instead of constructing them.

Instruments publish two:

| # | Requirement |
| --- | --- |
| 1. | `customFields` are copied from a template, so every instrument made from that template has the same field. The template field's Global ID identifies the definition and survives renames. |
| 2. | `extraFields` belong to one record. With no definition row, the pair (exact name, declared type) identifies a definition; a rename changes it. The ID encodes both, as `XF` plus a type letter plus the hex of the UTF-8 name, so it round-trips through an RSQL selector, a comma-separated `ids` list and a URL path without escaping. Clients treat it as opaque and display `label`. Discovery, saved-view hydration and projection preserve exact names, and a runtime-field filter resolves its selector by exact name, including case, independently of database collation. The encoding handles punctuation. A selector ends at a space, `,`, `=`, `;`, `(` or `"`, so user-supplied names such as `Max voltage (V), peak` cannot travel literally. RSQL parses UTF-8 values and selectors: a field named `温度計` holding `摂氏 4 °C` filters like any other. |

After resolution, use either namespace the same way. Filter with `where`, select columns with
`fields[resource]`, and read values under the namespace's response object. Definitions may share
labels, so a custom field identifies its template as `source`. An extra field has no owner and
leaves it empty.

Extra fields with the same name but different types, such as text and number, are separate
definitions. Queries restrict by concrete field entity. Numeric comparisons therefore never convert
or accidentally match the text field's values.

The API queries runtime-field values from the database, including ad-hoc extra fields. Instrument
custom-field projection selects only the instrument ID, definition ID, declared type and stored data
for the authorized page IDs. It does not hydrate field entities or their instrument and template
associations. A missing value row omits the definition. An existing row with null or empty data
publishes null through the same runtime-field codec used for populated rows.

### Text matching on a runtime field or a relationship target

Both operators match text in the database without case sensitivity. They work independently of
Hibernate Search. `=contains=` matches a substring anywhere. `=like=` splits on whitespace and
requires every word as a substring, in any order, including partial words. For example, `=like=lab
BSL-2` matches `Level BSL-2 lab`. See `InstrumentCustomFieldTextSearchIT` for examples.

### Fields reached through a relationship

The `where` parameter's `x-rspace-filter.selectors` map (a reference into
`components.x-rspace-filters`) publishes immediate target scalar fields,
keyed by `<relationship>.<field>`. `viaTitle` carries the relationship's title:

```json
"selectors": {
  "target.name":     {"schema": {"type": "string"}, "operators": ["==", "=contains=", "=like="],
                      "wildcards": true, "title": "Name", "viaTitle": "Booking target"},
  "target.globalId": {"schema": {"type": "string"}, "operators": [], "wildcards": false,
                      "title": "Global ID", "viaTitle": "Booking target"}
}
```

A relationship declaration makes its target fields available as optional table columns. Fields with
operators are also available as filters.

Keep wildcard equality and `=like=` as separate operations. Wildcard equality anchors the complete
value. `=like=` finds words anywhere in the value. Client labels must show this difference.

`operators` is empty for a field with no backing column, such as a global ID derived from an ID. The
API publishes the field so clients can display it, but `where` rejects it as unsupported.
Entries with non-empty `operators` can be used in filter expressions. An entry's presence alone
does not make it filterable.

A polymorphic relationship publishes only fields shared by every destination with the same schema
and the intersection of their operators. This avoids columns empty for half the rows and filters
whose meaning depends on the destination.

This selector map covers one relationship hop. It does not include paths such as
`target.parentContainer.name`; the query language does not accept those multi-hop filters.

### Discovering nested relationships for display

Each relationship property in a resource's `Read` component carries `x-rspace-relationship.targets`.
The map identifies every permitted target resource and points to its existing read schema. For
example, `InstrumentsRead.properties.parentContainer` includes:

```json
"x-rspace-relationship": {
  "targets": {
    "containers": {"$ref": "#/components/schemas/ContainersRead"}
  }
}
```

To build a relationship browser, start from the response's read schema, display its properties,
and follow each relationship's target pointer to discover the next resource's properties. Existing
property titles and `x-rspace-access` metadata describe the field at each step. Polymorphic
relationships list every possible target separately; keep those branches distinct because their
fields may differ.

These pointers form a finite graph, including self-references and cycles. Resolve them lazily when
a user expands a relationship. Track pointers on the current traversal path and stop automatic
expansion when a pointer repeats; do not globally suppress the same resource on unrelated branches.
Set a display depth limit as well. The `$ref` values inside this extension are pointers for the
application to resolve against `components.schemas`; standard OpenAPI tools need not resolve
extension contents automatically. The extension is server-owned and overrides any schema annotation
extension with the same name on a read relationship property.

Graph discovery describes possible response shapes. It does not grant access to records or enable
additional query paths. Request `depth` controls how many relationship levels the server expands,
and `fields[resource]` controls selected fields on each resource. Missing or unreadable targets can
render as `null`. Use the original endpoint's `x-rspace-filter.selectors` and its non-empty operator
lists to determine which paths can be filtered. In particular, following several schema pointers
does not make the resulting multi-hop path a supported filter.

### Relationship filter picker metadata

A single-target relationship with a declared global-ID prefix publishes an optional `picker` on its
root selector in `x-rspace-filter`. For example, a `calibrations` resource can declare a
single-target `instrument` relationship to `instruments` with the existing `IN` prefix. The
generated selector includes:

```json
"picker": {"resource": "instruments", "identity": "globalId", "globalIdPrefix": "IN"}
```

Public target fields such as `instrument.name` appear as selectors beside it. The shared instruments
source supplies its picker through the application registry.

The client selects an application-owned source using `resource`. Metadata never supplies a search
URL. A new collection opts in through its declared relationship and existing target prefix. It needs
no collection-specific query compiler. Register its source in the client registry if users need
search and selection controls. Unprefixed and multi-target relationships omit the picker descriptor
and retain their existing typed identity and compatible target-property selectors. `target.value`,
`target.relationTo`, and the exact `createdBy.value==me` spelling remain compatible. Older clients
can ignore `picker`.

Target-property and runtime traversal remain one hop. Runtime traversal requires one target;
existing multi-target identity and common scalar queries remain valid. A missing or inaccessible
identity matches `exists=false` but never a negative identity comparison. A runtime predicate
through a relationship always requires a readable target, including `exists=false`; that operator
then means the readable target has no nonempty value. Page and count queries share these predicates.
Request copies made during access checks or projection narrowing must preserve
`ResourceRequest.runtime()`, which contains the resolved runtime selectors needed by the query
compiler.

Runtime catalogs retain `/api/v2/{resource}/fields/{namespace}` routes. For client catalog
allowlisting and redirect rejection, see [Relationship
filters](TableListInteractions.md#relationship-filters). Target-only aliases may delegate catalogs;
never infer the catalog resource from `viaResource`.

Direct, relationship, and runtime predicates share the 50-comparison budget. Argument, nesting,
LIKE, catalog-ID, page-size, and projection limits continue to apply independently; adding picker
metadata does not raise them.

### Scalar read documents

Collections may declare a `ScalarReader` on the spec (`.scalarReader(reader)`); its `read` and
optional count-free `slice` project scalar rows through the same collection query compiler and
pagination as entity reads.
`CollectionQueryExecutor.projectedPage()` avoids hydrating the Inventory aggregate. The renderer
validates supplied declared objects and arrays before applying selection and per-row field access.
Instruments project `InstrumentReadSummary` rows, including the parent container ID, for list,
single and relationship reads. Runtime providers that support relationship ID projection receive
authorized page IDs through a direct `valuesForIds()` call, retaining their transaction advice.
Other providers must explicitly implement `valuesForAuthorizedIds()` and may trust those IDs only
after the collection read policy has selected the page. Scalar read documents may supply a to-one
relationship's target ID (or a `ResourceReference` for polymorphic relationships). The renderer
batches those IDs through the target registration with request-scoped caching, applies its read
policy even at depth zero, and renders unreadable targets as `null`. Selected expansions retain
target field access and global IDs. Container relationship reads also project scalar rows, avoiding
hydration of the Inventory aggregate. Entity reads remain available to internal domain callers and
write-side relationship resolution.

OpenAPI read schemas declare optional object maps for projected runtime namespaces, including
eligible relationship prefixes. The document root stays closed. The maps accept caller-specific
field IDs without publishing them in the cached schema. Runtime projection properties are excluded
from create and update schemas.

## Access control

Access control has three layers:

| # | Requirement |
| --- | --- |
| 1. | `AccessPolicy` controls resource operations. |
| 2. | Field configuration controls field reads and writes. |
| 3. | The manager controls domain rules that need stored data or permission services. |

The default policy requires authentication. Configure anonymous reads explicitly. REST API v2
accepts an `apiKey` header or an OAuth bearer token. REST API v2 usually ignores browser cookies. A
request with only a session cookie is usually anonymous.

`POST /api/v2/oauth/tokens` is the only credential-minting exception. It uses an existing
authenticated browser session to create a UI OAuth token. It does not create a browser session. It
ignores API keys and bearer tokens. Permissive API CORS does not apply to this route. During run-as,
the token's `sub` is the effective user and its RFC 8693 `act.sub` is the original administrator.
Every REST API v2 UI token carries the UI audience and an opaque `sid`. The `sid` binds it to the
current browser authentication context.

Inventory API v1 requests may use a REST API v2 UI token only when the matching authenticated
browser session is present. The v1 OAuth authenticator delegates these tokens to the v2 validator,
which checks the live subject, run-as actor, and session context; it does not create a second
stateless Shiro login. The legacy Inventory token endpoint remains available to public document
views.

The authentication interceptor resolves supplied credentials and passes the resulting or anonymous
subject to an access function. `ApiV2EndpointSpec` supplies the controller-level policy. A handler
with no contributed spec defaults to authenticated access. Public controllers contribute
`AccessFunction.anyone()`. The generic CRUD controller also contributes `anyone()` at this point.
Its selected `ApiV2ResourceSpec` operation makes the final decision from the collection policy.
Invalid supplied credentials are rejected even when the endpoint is public.

The `apiKey` credential header has no scheme. Unlike `Authorization`, HTTP tooling will not redact
it. Keep it out of logs and URLs.

When both credential headers are present, the API key wins. A long-lived `apiKey` on a shared HTTP
client takes priority over a fresh bearer token added later. The request authenticates as the key's
owner. Send only one credential.

These policy fields are independent:

| Policy field | Operation |
| --- | --- |
| `readAccess` | List, count, read by ID, relationship resolution, and the visible scope of updates/deletes. |
| `createAccess` | Single create and bulk create. |
| `updateAccess` | Single update and bulk update. |
| `deleteAccess` | A hard delete, including an explicitly selected permanent single delete. |
| `softDeleteAccess` | A configured soft-delete or archive command, including its bulk form. |

`softDeleteAccess` does not replace `deleteAccess`. A soft-delete operation must use
`AccessContext.Operation.SOFT_DELETE`. A hard-delete operation must use `DELETE`. A resource may opt
into both on the same canonical item URI only when an explicit request option such as
`permanent=true` selects the hard delete. Never infer a destructive command from the row's current
state; retrying an ordinary soft-delete request must remain safe.

An access function can return a row constraint. The framework combines it with the client filter for
list, count, read by ID, and bulk operations. Updates and deletes intersect READ and write
constraints. Single writes select `id AND scope` inside the mutation transaction; no match returns
404. The framework removes duplicate restrictions from managers.

### Resource-role protected collections

The [resource-role permission model](SecurityAndPermissions.md#resource-role-permissions) defines
role ordering, persisted Owners, live identity resolution, and authority for transactional
mutations. Register a `ResourceAccessSpec` in the resource's `ApiV2ResourceSpec` after its
service-layer scheme and `ProtectedResourceAccess` adapter are registered. Resource adapters supply
the capability and Owner-health read fields they support. Use the trusted
`ResourceRoleMembershipConstraint` for collection selection; resolve capability fields for the page
in batches. A missing or unreadable protected resource returns the same `404`.

Access routes are enabled by the resource's `ResourceAccessSpec`:

| Method and suffix | Contract |
| --- | --- |
| `GET .../{id}/access` | Reads the access document and its `ETag`; requires the resource's view-access capability. |
| `PUT .../{id}/access` | Atomically replaces all direct assignments using matching `If-Match`; requires assignment-management capability. A stale version returns `412`. |
| `DELETE .../{id}/access/me` | Removes the caller's direct assignment subject to service-layer leave rules. |
| `GET .../{id}/access/grantees` | Searches assignable principals with `q` (at least two characters) and optional `limit` (1–50, default 20) within the resource's assignment-management capability. |

The bounded grantee lookup returns `{docs: [...]}` without paging metadata.

Only opted-in resources expose these routes; ordinary collections return `404`. Access documents are
private and must not be cached. OpenAPI includes the routes only for registered access support.

The shared `ResourceAccessEditor` uses the access document, capabilities, grantee search, and
concurrency contract. Resource adapters supply translated role labels and notices.

The editor requires `mutationOptions` with `replaceAccess` and `leaveAccess` factories from the
resource's generated module. Callers inject these alongside the registry entry, keeping invalidation
in the generated rules. The common access-operation types depend on the shared generated access
models, so a collection can opt in at a later layer.

The React `ResourceAccessEditor` keeps its draft within one resource. Its registry entry supplies
generated access, replacement, leave and grantee-search operations and query keys. Replacement sends
the quoted `version` from the draft's original access document as `If-Match`. Generated mutation
options invalidate the resource list, count, record, access and audit queries. These keys contain no
identity scopes or bearer tokens. The shared token provider reloads the page when the identity
changes. Structured error details come from `ApiV2ProblemError.problem`, which may be absent for an
unstructured HTTP failure. A stale replacement refreshes the latest document and merges the draft,
keeping saving blocked until recovery succeeds. Successful Leave closes its confirmation, and its
optional navigation callback runs only while that editor session remains mounted.

Do not run authorization functions during OpenAPI generation. The `INHERITED` preset uses the
matching collection operation's documentation. Every other access function must include
`AccessDocumentation` with a PascalCase name, its authentication rule and denial codes. The name
keys the policy in the OpenAPI document, so reuse a name only for the same policy.

A custom `createAccess` function runs after structural parsing and before relationship resolution
and persistence. For one create document, `context.requireInput()` returns the complete immutable
`ParsedDocument`. For bulk create, `context.inputs()` returns all documents in input order.

```java
AccessFunction documentedCreateAccess =
    AccessFunction.documented(
        "WorkspaceMemberCreate",
        "A member can create a widget only in a workspace of that member.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN),
        context -> {
          if (!context.isAuthenticated()) {
            return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
          }
          Long workspaceId = (Long) context.requireInput().values().get("workspaceId");
          return membershipChecker.isMember(context.user(), workspaceId)
              ? AccessResult.allowed()
              : AccessResult.denied(AccessPolicy.FORBIDDEN);
        });
```

Create access cannot return a row constraint because a row does not exist. For bulk create, use
`inputs()`. Do not use `requireInput()`. A custom field function uses `Field.creatableBy(...)`. It
receives one document through `requireInput()`.

### Stateless requests

The servlet filter removes cookie headers before Shiro processes ordinary REST API v2 requests. It
also prevents servlet-session reads and writes. It preserves the existing session for `POST
/api/v2/oauth/tokens` and for bearer JWTs that declare the dedicated REST API v2 UI audience. The
audience declaration is only a routing hint. Authentication still verifies the JWT signature, token
row, subject, actor, and session binding before any controller runs. API keys and external OAuth
tokens remain stateless and cannot inherit run-as.

The authentication interceptor does not log in to or log out of Shiro. Endpoint metadata selects
browser-session authentication only for the token route. A session-bound UI token authorizes as its
subject and records both actor and subject for delegated security events. Other credentials never
consult the browser identity.

Browser authentication reloads the subject and originating actor on every request and rejects either
identity if its account is disabled or locked. This applies to UI token creation and to each use of
a session-bound UI token, including run-as.

Starting and ending legacy Shiro run-as rotates the opaque browser authentication context. Tokens
from before either transition therefore fail immediately, even if their JWT expiry has not passed.
The UI mints a fresh v2 token after each page load. It does not trust a token retained in session
storage across a run-as redirect. Authenticated responses default to `Cache-Control: no-store,
private, no-transform`. Public configuration and OpenAPI responses keep their explicit cache
policies.

The legacy UI-token endpoint refuses token creation during run-as. REST API v1 validates
session-bound REST API v2 UI tokens against the matching live browser session. AuthStore keeps the
current token in session storage for legacy API clients. The v2 token provider keeps its page token
in memory. Every page initialization mints the token appropriate to its current authentication
context.

### Rate limits

REST API v2 uses separate throttle beans from REST API v1 but shares its deployment properties.

REST API v2 uses thread-safe Bucket4j buckets with greedy refill. Before authentication, a source
bucket limits credential failures and anonymous traffic. After successful authentication, each
stable actor-ID bucket applies the 15-second, hourly, and daily limits atomically. A separate global
bucket and minimum request interval apply only to authenticated traffic. Raw, unvalidated
credentials are never bucket keys and cannot consume the authenticated global allowance.

Deployment property meanings are documented under [API
throttling](../public/RSpaceConfiguration.md#api-throttling). REST API v2 supports
`api.throttling.enabled`, `api.user.limit.15s`, `api.user.limit.hour`, `api.user.limit.day`,
`api.global.limit.15s`, and `api.global.minInterval`.

When throttling is enabled, responses contain `X-Rate-Limit-*` usage headers. Rejected requests
return 429 and an API problem body. Every throttle interval (`quarter_min`, `hour`, `day`) sends
`X-Rate-Limit-Limit-{interval}`, `X-Rate-Limit-Remaining-{interval}` (whole requests), and
`X-Rate-Limit-WaitTimeTillNextRequestMillis-{interval}`. Unlike API v1, the 15-second interval is
suffixed too. `X-Rate-Limit-WaitTimeMillis` and `X-Rate-Limit-MinWaitIntervalMillis` give the
largest value across the intervals. OpenAPI lists those two on each successful response, the shared
429 response lists every header, and the top-level `x-rspace-rate-limits` extension names the
intervals and the per-interval headers.

Authenticated buckets use a SHA-256 fingerprint of the stable actor ID. Public, anonymous, and
invalid-credential buckets use a fingerprint of the client address. Bucket keys retain neither user
IDs nor client addresses.

The address resolver accepts `X-Forwarded-For`. Configure each trusted proxy to replace an untrusted
`X-Forwarded-For` header.

The caller bucket store keeps at most 10,000 keys. The separate global bucket supplies the hard
limit when caller buckets are removed.

### Cross-origin requests

`api.permissiveCors.enabled` is a deployment property. It defaults to `false` in
`defaultDeployment.properties`. Setting it to `true` adds cross-origin resource sharing (CORS)
response headers for `/api/v2/**` through `WebConfig.addCorsMappings`, so browser pages on other
origins can read responses. The token route `/api/v2/oauth/tokens` never allows CORS. CORS does not
grant API access.

API availability is separate. `ApiV2Authenticator` checks the `api.available` system setting through
`ApiAvailabilityHandler`, globally and per user for API keys and external OAuth tokens. External
OAuth tokens also require `isOAuthAccessAllowed`. Session-bound UI tokens skip these availability
checks so the RSpace UI keeps working when the public API is off.

A browser app on another origin needs both API availability and `api.permissiveCors.enabled=true`.
Disabled API availability rejects its API key or external OAuth token with `401`. Without CORS
permission, the browser blocks access to the response.

An `OPTIONS` response permits `POST`, `PUT`, `PATCH`, `GET`, `OPTIONS`, and `DELETE`. It permits the
`apiKey`, `Authorization`, `Content-Type`, and `If-Match` request headers and exposes the `ETag`
response header so browser clients can make versioned updates.

### Deviations from REST API v1

Intentional v1 differences: v2 authentication leaves Shiro state unchanged, and external OAuth
applies deployment, user, and OAuth availability settings. Disabled throttling omits the fabricated
rate-limit headers returned by v1. V2 uses fingerprinted, bounded bucket keys and atomic allowance
consumption so concurrent requests cannot exceed the allowance. See [Stateless
requests](#stateless-requests) and [Rate limits](#rate-limits) for the v2 behavior.

## Relationships

For relationship declarations, see
[`@ApiV2ResourceRelationship`](RestApiV2Collections.md#apiv2resourcerelationship).

This example is a polymorphic to-one value:

```json
{
  "enabled": true,
  "timezone": "Europe/Berlin",
  "target": {
    "relationTo": "instruments",
    "value": 123
  }
}
```

Before calling the operations class, the relationship resolver verifies target existence and
effective-subject read access. The operations class receives a `ResolvedResourceReference`. It never
receives an untrusted ID. Rendering repeats target read authorization for every relationship. The
renderer sets the complete relationship field to `null` at every depth if the target is missing or
unreadable. It never falls back to a raw target ID, resource name, or global ID. The entity that
contains the relationship remains readable and remains in list totals.

An ordinary list or count does not join the relationship target or apply its access rule. A filter
that observes a relationship, including its stored ID or a target field, compiles to an authorized
correlated `EXISTS` query. An unfiltered count therefore makes no target-access query. A target
filter cannot reveal hidden references.

Every `CollectionDao` query receives an explicit `RelationshipReadAccess`. The generic collection
manager uses the complete `ResourceRegistry` by default. It evaluates each observed target's read
policy for the effective subject, caching decisions for the query and skipping unrelated targets.

A new manager that extends `AbstractCollectionManager` must pass an
`ObjectProvider<ResourceRegistry>` to the superclass:

```java
protected WidgetManager(
    WidgetDao dao,
    ObjectProvider<ResourceRegistry> resourceRegistry) {
  super(dao, ApiV2WidgetResource.DESCRIPTION, resourceRegistry);
}
```

The provider prevents a construction cycle during resource catalog startup. The registry discovers
each one-hop public scalar target filter from the declared relationship. Adding a relationship needs
no override in a standard manager.

At startup, the registry validates the target resource, entity type, ID type, and query fields. The
HTTP boundary checks access to the source relationship and each target field. The standard
declaration and registry supply the permission checks; no collection-specific target map is needed.

A custom manager that does not extend `AbstractCollectionManager` must pass
`RelationshipReadAccess.forActor(registry, subject)` to its DAO. Mutation filters reuse the read
policy by default. `RelationshipReadAccess.none()` remains a fail-closed option for a lower-level
query that has no registry.

A manager can supply `unrestricted()` only after a domain-specific authorization check. A
persistence adapter must not choose this policy.

Relationship targets are registered collections. Their own read policy governs scalar fields,
reference filters and runtime fields, including negative and missing-value comparisons.

At `depth=0`, a readable target's `value` is its ID. This reference object is also the update shape.
If the client supplies `globalId`, it must agree with `relationTo` and `value`. A relationship can
also accept a global-ID string as update shorthand. Target visibility is still checked at depth 0.

At greater depths, `value` is the expanded target document in the same relationship envelope. The
target registration applies its access and field rules. The API does not accept an expanded response
object as update input.

A response includes `globalId` when the target has a global-ID prefix. For example, an instrument
reference includes `"globalId": "IN123"`. The response retains `globalId` when `value` is expanded.
Targets without a global ID, such as users, omit this field.

Relationship population is request-scoped and breadth-first. The renderer deduplicates target IDs,
groups them by resource type, and issues one authorized `IN` query per type at each requested depth.
The renderer caches resolved and unavailable targets for the request. This bounds projection queries
by depth and target types instead of by the number of returned entities.

## Conditional requests

Versioned routes, including notification-subscription PUT, return an `ETag` and require its current
value in `If-Match`. Notification subscription bodies contain only `enabled`; their opaque tags also
cover effective preference flags. Missing `If-Match` returns 428, malformed tags return 400, and
stale tags return 412. Preserve
`Cache-Control: no-transform` so a compression proxy does not rewrite the validator. Shared
`ApiV2ConditionalRequest` parsing accepts the known proxy alterations: one `-gzip`, `-br`, or
`-deflate` suffix inside the quotes and an optional `W/` prefix. `parseStrongEtag` returns the
normalized quoted tag; numeric version parsing also requires a non-negative integer. Missing
required preconditions return `428`; malformed tags return `400`. Each versioned route checks the
normalized tag against current state. The stale-state response is route-specific; use `412` only
where the route contract defines it. Shared parsing does not choose that status.

## Errors and locale

REST API v2 returns errors as RFC 9457 problem details. The response content type is
`application/problem+json`.

```json
{
  "title": "The request contains an invalid value.",
  "status": 400,
  "code": "errors.api.v2.invalidRequest",
  "invalidParams": [
    {"name": "docs[1].name", "reason": "required"}
  ]
}
```

Every problem contains `title`, `status`, and `code`. `detail` and `invalidParams` are present when
the error has that information.

The `code` and localized `title` are stable for a problem type. With non-empty message arguments,
the server resolves the title from the sibling `<leaf>Title` catalog key and puts the formatted
message in `detail`. A missing sibling title logs a warning and uses the status-generic title.
Without arguments, `detail` is omitted unless a caller supplies an explicit validation detail.
Document violations remain in `invalidParams`.
Authentication errors also contain `WWW-Authenticate: Bearer`.

Spring uses the same problem format when rejecting a request before handler selection, including
unsupported methods, which do not return HTML error pages.

The server uses the deployment locale. It does not select an unavailable locale from
`Accept-Language`.

Every REST API v2 response contains `Content-Language` and adds `Accept-Language` to the `Vary`
header.

### Validation and pagination limits

Errors use localized RFC 9457 problem responses. Query validation rejects unsupported fields,
operators, values and excessive complexity before querying the database. Spring's JSON reader also
limits document size, string size, token count and nesting depth.

`ResourceRequest.Page` enforces a size between 1 and 100 for every adapter, including direct Java
callers. Relationship fallback lookups split larger ID sets into pages of at most 100 and combine
the readable results before applying read overrides. `PageOffsets` shares overflow-safe arithmetic
for one-based pages. Collection requests, runtime-field catalogs and response metadata retain
separate validation, limits, and pagination contracts.

## OpenAPI

The catalog publishes OpenAPI 3.1 at `/api/v2/openapi.json`. A routed resource spec adds its paths
and its schemas. Relationships refer to registered resources and their schemas.

Repeated content is published once under `components` and referenced with `$ref`, so readers must
resolve local references before inspecting a parameter, response, or extension. Standard sections
hold shared query parameters (`<Resource>Where`, `<Resource>WhereRequired`, `<Resource>Fields`,
`<Resource>Exclude`, `<Resource>Id`, and the resource-independent `Page`, `Limit`, `Totals`,
`Max`, `Depth`, `IfMatch`, `IfMatchOptional`, `IdempotencyKey`, and `Permanent`), shared responses
(`CountResult`, `PermanentlyRemoved`, `VersionChanged`, and `<Resource>IfMatchRequired`), and
documented problem examples named by error code. Audit operations share the `AuditStart`,
`AuditEnd`, `AuditSearch`, and `AuditActions` parameters and the `AuditEvents`,
`AuditInvalidRequest`, and `AuditUnavailable` responses. Two custom sections hold extension values: `x-rspace-filters.<Resource>` is the
`x-rspace-filter` catalogue of that resource's `where` parameters, and
`x-rspace-access-policies.<Name>` holds each `x-rspace-access` object and `x-rspace-fields` access
policy under the name its `AccessDocumentation` declares, such as `Authenticated` or
`ContainerRead`. Operations, schemas and fields that apply one policy share one entry, and
generation fails when two different policies declare the same name. A component exists only when an
operation or schema refers to it.

The document service merges Swagger annotations from concrete controllers. It validates the complete
document before returning it.

Production creates the document after a context refresh, caches it privately for one hour, and
supplies an `ETag`. A matching `If-None-Match` request returns 304.

Development creates and validates the document for each request. Responses use `Cache-Control:
no-store`.

Use `OpenApiOperationDocumentation` for resource-specific descriptions and examples:

```java
Map.of(
    ResourceOperation.CREATE,
    OpenApiOperationDocumentation.builder()
        .description("Creates one widget in the selected workspace.")
        .requestExample(Map.of("name", "Centrifuge rotor", "enabled", true))
        .build())
```

Declare each non-standard error in the resource spec. Each mapping:

| # | Requirement |
| --- | --- |
| 1. | Converts the domain exception to the specified HTTP response for that operation. |
| 2. | Resolves a stable title and optional occurrence detail from catalog keys. |
| 3. | Adds the error response to OpenAPI. |

`ApiV2ErrorMapping.forException(type).status(status).documentedError(code, description)` starts a
typed builder. Optional `arguments`, `code`, and `extensions` extractors customize the mapping before
`build()`. The `extensions` extractor supplies a `Map<String, Object>` of problem extension
members from the exception. `ApiV2Problem` serializes the map at the top level with
`@JsonAnyGetter`. Keep typed extension records in the feature package. Document them with a
specialized schema and select it for the response, rather than adding feature components to
`ApiV2Problem`.

When exception classes overlap, the most specific matching class applies. An unmapped exception
continues through the normal controller-advice chain. Startup fails if a mapping refers to an
operation that the resource does not expose.

For a concrete controller, use the standard Swagger annotations. These include `@Operation`,
`@Parameter`, `@RequestBody`, `@ApiResponse`, and `@Schema`. Every `/api/v2/**` handler must declare
`@Operation` or be explicitly `@Hidden` on its method or controller. The merger rejects undocumented
handlers. The generic CRUD controller is hidden because the resource catalog generates its concrete
paths. `ApiV2OperationCoverageTest` checks the production handler map without loading service or
database dependencies.

The public OpenAPI endpoint uses the same problem format for invalid supplied credentials and
exceeded request limits.

## Test the resource

Add focused tests for these areas:

| # | Requirement |
| --- | --- |
| 1. | Resource metadata, fields, sort, access, schema, and relationships. |
| 2. | Conversion from API documents to manager commands. |
| 3. | Manager authorization, validation, transactions, and bulk rules. |
| 4. | DAO filters, access constraints, sort, pagination, and counts. |
| 5. | Spring catalog registration. |
| 6. | HTTP payloads, responses, status codes, fields, filters, and writes. |
| 7. | OpenAPI paths, schemas, examples, access data, and errors. |

See these examples:

| # | Requirement |
| --- | --- |
| 1. | `ApiV2MaintenanceResourceTest` |
| 2. | `MaintenanceResourceOperations` |
| 3. | `ApiV2ResourceErrorMappingTest` |
| 4. | `ApiV2DeclaredArrayOpenApiTest` |

Run focused unit tests:

```bash
mvn test -Dtest=MyResourceTest,MyResourceOperationsTest -Dfast=true
```

A DAO test extends `SpringTransactionalTest`. See [Creating
transactions](Transactions.md#creating-transactions) for service transaction declarations and advice
naming rules.

### Write an HTTP test for a REST API v2 route

Annotate the class with `@ApiV2WebIntegrationTest`, name it `*MVCIT.java`, and take fixtures from
`ApiV2Fixture`:

```java
@ApiV2WebIntegrationTest
class WidgetContractMVCIT {

  @Autowired private WebApplicationContext context;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
  }
}
```

Use this annotation and fixture together for API v2 integration tests. `MVCTestBase` extends the
JUnit Jupiter `RealTransactionSpringTestBase` and supports MVC tests, but lacks the API v2 fixture's
authenticated `MockMvc` and disposable rows. The fixture creates system-administrator and
ordinary-user credentials and installs the production servlet filters.

Follow these harness rules:

| # | Requirement |
| --- | --- |
| 1. | **Work commits.** The annotation omits `TransactionalTestExecutionListener`, so nothing rolls back. The annotation registers `DatabaseCleaner` before each test and cleans committed rows after the outer class and all its nested classes finish. `ApiV2Fixture.cleanUp()` still runs after each test to check delete routes and restore in-memory state. |
| 2. | **Collections are shared.** Another test's rows are in the same table. Never assert a raw `totalDocs` against a whole collection. Tag rows with `fixture.marker()` and scope every query to it, for example `?where=message==<marker>`. |

Build `MockMvc` yourself only when the test needs to omit the fixture's filters. For example, a test
may check behavior without `LocaleFilter`.

## Checklist

| # | Requirement |
| --- | --- |
| 1. | [ ] The resource name and public fields are stable. |
| 2. | [ ] The ID is read-only and is the last default sort field. |
| 3. | [ ] Resource and field access fail closed. |
| 4. | [ ] The exposed operations match the HTTP contract. |
| 5. | [ ] The operations class calls a manager and does not call a DAO. |
| 6. | [ ] The manager controls domain authorization, validation, transactions, and bulk rules. |
| 7. | [ ] All user-facing errors are in message catalogs. |
| 8. | [ ] Each public concrete controller contributes an `ApiV2EndpointSpec`. |
| 9. | [ ] Each relationship verifies target read access. |
| 10. | [ ] Each relationship target is registered. |
| 11. | [ ] OpenAPI shows examples and resource-specific errors. |
| 12. | [ ] Focused resource, operations, manager, DAO, controller, and OpenAPI tests pass. |

Booking filters accept `requester.value==me` through the requester relationship to users. Each
request resolves the alias using the effective subject, including during operate-as. Booking
configurations, the catalogue and calendar events accept the filter-only `targetOwner==me` member.
It includes the Inventory read constraint and accepts only equality with `me`. It is invalid within
an OR group. Shared links and saved views should retain `me`, rather than substituting a user id.

## Generated frontend access and relationship clients

Resource-access and relationship-picker requests use registry entries backed by the generated Orval
client. The shared transport supplies authorization, validates generated request and response
schemas, and handles HTTP problems. Callers do not supply a token. See [the frontend client
guide](RestApiV2FrontendClient.md).

Picker sources keep display-label mapping and load selected IDs in bounded batches. Search keys
contain the source ID and term; option and batch caches contain the source ID and canonical selected
IDs. These display-option caches are the documented exception to generated operation keys and
contain no identity scope. Selected display documents retain their 15-second freshness check.
