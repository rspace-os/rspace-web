# REST API v2 queries and access

## Compile and combine query predicates

`RsqlCollectionQuery.Subquery` carries nested subqueries in the scope where their correlated
aliases are visible. Apply a compiled predicate with `Predicate.applyTo(query)`, or
`Predicate.applyTo(nestedBuilder, rootQuery)` inside an existing subquery. Both forms register
nested subqueries and bind parameters on the root query.

Combine compiled predicates with `Predicate.and(...)` or `Predicate.or(...)`. They ignore null
predicates and reject conflicting parameter or subquery bindings. Use one
`RsqlCollectionQuery.newCompilation()` session when compiling several predicates for the same
operation; its allocator keeps generated names distinct across nested scopes. Independent
compilations need distinct prefixes if combined. Keep sessions local to an operation, never on a
reusable DAO.

## Signed global IDs

A global ID in a relationship filter or write is a prefix and an unversioned ID. The ID can be
negative, as seeded and baseline rows are (for example `US-3`). A versioned ID such as `IN12v2` is
rejected.

## Query configuration

The resource definition also allowlists queries. Clients can use:

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

Responses always contain the ID. Field access rules can hide fields, which clients cannot use in
`where` or `sort`.

### Current user in relationship filters

On a relationship whose targets are all `users`, its ID selectors accept `me` as the authenticated
effective subject's numeric user ID:

```text
?where=createdBy.value==me
?where=createdBy.id=="me"
?where=createdBy.value=in=(me,42)
```

`.value` and `.id` mean the same thing. The alias works in eligible mixed `=in=` lists when
the selector's existing operator allowlist permits `=in=`. Quoted and unquoted exact lowercase
`me` are equivalent. Other spellings, including `ME`, are ordinary values and normally fail numeric
ID parsing.

During run-as, `me` means the effective subject, not the administrator acting for that subject.
Collection list, count, filtered bulk update, and filtered bulk delete requests all resolve it
the same way.

### Per-actor fields

A collection can publish caller-specific runtime fields. Each field kind
has a namespace, provider, identity scheme, and catalog route:
`/api/v2/{resource}/fields/{namespace}`. Clients must use the published catalog routes instead of constructing them.

Instruments publish two:

| # | Requirement |
| --- | --- |
| 1. | `customFields` are copied from a template, so every instrument made from that template has the same field. The template field's Global ID identifies the definition and survives renames. |
| 2. | `extraFields` belong to one record. With no definition row, the pair (exact name, declared type) identifies a definition; a rename changes it. The ID encodes both, as `XF` plus a type letter plus the hex of the UTF-8 name, so it round-trips through an RSQL selector, a comma-separated `ids` list and a URL path without escaping. Clients treat it as opaque and display `label`. Discovery, saved-view hydration and projection preserve exact names, and a runtime-field filter resolves its selector by exact name, including case, independently of database collation. The encoding handles punctuation. A selector ends at a space, `,`, `=`, `;`, `(` or `"`, so user-supplied names such as `Max voltage (V), peak` cannot travel literally. RSQL parses UTF-8 values and selectors: a field named `温度計` holding `摂氏 4 °C` filters like any other. |

Both namespaces behave identically after resolution: filter with `where`, select as a column with
`fields[resource]`, and read values back under the namespace's response object. Definitions may
share labels, so a custom field identifies its template as `source`. An extra field has no owner
and leaves it empty.

Extra fields with the same name but different types, such as text and number, are separate
definitions. Queries restrict by concrete field entity, so numeric comparisons never convert or
accidentally match the text field's values.

Runtime-field values are queried from the database, including ad-hoc extra fields.
Instrument custom-field projection selects only the instrument ID, definition ID, declared type
and stored data for the authorized page IDs. It does not hydrate field entities or their instrument
and template associations. A missing value row omits the definition; an existing row with null or
empty data publishes null through the same runtime-field codec used for populated rows.

