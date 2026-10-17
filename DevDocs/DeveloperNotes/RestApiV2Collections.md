# REST API v2 collections

REST API v2 exposes read-only `instruments` and `users` collections and a writable `maintenances` collection.
A resource description declares public fields, relationships, access rules and default sorting.
An `ApiV2ResourceSpec` registers the description and an operations class that delegates to a
transactional domain manager. DAOs apply access constraints and client filters together, before
pagination and counting. Do not filter results in the controller after pagination.

## Compile and combine query predicates

`RsqlCollectionQuery.Subquery` carries nested subqueries in the scope where their correlated
aliases are visible. Apply a compiled predicate with `Predicate.applyTo(query)`, or
`Predicate.applyTo(nestedBuilder, rootQuery)` inside an existing subquery. Both forms register
nested subqueries and bind parameters on the root query.

Combine compiled predicates with `Predicate.and(...)` or `Predicate.or(...)`. They ignore null
predicates and reject conflicting parameter or subquery bindings. Use one
`RsqlCollectionQuery.newCompilation()` session when compiling several predicates for the same
operation; its allocator keeps generated names distinct across nested scopes. Independent
compilations still need distinct prefixes when they will be combined. Keep sessions local to an
operation rather than storing them on a reusable DAO.

## Add a field type

Collection field implementations live in `model.collection.types`. Each implements
`CollectionFieldType` and owns its literal parser, JSON decoder, validation,
serialization, schema and supported operators. `CollectionFieldTypes` keeps the
convenience factories used by resource declarations.

For an annotated Java type, copy the registrations from
`CollectionFieldTypeRegistry.builtIns().registrations()`, add an exact-class
`Registration`, and construct a new immutable registry. Pass it to the
registry-taking `CollectionDescription.fromApiV2Resource` overload. Primitive
classes and boxed classes share a registration. Enum declarations use the enum
factory unless an exact registration overrides it. Programmatic `Field`
declarations can use a type instance directly, including types configured with
per-field limits.

`parse` validates filter literals. `parseDocument` decodes a JSON body value and
receives the write operation. The document parser checks the accepted JSON kind
before decoding and runs field validation afterwards. Keep rules that apply only
to writes in `validate` and `writeConstraints`; do not publish those rules in
filter schemas. Declared object and array types retain child validation before
assembly. A constraint decorator forwards the underlying decoder and comparison
strategy.

Runtime fields use `RuntimeFieldType` implementations in
`model.collection.types.runtime`. These decode persisted strings and parse filter
arguments, which can have different shapes: a choice filter takes one option,
while a stored choice serializes to an array. Add the stable identifier to
`RuntimeFieldValueType`, register its implementation in `RuntimeFieldTypeRegistry`,
and map the source domain's field kind in its runtime manager. The enum delegates
to the registered implementation. Share value helpers with predefined types only
when their rules match. Runtime numeric syntax and precision rules also govern
the DAO's numeric-text SQL conversion.

Each type declares a `FieldComparisonStrategy`. Reuse an existing strategy when
the storage semantics match. For a new strategy, implement
`RsqlFieldPredicateCompiler` in the DAO layer and add its `Registration` to a new
`RsqlFieldPredicateCompilerRegistry`, retaining the built-in registrations. Pass
that registry to `RsqlCollectionQuery` or `CollectionQueryExecutor`; nested
relationship queries use the same registry. Compiler contexts bind values and
count LIKE predicates through the operation's shared state. Model types never
contain SQL or import DAO classes. Runtime correlation, access checks and presence
semantics remain outside the value compiler.

Registry construction rejects duplicate mappings. Query construction validates
known field mappings, and compilation checks dynamically resolved fields. A
non-queryable type uses `FieldComparisonStrategy.NONE` and no filter operators.
Add tests for literal and document parsing, invalid values, schema output,
serialization and compiled parameter bindings when introducing a type. Storage
conversions also need a database comparison test.

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

Only declared operations are exposed. Maintenance reads are public; writes require a system
administrator. Instruments and users remain read-only and return 405 for mutation requests.
Audit and booking routes are not yet exposed.

## Mutations and protected access

The collection controller provides POST on a collection and its `/bulk` path, PATCH on an item or
filtered collection, and DELETE on an item or filtered collection. A collection must explicitly
declare each operation. Mutation authorization runs before dispatch; the manager owns the
transaction, validation and lifecycle hooks. Bulk updates/deletes require an explicit filter and
respect the collection's match limit. Invalid rows roll back the whole operation.

`MaintenanceResourceOperations`, `MaintenanceManagerImpl` and `AbstractCollectionManager` show
the complete path. Maintenance creation accepts `startDate`, `endDate`, optional
`stopUserLoginDate` and `message`; dates use ISO timestamps. Domain validation checks the full
resulting maintenance window after a patch, not just the changed fields.

Protected resources can contribute `/api/v2/{resource}/{id}/access`, `/access/grantees` and
`/access/me` through `ResourceAccessSpec`. The resource must opt in; ordinary collections return
404 for these routes. Assignment replacement requires a strong `If-Match` version and enforces
the resource's role and ownership rules. The access document is private and must not be cached.
The generated OpenAPI document includes protected-access routes only for opted-in resources.

The React `ResourceAccessEditor` keeps its draft within one resource and bearer-token session.
Changing either starts a fresh editor; access documents and grantee search results use the same
session boundary in their query keys. A failed background or conflict refresh keeps the loaded
document, unsaved draft and navigation guard mounted. After a stale replacement, Retry loading
latest remains available and saving stays blocked until recovery succeeds. A successful Leave closes
its confirmation even if the caller retains inherited access. Its optional navigation callback runs
only while the editor session that initiated the request remains mounted.

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
