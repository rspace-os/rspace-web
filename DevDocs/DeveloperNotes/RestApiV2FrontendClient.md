# REST API v2 frontend client

REST API v2 frontend calls use Orval-generated React Query clients and Valibot schemas from the
backend's OpenAPI 3.1 document. Orval 8.48.0 generates both the HTTP client and reusable Valibot
schemas, including readonly arrays and typed recursive references. A small adapter removes URL
validation and supplies the component exports and operation registry used by the frontend.

See [REST API v2 queries and access](RestApiV2QueriesAndAccess.md) for query syntax, relationships,
authorization, conditional requests and server problem responses. API v1, Inventory and ELN clients
use their existing clients.

## Regenerate the client

Run from the repository root after changing backend endpoint or schema declarations:

```sh
mvn test -Dfast=true -Dtest=ApiV2OpenApiDocumentExportTest
pnpm generate:api-v2
```

`ApiV2OpenApiDocumentExportTest` exports the complete document for registered resources and
controllers without a database. It writes only
`src/main/webapp/ui/src/modules/common/api/v2/openapi.generated.json` and checks drift from the backend.

`pnpm generate:api-v2` runs the HTTP output first, the native Valibot output second, the adapter next, and the resource registry last. Both Orval projects read the committed spec. Keep this order: the HTTP output's `clean: true`
can delete validation files generated before it. The Valibot project uses `client: "valibot"`,
`mode: "single"` and `override.valibot.generateReusableSchemas: true`, with an isolated target and
no `output.schemas` setting.

Commit the spec and generated changes with the source change. Do not edit generated files. CI checks
backend-to-spec drift and regenerates the client, native schemas and adapter output to check for a
diff. `orval --outdated` alone does not check the adapter.

Paths below are relative to `src/main/webapp/ui/`:

| # | Path | Contents |
| --- | --- | --- |
| 1. | `orval.config.ts` | Sequential HTTP and Valibot projects. HTTP generation retains React Query 5, fetch, tag-split output, suspense hooks, cancellation, invalidation and transport overrides. |
| 2. | `src/modules/common/api/v2/generated/` | HTTP model types, request functions, query options, ordinary and suspense hooks, query keys and `invalidate*` helpers. |
| 3. | `src/modules/common/api/v2/generated/valibot/native.ts` | Orval's reusable component schemas and operation schemas for bodies, responses and applicable path, query and header parameters. |
| 4. | `scripts/api-v2-valibot/` | Adapter and schema-subset preflight. `generate.mjs` registers native output rather than translating JSON Schema; its only rewrite removes `v.url()`. |
| 5. | `src/modules/common/api/v2/generated/valibot/schemas.ts` | Component exports named `<ComponentName>Schema` and two-way type assertions against HTTP models. Existing consumer imports stay here. |
| 6. | `src/modules/common/api/v2/generated/valibot/operations.ts` | `apiV2Operations`, keyed by operation ID, with optional JSON request schemas, JSON success schemas by status and content type, and public/session flags. Downloads and empty operations retain entries without JSON body schemas. |

Backend `operationId` values determine generated names. Collection names are plural, such as
`listBookings`, `countBookings` and `createManyBookings`; single-record names are singular, such as
`getBooking` and `updateBooking`. Fix names in the backend. There is no Orval operation-name
override or input transformer. 

## Transport and errors

The runtime lives in `src/modules/common/api/v2/`, with exports through `index.ts`. The default
mutator is `apiV2Fetch<T>(url, init?): Promise<T>`. It returns the parsed body; a 204 returns
`undefined` and generates a `void` return type. Generated requests supply an internal `operationId`
for validation lookup. An unregistered
operation fails.

The mutator sends `X-Requested-With: XMLHttpRequest`. It gets Bearer authorization from the token
provider. Token creation uses the generated `createUiOAuthToken` operation without asking the
provider. This prevents recursive token creation. Header parameters, including `If-Match`, remain
part of the generated operation signature. Array query parameters repeat their keys. This preserves
values that contain commas. Generated queries pass their cancellation signal to fetch.

`apiV2HeadersFetch<T>` returns `{ body: T, headers: Headers }` for calendar-subscription get and
create operations, including user subscriptions, which need `ETag`. `apiV2BlobFetch` returns
`{ body: Blob, headers: Headers }` for calendar-file downloads that need `Content-Disposition`.
Both live in `apiV2HeadersFetch.ts` and share the default transport authentication, error handling
and applicable validation.

`apiV2Fetch.ts` exports `BodyType<B> = B` and `ErrorType<E> = ApiV2ProblemError | TypeError` for
Orval. Generated hooks do not expose separate error types for each status. Every HTTP failure throws
`ApiV2ProblemError`, which exposes `status`, optional `code`, `message` and optional `problem`.
Network failures remain the `TypeError` thrown by fetch.

`message` uses the occurrence-specific `detail` when supplied, otherwise the stable `title`, then
the HTTP status text. Generic problems and local request-validation problems omit duplicated detail.
Use `code` for programmatic decisions.