### Text matching on a runtime field or a relationship target

A text runtime field publishes both `=contains=` and `=like=`. Both operators use the database and
are case-insensitive.

`=contains=` is an exact substring match that always runs in the database. Use it to find a
character sequence at any position: `=contains=BSL` finds `BSL-2`,
`XBSL-2` and `Level BSL-2 lab`, and a substring may span words.

`=like=` splits the filter value on whitespace and requires every resulting word to occur as a
substring, in any order. It has these behaviors:

| # | Requirement |
| --- | --- |
| 1. | A word is a substring, with lower-casing. `=like=BSL` finds `BSL-2`, `XBSL-2`, and `BSL-3`. `=like=BSL-2` does not find `BSL-3`. |
| 2. | Several words all have to be present, in any order. `=like=BSL-2 lab` and `=like=lab BSL-2` both find `Level BSL-2 lab`; `=like=BSL-2 freezer` finds nothing. |
| 3. | A word can be a partial word, so `=like=SL-2` finds values containing that substring. |

`InstrumentCustomFieldTextSearchIT` tests these behaviors. Collection queries always use the
database path, so the result does not depend on Hibernate Search index state.

### Scalar read documents

Instrument list and single reads use `ResourceOperations.usesReadDocuments()` and
`findReadDocuments()` to project scalar rows through the same collection query compiler and
pagination as entity reads. `CollectionQueryExecutor.projectedPage()` avoids hydrating the
Inventory aggregate. The renderer validates supplied declared objects and arrays before applying
selection and per-row field access. Location masking resolves the authorized page's current
parents in bulk. Runtime providers that support relationship ID projection receive authorized
page IDs through a direct `valuesForIds()` call, retaining their transaction advice. Other
providers must explicitly implement `valuesForAuthorizedIds()` and may trust those IDs only after
the collection read policy has selected the page. Scalar read documents do
not bind relationships. Entity reads remain available to internal domain callers and relationship
resolution.

## Access control

Access control has three layers:

| # | Requirement |
| --- | --- |
| 1. | `AccessPolicy` controls resource operations. |
| 2. | Field configuration controls field reads and writes. |
| 3. | The manager controls domain rules that need stored data or permission services. |

The default policy requires authentication. Configure anonymous reads explicitly. REST API v2
accepts an `apiKey` header or an OAuth bearer token. REST API v2 usually ignores browser cookies.
A request with only a session cookie is usually anonymous.

`POST /api/v2/oauth/tokens` is the only credential-minting exception. It uses an existing
authenticated browser session to create a UI OAuth token. It does not create a browser session. It
ignores API keys and bearer tokens. Permissive API CORS does not apply to this route. During run-as,
the token's `sub` is the effective user and its RFC 8693 `act.sub` is the original administrator.
Every REST API v2 UI token carries the UI audience. It also carries an opaque `sid` that binds it to
the current browser authentication context.

Inventory API v1 requests may use a REST API v2 UI token only when the matching authenticated
browser session is present. The v1 OAuth authenticator delegates these tokens to the v2 validator,
which checks the live subject, run-as actor, and session context; it does not create a second
stateless Shiro login. The legacy Inventory token endpoint remains available to public document
views.

The authentication interceptor resolves supplied credentials and passes the resulting or anonymous
subject to an access function. `ApiV2EndpointSpec` supplies the coarse controller policy.
A handler with no contributed spec defaults to authenticated access. Public controllers contribute
`AccessFunction.anyone()`. The generic CRUD controller also contributes `anyone()` at this seam.
Its selected `ApiV2ResourceSpec` operation makes the final decision from the collection policy.
Invalid supplied credentials are rejected even when the endpoint is public.

The `apiKey` credential header has no scheme. Unlike `Authorization`, HTTP tooling will not redact
it. Keep it out of logs and URLs.

