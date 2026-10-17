# REST API v2 read collections

REST API v2 currently exposes read-only `instruments` and `users` collections.
A resource description declares public fields, relationships, access rules and default sorting.
An `ApiV2ResourceSpec` registers the description and an operations class that delegates to a
transactional domain manager. DAOs apply access constraints and client filters together, before
pagination and counting. Do not filter results in the controller after pagination.

## Routes

| Route | Access |
| --- | --- |
| `GET /api/v2/config` | Public deployment information. |
| `GET /api/v2/openapi.json` | Public generated OpenAPI 3.1 document. |
| `POST /api/v2/oauth/tokens` | Authenticated browser session; issues a session-bound UI token. |
| `GET /api/v2/users/me` | Authenticated current-user information. |
| `GET /api/v2/users/me/profile-image` | Authenticated current-user profile image. |
| `GET /api/v2/{resource}` | Collection policy, row and field authorization. |
| `GET /api/v2/{resource}/count` | The same access and filter rules as the list. |
| `GET /api/v2/{resource}/{id}` | The same row and field authorization as collection reads. |
| `GET /api/v2/{resource}/fields/{namespace}` | Caller-specific runtime field catalog. |

Collection mutations, access-management routes, audit routes and booking routes are not yet exposed.
The generated document is the contract for this prefix; internal mutation support types do not
enable HTTP write operations.

## Register a read-only collection

Use `AccessPolicy.readOnly(...)` and limit `exposedOperations` to `LIST`, `COUNT` and `READ`.
Implement `find`, `count` and `findById` in `ResourceOperations<T, ID>`. Follow
`InstrumentResourceOperations` or `UserResourceOperations`. A resource registration also registers
its relationship target; do not register the same name twice.

Use `AccessResult.allowedWhere(...)` for access rules expressible as row constraints. Internal
selectors let the database enforce ownership and sharing without allowing clients to query those
properties. `InventoryReadFilters` and `InstrumentReadAccess` demonstrate this pattern.

Public relationships must remain resolvable after the read transaction commits. Supply batched
read overrides or safe scalar projections rather than relying on lazy entity traversal.

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

OpenAPI read schemas declare optional object maps for projected runtime namespaces, including
eligible relationship prefixes such as `target.customFields`. The document root stays closed;
the maps accept caller-specific field IDs without publishing those IDs in the cached schema.
Runtime projection properties are excluded from create and update schemas.

## Declared array fields

Declare each public object array with `CollectionFieldTypes.array(...)` and its children with
`Field.embedded(...)`. Pass that declared type to the resource field. For example:

```java
record Label(String text) {}

var labels = CollectionFieldTypes.array(
    Label.class,
    List.of(Field.embedded("text", CollectionFieldTypes.text(80), Label::text).required()),
    values -> new Label((String) values.get("text")));
```

Use `CollectionFieldTypes.object(...)` for a nested object and another declared array for nested
rows. OpenAPI derives their recursive `properties` and `items` from these child fields, with
`additionalProperties: false` on each object. It preserves child constraints, documentation,
nullability and operation-specific access rules. `x-rspace-fields` contains the same recursive
field metadata. The declared child list controls the public object shape.

The array factory's row bounds become `minItems` and `maxItems`. Internal create and update body
schemas describe a supplied array as a complete replacement. Required children and child defaults
apply to every supplied row on either operation, while the outer field remains optional in an
update body. These schemas do not enable HTTP write routes for read-only collections.

## Authentication and request limits

`api.permissiveCors.enabled=true` enables v2 CORS for API credentials. The browser-session token
endpoint is excluded from permissive CORS and retains origin/referer checks. Advertised CORS
methods are not a promise that a collection implements writes: consult OpenAPI for its operations.

Errors use localized RFC 9457 problem responses. Query validation rejects unsupported fields,
operators, values and excessive complexity before querying the database. Spring's JSON reader
also bounds document size, string size, token count and nesting depth.
The public OpenAPI endpoint returns the same problem responses for invalid supplied credentials
and exceeded request limits.

`ResourceRequest.Page` enforces a size between 1 and 100 for every adapter, including direct Java
callers. Relationship fallback lookups split larger ID sets into pages of at most 100 and
combine the readable results before applying read overrides.

`PageOffsets` supplies shared overflow-safe arithmetic for one-based pages. Collection
requests, runtime-field catalogs and response metadata keep their own validation and limits;
sharing offset math does not give them a common pagination contract.

Inventory API v1 requests may use a REST API v2 UI token only when the matching authenticated
browser session is present. The v1 OAuth authenticator delegates these tokens to the v2 validator,
which checks the live subject, run-as actor, and session context; it does not create a second
stateless Shiro login. The legacy Inventory token endpoint remains available to public document
views.