The `problem` member has the generated `ApiV2Problem` type. It is present only when the body passes
`ApiV2ProblemSchema`. Non-JSON or malformed problem bodies leave it absent. Consumers must handle
that case before reading details.

## Tokens and identity changes

`tokenProvider.ts` exports `ApiV2TokenProvider` with `getToken(signal?: AbortSignal):
Promise<string>` and `recover(rejected: string): Promise<string>`, plus `getApiV2TokenProvider()`
and `setApiV2TokenProvider(provider)`. The setter returns a restore function.

The default provider shares token creation across concurrent callers. It refreshes before expiry and
uses the legacy token route on public document views. It works independently of a `QueryClient`. V2
callers neither pass tokens nor call `useOauthTokenQuery`. 401 recovery distinguishes an invalid
token from a resource permission failure. Only GET requests replay after recovery; writes never
replay automatically.

Every minted token, including scheduled refreshes and 401 recovery, is compared with the current
token by refresh-token hash. A mismatch reloads the page before any caller receives the new token,
including concurrent callers waiting for the same mint. An Operate As change therefore discards the
old page's cache and UI state. A token without the hash claim does not count as a mismatch. This
prevents reload loops. Same-identity refreshes do not reload.

## Query keys and invalidation

Use Orval's keys with the shape `[path, params?]`. Use a full path such as `/api/v2/bookings/42`.
Suspense and ordinary hooks share keys. Do not add resource-root key factories or identity scopes.
Identity changes reload the page through the provider. Current-user identity still supplies Table
List `dataScope` metadata and runtime-field selection resets; those values are not query-key
members.

`orval.config.ts` builds `mutationInvalidates` rules through `scripts/api-v2-invalidation/`. Every
non-GET operation invalidates its resource's list and count queries across all parameter sets by
prefix. A mutation path containing `{id}` also invalidates that record's get, access and audit
queries by ID.

Cross-resource effects are explicit entries in
`src/modules/common/api/v2/crossResourceInvalidation.ts`, using `onMutations` and `invalidates`
targets with `query` and optional `params`. Booking writes affecting catalogue availability or
calendar events use these extra rules. The builder supplies each cross-tag target's generated `file`
path. Config tests check mutation coverage, target operation names and generated target files.

Bulk `createMany*`, `updateMany*` and `deleteMany*` rules refresh list and count queries. Their
domain hooks also use generated `invalidate*` helpers for each affected record identified by the
response's `docs`. Do not assume a bulk rule refreshes cached records.

Generated success invalidation does not await refetches. A mutation may resolve before fresh data
arrives. A save-and-close flow that needs fresh data first must await the generated helper. Domain
`onError` handlers also use generated helpers when an uncertain outcome, conflict or unavailable
target needs reconciliation. Imperative events such as current-user rename, ORCID or email changes
use those helpers too. There is no custom `useApiV2Mutation`, invalidating `MutationCache` or
`meta.invalidates` mechanism.

## Domain hooks and generic components

Each module keeps thin domain query options and hooks as its public API. Use `select` for domain
mapping and plain functions for transforms or domain checks. Invalidation belongs in the Orval
configuration. Components may call generated hooks directly for plain calls without domain logic and
for imperative actions such as downloads.

`apiV2Resources` in `src/modules/common/api/v2/apiV2Resources.ts` maps resource names to entries
with `name` and supported generated operations and query-key getters. Optional keys are `list`,
`count`, `get`, `create`, `update`, `delete`, `createMany`, `updateMany`, `deleteMany`, `access`,
`replaceAccess`, `leaveAccess`, `grantees`, `audit` and `runtimeFields`. Unsupported capabilities
are absent.

The registry is generated from each operation's tag and `x-rspace-operation` by
`scripts/api-v2-valibot/resources.mjs`, run by `pnpm generate:api-v2` after Orval and the
Valibot adapter. The handwritten module keeps the registry types and re-exports generated
`apiV2Resources`; tags with only `CUSTOM` operations have no entry. Read operations expose
`request` and `queryKey`; writes expose their request function. Runtime-field endpoints use
`RUNTIME_FIELDS`, with no route-name inference. Unknown kinds, duplicate kinds within a tag,
and missing Orval exports fail generation. To add a kind, register its backend metadata,
add its slot and read/write classification to `resources.mjs`, extend `ApiV2OperationSet`
if needed, and update the focused mapping test. Regenerate the client and golden slots.
The generated entries retain literal tag names through `satisfies ApiV2ResourceEntry`.

Generic modules take a registry entry instead of constructing URLs from a resource-name string.
Tests and stories can inject fake entries for resources such as `widgets`, `records` and
`test-collections`. Continue fetching filter and field metadata from `/api/v2/openapi.json` at
runtime through the shared fetch function. The document publishes repeated parameters, responses
and extension values once under `components`, so readers pass it through `dereferenceApiV2OpenApi`
before looking up a parameter or extension. `where` remains a string built with the existing
`serializeRsql` and `filterFields` utilities.

## Validation rules

The mutator validates JSON request bodies before sending them and JSON success bodies against the
registered operation, status and content type. Blob and empty bodies have no JSON response entry.
Invalid requests throw `ApiV2ProblemError` with status 400, code `errors.api.v2.invalidRequest` and
`problem.invalidParams` derived from Valibot issues, matching the server's validation-error shape.