When both credential headers are present, the API key wins. A long-lived `apiKey` on a shared HTTP
client silently wins over a fresh bearer token added later, authenticating as the key's owner. Send only one credential.

These policy fields are independent:

| Policy field | Operation |
| --- | --- |
| `readAccess` | List, count, read by ID, and relationship resolution. |
| `createAccess` | Single create and bulk create. |
| `updateAccess` | Single update and bulk update. |
| `deleteAccess` | A hard delete, including an explicitly selected permanent single delete. |
| `softDeleteAccess` | A configured soft-delete or archive command, including its bulk form. |

`softDeleteAccess` does not replace `deleteAccess`. A soft-delete operation must use
`AccessContext.Operation.SOFT_DELETE`. A hard-delete operation must use `DELETE`. A resource may
opt into both on the same canonical item URI only when an explicit request option such as
`permanent=true` selects the hard delete. Never infer a destructive command from the row's current
state; retrying an ordinary soft-delete request must remain safe.

An access function can return a row constraint. The framework combines it with the client filter
for list, count, read by ID, and bulk operations. Unreadable rows return 404.

### Resource-role protected collections

The [resource-role permission model](SecurityAndPermissions.md#resource-role-permissions) owns
role ordering, persisted Owners, live identity resolution, and transactional mutation authority.
Register a `ResourceAccessSpec` in the resource's `ApiV2ResourceSpec` after its service-layer
scheme and `ProtectedResourceAccess` adapter are registered. Resource adapters supply the
capability and Owner-health read fields they support. Use the trusted
`ResourceRoleMembershipConstraint` for collection selection; resolve page capability fields in
batches. A missing or unreadable protected resource returns the same `404`.

A custom `createAccess` function runs after structural parsing and before relationship resolution
and persistence. For one create document, `context.requireInput()` returns the complete
immutable `ParsedDocument`. For bulk create, `context.inputs()` returns all documents in input
order.

```java
AccessFunction documentedCreateAccess =
    AccessFunction.documented(
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
also prevents servlet-session reads and writes. It preserves the existing session for
`POST /api/v2/oauth/tokens` and for bearer JWTs that declare the dedicated REST API v2 UI audience.
The audience declaration is only a routing hint. Authentication still verifies the JWT signature,
token row, subject, actor, and session binding before any controller runs. API keys and
external OAuth tokens remain stateless and cannot inherit run-as.

The authentication interceptor neither logs in to nor logs out of Shiro. Endpoint metadata selects
browser-session authentication only for the token route. A session-bound UI token authorizes as its subject and records both actor and subject for delegated
security events. Other credentials never consult the browser identity.

Browser authentication reloads the subject and originating actor on every request
and rejects either identity if its account is disabled or locked. This applies to
UI token creation and to each use of a session-bound UI token, including run-as.

Starting and ending legacy Shiro run-as rotates the opaque browser authentication context. Tokens
from before either transition therefore fail immediately, even if their JWT expiry has not passed.
The UI mints a fresh v2 token after each page load. It does not trust a token retained in session
storage across a run-as redirect. Authenticated responses default to
`Cache-Control: no-store, private, no-transform`.
Public configuration responses keep their explicit cache policies.

The legacy UI-token endpoint refuses token creation during run-as. REST API v1 validates
session-bound REST API v2 UI tokens against the matching live browser session. AuthStore keeps the
current token in session storage for legacy API clients; the query hooks keep theirs in memory.
Every page initialization mints the token appropriate to its current authentication context.

### Rate limits

REST API v2 uses separate throttle beans from REST API v1 but shares its deployment properties.

REST API v2 uses thread-safe Bucket4j buckets with greedy refill. Before authentication, a source
bucket limits credential failures and anonymous traffic. After successful authentication, each
stable actor-ID bucket applies the 15-second, hourly, and daily limits atomically. A separate global
bucket and minimum request interval apply only to authenticated traffic. Raw, unvalidated
credentials are never bucket keys and cannot consume the authenticated global allowance.

Deployment property meanings are documented under
[API throttling](../public/RSpaceConfiguration.md#api-throttling). REST API v2 supports
`api.throttling.enabled`, `api.user.limit.15s`, `api.user.limit.hour`, `api.user.limit.day`,
`api.global.limit.15s`, and `api.global.minInterval`.

When throttling is enabled, responses contain `X-Rate-Limit-*` usage headers. Rejected requests
return 429 and an API problem body.

Authenticated buckets use a SHA-256 fingerprint of the stable actor ID. Public, anonymous, and
invalid-credential buckets use a fingerprint of the client address. Bucket keys retain neither
user IDs nor client addresses.

The address resolver accepts `X-Forwarded-For`. Configure each trusted proxy to replace an
untrusted `X-Forwarded-For` header.

The caller bucket store keeps at most 10,000 keys. The separate global bucket supplies the hard
limit when caller buckets are removed.

### Cross-origin requests

Set `api.permissiveCors.enabled=true` to permit all origins through cross-origin resource sharing
(CORS) in REST API v2.

An `OPTIONS` response permits `POST`, `PUT`, `PATCH`, `GET`, `OPTIONS`, and `DELETE`. It permits the
`apiKey`, `Authorization`, `Content-Type`, and `If-Match` request headers and exposes the `ETag`
response header so browser clients can make versioned updates.

### Deviations from REST API v1

These differences are intentional:

| Area | REST API v1 | REST API v2 |
| --- | --- | --- |
| Browser state | Authentication can use Shiro request state. | The stateless filter removes cookies and blocks servlet-session access. |
| Authentication lifecycle | The interceptor logs in to Shiro and logs out after the request. | The interceptor validates credentials without changing Shiro state. |
| UI bearer tokens | Accepts legacy UI tokens; session-bound v2 UI tokens require the matching live browser session through the v2 validator. | Requires the matching live session for a session-bound UI token. |
| External OAuth controls | External OAuth does not apply all API availability settings. | External OAuth applies deployment, user, and OAuth availability settings. |
| Disabled throttling | Responses contain fabricated rate-limit statistics. | Responses do not contain rate-limit statistics. |
| Throttle keys | The bucket store retains raw credentials and has no size limit. | The bucket store retains fingerprints and keeps at most 10,000 caller keys. |
| Concurrent requests | A caller can overspend one allowance through concurrent requests. | Bucket4j consumes every configured allowance atomically. |

## Relationships

Declare a relationship as a record component annotated with `@ApiV2ResourceRelationship`, next to
the scalar fields; see
[`@ApiV2ResourceRelationship`](RestApiV2Collections.md#apiv2resourcerelationship). The declaration
specifies these items:

| # | Requirement |
| --- | --- |
| 1. | Public name. Relationships currently expose one typed resource reference. |
| 2. | Accepted target resource types. |
| 3. | Entity property and the related value's ID and kind accessors. |
| 4. | Accepted create and update forms. |
| 5. | Required, nullable, read, and write rules. |

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
effective-subject read access. The operations class receives a `ResolvedResourceReference`, never
an untrusted ID. Rendering repeats target read authorization for every relationship. A missing or unreadable target renders the complete
relationship field as `null` at every depth. It never falls back to a raw target ID, resource name,
or global ID. The entity that contains the relationship remains readable and remains in list totals.

An ordinary list or count does not join the relationship target or apply its access rule. A filter
that observes a relationship, including its stored ID or a target field, compiles to an authorized
correlated `EXISTS` query. Thus an unfiltered count performs no target-access query, while a target
filter cannot be used to enumerate hidden references.

To support relationships without top-level CRUD routes, register an
`ApiV2RelationshipTargetSpec<T, ID>` bean. Its batch lookup receives a set of IDs
and returns a map containing only entities that the effective subject can read. A target-only
resource exposes relationship resolution without top-level collection routes. Its access policy must not return a row
constraint. The batch lookup must enforce row visibility.

A target-only spec can supply a `runtimeFieldSourceResource` naming a routable resource with
the same entity and ID mapping. This delegates runtime-field discovery and resolution without
exposing collection routes or copying the source's scalar fields. The catalog uses the source's existing field catalogue URLs. The catalogue validates the mapping at startup.

Delegated runtime predicates additionally enforce the source resource's read policy in a correlated
`EXISTS`, including negative and missing-value comparisons. Discovery of a definition on one
readable item does not authorize evaluating it on every item with that definition. Safe reference
and name predicates retain the target-only resource's own policy. Hopped projection still requires
the provider's independently authorized `valuesForIds` support.

At `depth=0`, a readable target's `value` is its ID. This reference object is also the update shape.
If the client supplies `globalId`, it must agree with `relationTo` and `value`. A relationship can
also accept a global-ID string as update shorthand. Target visibility is still checked at depth 0.

At greater depths, `value` is the expanded target document in the same relationship envelope.
The target registration applies its access and field rules. The API does not accept an expanded
response object as update input.

A response includes `globalId` when the target has a global-ID prefix. For example, an instrument
reference includes `"globalId": "IN123"`. The response retains `globalId` when `value` is expanded.
Targets without a global ID, such as users, omit this field.

Relationship population is request-scoped and breadth-first. The renderer deduplicates target IDs,
groups them by resource type, and issues one authorized `IN` query per type at each requested depth.
Resolved and unavailable targets are cached for the request. This bounds projection queries by
depth and target types instead of by the number of returned entities.

## Conditional requests

Versioned routes return an `ETag` and require its current value in `If-Match`. Preserve
`Cache-Control: no-transform` so a compression proxy does not rewrite the validator. Shared
`ApiV2ConditionalRequest` parsing accepts the known proxy alterations: one `-gzip`, `-br`, or
`-deflate` suffix inside the quotes and an optional `W/` prefix. `parseStrongEtag` returns the
normalized quoted tag; numeric version parsing additionally requires a non-negative integer.
Missing required preconditions return `428`; malformed tags return `400`. Each versioned route
checks the normalized tag against current state. The stale-state response is route-specific;
use `412` only where the route contract defines it. Shared parsing does not choose that status.

## Errors and locale

REST API v2 returns errors as RFC 9457 problem details. The response content type is
`application/problem+json`.

```json
{
  "title": "The request contains an invalid value.",
  "status": 400,
  "code": "errors.api.v2.invalidRequest",
  "detail": "The request contains an invalid value.",
  "invalidParams": [
    {"name": "docs[1].name", "reason": "required"}
  ]
}
```

`title`, `status`, and `code` are always present. `detail` and `invalidParams` are present when the
error has that information.

The `code` value is stable. The server resolves `title` and `detail` from the message catalog.
Authentication errors also contain `WWW-Authenticate: Bearer`.

Spring uses the same problem format when rejecting a request before handler selection, including
unsupported methods, which do not return HTML error pages.

The server uses the deployment locale. It does not select an unavailable locale from
`Accept-Language`.

Every REST API v2 response contains `Content-Language` and adds `Accept-Language` to
the `Vary` header.

### Validation and pagination limits

Errors use localized RFC 9457 problem responses. Query validation rejects unsupported fields,
operators, values and excessive complexity before querying the database. Spring's JSON reader
also bounds document size, string size, token count and nesting depth.

`ResourceRequest.Page` enforces a size between 1 and 100 for every adapter, including direct Java
callers. Relationship fallback lookups split larger ID sets into pages of at most 100 and
combine the readable results before applying read overrides.
`PageOffsets` shares overflow-safe arithmetic for one-based pages. Collection requests,
runtime-field catalogs and response metadata retain separate validation, limits, and pagination
contracts.