Validation checks values. The transport returns the original parsed JSON unchanged. Open
dictionaries, runtime metadata and problem extensions keep their values. Read schemas require only
`id`, so projected responses using `fields`, `exclude` or `depth` pass. Table List keeps its
stronger selected-field check with `v.required(v.pick(schema, selected))` on the generated schema.
Generated fieldset types exclude `null`; adapters omit empty selections. The mutator does not
sanitize fieldsets.

The schema-subset preflight remains a generation gate even when native Orval accepts more
constructs. It checks keywords, references and formats in all component and inline schemas,
including error schemas. Unsupported validation keywords, references or formats fail generation
instead of silently weakening validation. Extend the gate and its tests when the API needs another
construct.

| # | Supported schema rules | Validation behavior |
| --- | --- | --- |
| 1. | Primitive types, arrays, nullable type unions, `const`, `enum` | Native Valibot type, literal and enum checks; integers also use `integer()`. |
| 2. | `$ref` and recursive references | Reusable component schemas and lazy references. Orval types each reference that closes a cycle with its generated input and output types. |
| 3. | `allOf`, `anyOf`, `oneOf` | Native intersections or unions. The supported document has no discriminators. |
| 4. | Object properties, `required`, `additionalProperties` | Native object, loose-object, object-with-rest or record schemas. Closed/default object parsing can strip unknown keys; the mutator returns the original JSON. |
| 5. | `minLength`, `maxLength`, `pattern` | String constraints. Native string length checks count Unicode code points. |
| 6. | `minimum`, `maximum`, `exclusiveMinimum` | Numeric bounds, including a strict lower bound. |
| 7. | `minItems`, `maxItems` | Array length checks. |
| 8. | Formats `date-time`, `date`, `email`, `idn-email` | `isoTimestamp()`, `isoDate()`, and the existing strict `email()` mapping for `email`. Server email fields use `idn-email` (RFC 6531) and deliberately remain unvalidated `string()` schemas. Timestamps accept Z and offsets. |
| 9. | Formats `uri`, `uri-reference`, `binary` | URI values remain plain strings. The adapter removes native `v.url` checks only from generated Valibot output. Binary media stay outside the JSON registry. |

Orval emits `v.readonly()` for `readOnly` arrays. Each component's two-way type
assertions must still pass against its generated HTTP model, including recursive fields. Do not
weaken an assertion to `unknown`. Orval's HTTP models keep an array `$ref` with a `readOnly` sibling
mutable while its Valibot output makes it readonly, so that shape fails the assertions. Projection consumers keep using object schemas with `v.pick` and
`v.required`.

Native intersections can drop open members from their parsed output. Callers needing a parsed
open-`allOf` result must explicitly preserve those members or reject that shape. The shared
transport preserves extensions by returning the original JSON.

Annotations such as `description`, `title`, `example`, `examples`, `default`, `deprecated`,
`readOnly`, `writeOnly`, `nullable: false` and `x-rspace-*` do not add validation constraints.
Readonly array typing still applies. Integer formats `int32` and `int64` do not add size bounds or
BigInt conversion.

The adapter also rejects duplicate operation IDs and different JSON request or success response
schemas across media types or statuses within one operation. Native Orval selects one response
schema. Mapping different schemas to the same response export would therefore lose validation.
Supporting such an operation requires separate schema-only operations per status/media pair and an
explicit registry mapping.

Validation stays inside `apiV2Fetch`. Native path, query and header schemas do not expand its
body-validation contract; using them would also require parameter extraction and deserialization.
`runtimeValidation.standardSchema` does not replace the mutator-owned Valibot validation.

## Tests and stories

The shared jsdom setup registers a test token provider and a default MSW handler for `POST
/api/v2/oauth/tokens`. Most tests need no individual token stub. For a standalone client or a
provider-specific test, install a provider and restore it after the test:

```ts
const restore = setApiV2TokenProvider({
  getToken: async () => "test-token",
  recover: async () => "test-token",
});
// Exercise the generated client or domain hook.
restore();
```

Restore the provider in teardown or `finally` so it is restored even if an assertion fails. Register
endpoint handlers with `server.use(...)`, importing `server` from `src/__tests__/mswServer.ts`.
Return success bodies typed with generated models. Keep malformed bodies and problem fixtures
available for failure tests. There are no generated Faker mocks. Shared component tests and stories
use the registry injection seam for fake resources.

Collection list envelopes expose `docs`, `page`, `limit` and `hasNextPage`, plus optional
`totalDocs` and `totalIsLowerBound`. Request `page + 1` when `hasNextPage` is true; derive
a previous page from `page > 1` and page counts from `ceil(totalDocs / limit)` only for exact totals.
The separate `/count` endpoint and bounded `max` semantics remain unchanged.

## OpenAPI tags

For OpenAPI 3.2, replace `x-tagGroups` with `Tag.parent` and a `kind: "nav"` parent tag per group once swagger-core supports 3.2 (swagger-api/swagger-core#5326).
