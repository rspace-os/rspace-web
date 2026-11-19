# REST API v2 frontend client

REST API v2 frontend calls use Orval-generated React Query clients and Valibot schemas from the
backend's OpenAPI 3.1 document. Orval 8.48.0 generates both the HTTP client and reusable Valibot
schemas, including readonly arrays and typed recursive references. A small adapter supplies the
component exports and operation registry used by the frontend.

See [REST API v2 queries and access](RestApiV2QueriesAndAccess.md) for query syntax, relationships,
authorization, conditional requests and server problem responses. API v1, Inventory and ELN clients
use their existing clients.

## Regenerate the client

Run from the repository root after changing backend endpoint or schema declarations:

```sh
mvn test -Dfast=true -Dtest=ApiV2OpenApiDocumentExportTest -DupdateApiV2OpenApiFixture=true
pnpm generate:api-v2
pnpm test --project scripts
pnpm run i18n:types # only when a server catalogue changed
```

`ApiV2OpenApiDocumentExportTest` exports the complete document for registered resources and
controllers without a database. With `-DupdateApiV2OpenApiFixture=true` it writes only
`src/main/webapp/ui/src/modules/common/api/v2/openapi.generated.json`; without the flag it fails if
that file differs from the backend.

`pnpm generate:api-v2` runs the HTTP output first, the native Valibot output second, the adapter next, the resource registry and guaranteed read fields after it, and the `where` filter types last. Both Orval projects read the committed spec. Keep this order: the HTTP output's `clean: true`
can delete validation files generated before it. The Valibot project uses `client: "valibot"`,
`mode: "single"` and `override.valibot.generateReusableSchemas: true`, with an isolated target and
no `output.schemas` setting.

Generated files are formatted and linted like handwritten code: both Orval projects set
`formatter: "biome"`, and the adapter scripts run Biome on their own output. The generator scripts
are TypeScript that Node runs directly. `src/modules/common/api/v2/` has no barrel; import each
export from the module that defines it, such as `@/modules/common/api/v2/ApiV2ProblemError`.

Commit the spec and generated changes with the source change. Do not edit generated files. The backend
is the source of truth: the `api-v2-contract` CI job runs both commands above from the backend and
fails if the committed spec or any generated file differs. It runs in CI rather than as a pre-commit
hook because the export needs a Maven build. `orval --outdated` alone does not check the adapter.

Paths below are relative to `src/main/webapp/ui/`:

| # | Path | Contents |
| --- | --- | --- |
| 1. | `orval.config.ts` | Sequential HTTP and Valibot projects. HTTP generation retains React Query 5, fetch, tag-split output, suspense hooks, cancellation, invalidation and transport overrides. |
| 2. | `src/modules/common/api/v2/generated/` | HTTP model types, request functions, query options, ordinary and suspense hooks, query keys and `invalidate*` helpers. |
| 3. | `src/modules/common/api/v2/generated/valibot/native.ts` | Orval's reusable component schemas and operation schemas for bodies, responses and applicable path, query and header parameters. |
| 4. | `scripts/api-v2-valibot/` | Adapter and schema-subset preflight. `generate.ts` registers native output rather than translating JSON Schema. `filters.ts` types filtered `where` parameters (see Filtering with `where`). |
| 5. | `src/modules/common/api/v2/generated/valibot/schemas.ts` | Component exports named `<ComponentName>Schema` and two-way type assertions against HTTP models. Existing consumer imports stay here. |
| 6. | `src/modules/common/api/v2/generated/valibot/operations.ts` | `apiV2Operations`, keyed by operation ID, with optional JSON request schemas, JSON success schemas by status and content type, and public/session flags. Downloads and empty operations retain entries without JSON body schemas. |

Backend `operationId` values determine generated names. Collection names are plural, such as
`listInstruments` and `countInstruments`; single-record names are singular, such as
`getInstrument`. Fix names in the backend. There is no Orval operation-name
override or input transformer. 

## Transport and errors

The runtime lives in `src/modules/common/api/v2/`. The default mutator is `apiV2Fetch<T>(url, init?): Promise<T>`. It returns the parsed body; a 204 returns
`undefined` and generates a `void` return type. Generated requests supply an internal `operationId`
for validation lookup. An unregistered
operation fails.

The mutator sends `X-Requested-With: XMLHttpRequest`. It gets Bearer authorization from the token
provider. Token creation uses the generated `createUiOAuthToken` operation without asking the
provider. This prevents recursive token creation. Header parameters, including `If-Match`, remain
part of the generated operation signature. Array query parameters repeat their keys. This preserves
values that contain commas. Generated queries pass their cancellation signal to fetch.

`apiV2HeadersFetch<T>` returns `{ body: T, headers: Headers }` for operations that need response
headers. `apiV2BlobFetch` returns `{ body: Blob, headers: Headers }` for file downloads. Both
live in `apiV2Fetch.ts` and share the default transport authentication and error handling.

`orval.config.ts` derives each operation's mutator from the spec (`scripts/api-v2-valibot/mutators.ts`):

- An operation whose 2xx responses are all binary, after resolving `$ref` responses, uses `apiV2BlobFetch`. A
  bodyless 204 is not binary: an operation whose only success is 204 keeps `apiV2Fetch`, which resolves `undefined`.
- Generation fails on mixed JSON and binary successes, on a binary success combined with a bodyless 204 (the Blob
  mutator would hide the 204 as an empty `Blob`), and on a bodyless 2xx other than 204.
- `apiV2HeadersFetch` stays an explicit allowlist in `orval.config.ts`. Every operation that declares an `ETag`
  header must be on that list or on its ETag exclusion list, or generation fails. Exclude an operation only when
  callers read its version some other way, such as a `version` body field.

`apiV2Fetch.ts` exports `BodyType<B> = B` and `ErrorType<E>` for Orval. Generated hooks do not expose
separate error types for each status. Transport and token failures extend `ApiV2Error`, which carries an optional
HTTP `status` and the standard `cause`:

| Error | When |
| --- | --- |
| `ApiV2ProblemError` | An HTTP failure. Exposes `status`, optional `code`, `message` and optional `problem`. |
| `ApiV2UnexpectedResponseError` | A response the client cannot use, with `reason`: `undeclared` (the registry does not declare its status and media type), `invalidBody` (the body fails its schema; the Valibot error is the `cause`) or `malformedJson` (the `SyntaxError` is the `cause`). |
| `ApiV2NetworkError` | `fetch()` or reading a body failed with a `TypeError` while the request was not cancelled. |
| `ApiV2SessionEndedError` | Token issuance refused the session (see Tokens and identity changes). Never retry it, whatever its `status`: the HTML login page carries 200. |

A blocked identity change throws `ApiV2IdentityChangedError`, which is not an `ApiV2Error` because the page is
reloading. Cancellation is never wrapped: an abort rejects with the signal's reason, usually a `DOMException`, but a
custom `signal.reason` can be any value. `ErrorType` is `ApiV2Error | ApiV2IdentityChangedError | DOMException`. An
unregistered operation throws a `TypeError`, which is a programming error and not part of `ErrorType`.

`message` uses the occurrence-specific `detail` when supplied, otherwise the stable `title`, then
the HTTP status text. Generic problems and local request-validation problems omit duplicated detail.
Use `code` for programmatic decisions.

The `problem` member has the generated `ApiV2Problem` type. It is present only when the body passes
`ApiV2ProblemSchema`. Non-JSON or malformed problem bodies leave it absent. Consumers must handle
that case before reading details.

## Tokens and identity changes

`tokenProvider.ts` exports `ApiV2TokenProvider` with `getToken(signal?: AbortSignal):
Promise<string>` and `recover(rejected: string): Promise<string>`, plus
`getApiV2TokenProvider()` and `setApiV2TokenProvider(provider)`. There is no default provider: each entry point
that makes token-bearing calls installs one before rendering, and `getApiV2TokenProvider()` throws until it has.
Session operations never send a token. Public operations send one when the page has a provider and it can supply a
token, so a signed-in caller keeps their view; otherwise, including after a refused mint or recovery, they read
anonymously. The setter returns a restore function, which tests use to swap
providers; `setup.ts`, `browserSetup.ts` and the Storybook preview install one for tests and stories.

In the app shell, `router.tsx` builds the provider with `appShellSessionHandlers` and puts it in `AppRouterContext` as
`tokenProvider`; `AppEntrypoint` installs that same instance, so routes and generated calls share one provider.
The Inventory and Gallery entry points, whose feature-flag devtools call REST API v2, install
`new SessionTokenProvider(defaultApiV2SessionHandlers)`.

`SessionTokenProvider` shares token creation across concurrent callers. It refreshes on demand only:
`getToken` re-mints a token inside the five-minute expiry buffer, so an idle tab does no background work. It works
independently of a `QueryClient`. V2 callers neither pass tokens nor call `useOauthTokenQuery`. API v2 answers 401
only when it rejects the credentials and 403 when it refuses a permission, so 401 recovery mints a replacement
without probing the rejected token. Only GET requests replay after recovery; writes never replay automatically.

The provider decides when a session has ended; session handlers decide what the page does about it.
`new SessionTokenProvider({ onSessionEnded, onIdentityChanged })` requires them. `defaultApiV2SessionHandlers`
navigates to `/login` (session ended) or reloads (identity changed). The provider keeps its token in memory and never
reads or writes the API v1 token in session storage.

| Path | Condition | Handler | Caller receives |
| --- | --- | --- | --- |
| Mint | 401 or 403 from token issuance, or the HTML login page | `onSessionEnded` | `ApiV2SessionEndedError` |
| Mint | network failure, 5xx, malformed JSON, invalid or empty token body | none | the `ApiV2Error`; the next call retries |
| Recovery | the replacement mint is refused | `onSessionEnded` | `ApiV2SessionEndedError` |
| Recovery | other failure | none | the original 401 |
| Any mint | token for another identity | `onIdentityChanged` | `ApiV2IdentityChangedError` |

A tab that makes no v2 call notices an identity change only on its next mint or rejected-token recovery. Starting
or ending Operate As rotates the browser authentication context, so the server rejects the old token on the next call.

The provider serves REST API v2 only. API v1 callers (`useOauthToken`, `useOauthTokenQuery`,
`fetchWithUiToken`, the `common/axios` interceptors and `AuthStore`) keep the legacy
`/userform/ajax/inventoryOauthToken` route, session storage and their own refresh and recovery.

Every minted token, including expiry-driven refreshes and 401 recovery, is compared with the current
token by refresh-token hash. A mismatch reloads the page before any caller receives the new token,
including concurrent callers waiting for the same mint. An Operate As change therefore discards the
old page's cache and UI state. A token without the hash claim does not count as a mismatch. This
prevents reload loops. Same-identity refreshes do not reload.

## Filtering with `where`

Collection operations whose `where` parameter carries `x-rspace-filter` take
`ApiV2Where<"<Filter>">`: a typed object, or an `RsqlWhere` already built from one. Raw strings do
not type-check. Selectors, the operators each allows and value types come from the spec, so a wrong
selector, operator or value fails `pnpm tsc`.

```ts
useListUsers({
  where: {
    or: [{ email: { contains: "smith" } }, { lastName: { matches: "Sm*" } }],
    createdAt: { greaterThanOrEqual: new Date("2026-01-01") },
  },
});
```

A bare value means `equals`. Operator names are `equals`, `notEquals`, `greaterThan`,
`greaterThanOrEqual`, `lessThan`, `lessThanOrEqual`, `in`, `notIn` (non-empty lists), `contains`,
`matches` (wildcards) and `exists` (boolean). Keys combine with AND; `and` and `or` take lists of
nested objects. `apiV2SerializeQuery`, Orval's `paramsSerializer` for these operations, converts the
object with `apiV2WhereToRsql` and otherwise serializes exactly like Orval's default; a parity test
checks that against Orval's own output. An empty object sends no filter. The four search operations
without filter metadata keep a plain string `where`. Query keys hold the params as given, so an
object and the equivalent `RsqlWhere` are cached separately; prefix invalidation still covers both.

## Query keys and invalidation

Use Orval's keys with the shape `[path, params?]`. Use a full path such as `/api/v2/instruments/42`.
Suspense and ordinary hooks share keys. Do not add resource-root key factories or identity scopes.
Identity changes reload the page through the provider.

`orval.config.ts` builds `mutationInvalidates` rules through `scripts/api-v2-invalidation/`. Every
non-GET operation invalidates its resource's list and count queries across all parameter sets by
prefix. A mutation path containing `{id}` also invalidates that record's get, access and audit
queries by ID.

Cross-resource effects are explicit entries in
`src/modules/common/api/v2/crossResourceInvalidation.ts`, using `onMutations` and `invalidates`
targets with `query` and optional `params`. The builder supplies each cross-tag target's generated `file`
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

Reads prefer Suspense: use the generated `use…Suspense` hooks, or `useSuspenseQuery` with
`get…SuspenseQueryOptions`, under a Suspense fallback and an error boundary. Keep a plain `useQuery`
only with a one-line comment giving the reason, such as polling, an optional read that waits for its
input, or an input that stays interactive while its results load.

The registry is generated from each operation's tag and `x-rspace-operation` by
`scripts/api-v2-valibot/resources.ts`, run by `pnpm generate:api-v2` after Orval and the
Valibot adapter. The handwritten module keeps the registry types and re-exports generated
`apiV2Resources`; tags with only `CUSTOM` operations have no entry. Read operations expose
`request` and `queryKey`; writes expose their request function. Runtime-field endpoints use
`RUNTIME_FIELDS`, with no route-name inference. Unknown kinds, duplicate kinds within a tag,
and missing Orval exports fail generation. To add a kind, register its backend metadata,
add its slot and read/write classification to `resources.ts`, extend `ApiV2OperationSet`
if needed, and update the focused mapping test. Regenerate the client and golden slots.
The generated entries retain literal tag names through `satisfies ApiV2ResourceEntry`.

## Validation rules

The mutator validates JSON request bodies before sending them and JSON success bodies against the
registered operation, status and content type. Blob and empty bodies have no JSON response entry.
Invalid requests throw `ApiV2ProblemError` with status 400, code `errors.api.v2.invalidRequest` and
`problem.invalidParams` derived from Valibot issues, matching the server's validation-error shape.

Validation checks values. The transport returns the original parsed JSON unchanged. Open
dictionaries, runtime metadata and problem extensions keep their values. Read schemas require only
`id`, so projected responses using `fields`, `exclude` or `depth` pass.
Generated fieldset types exclude `null`; adapters omit empty selections. The mutator does not
sanitize fieldsets.

Other response types mark every property the server always sends as required, so a missing key fails as
`invalidBody`; a nullable key can still be `null`. List envelopes always contain `docs`, `limit`, `page` and
`hasNextPage`; `totalDocs` is present only when totals were requested.

### Sparse reads with `apiV2Selected.ts`

`*Read` properties marked `x-rspace-read-guaranteed` are always present when requested from a readable document (see
[field read guarantees](RestApiV2QueriesAndAccess.md#field-read-guarantees)). `pnpm generate:api-v2` writes them to
`generated/guaranteedReadFields.ts`, keyed like the resource registry, as a type and a runtime set.
`apiV2Selected.ts` uses them:

- `ApiV2Selected<R, F>`: the resource's `Read` type with each requested guaranteed field present (`undefined`
  removed, `null` kept). Other fields stay optional. Only a literal tuple narrows; `("id" | "name")[]` or `string[]`
  leaves the plain `Read` type.
- `apiV2Fields(fields)`: the `fields[...]` query value for an `as const` tuple, so the request and the type agree.
- `assertApiV2Selected(resource, document, fields)`: narrows the document, or throws `ApiV2UnexpectedResponseError`
  with `invalidBody` when a requested guaranteed field is missing.

Run the assertion at the fetch boundary (the request wrapper or `queryFn`), never in a query's `select`, so its
error goes through the same mapping and retry policy as other response failures. It covers root documents only:
fields of expanded relationships keep their own checks.

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
| 9. | Formats `uri`, `uri-reference`, `binary` | `uri` values are checked with Valibot's `v.url()`, which uses the WHATWG URL parser; `uri-reference` values stay plain strings. Binary media stay outside the JSON registry. |

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

`pnpm test` runs two Vitest projects: `unit`, the jsdom suite, and `scripts`, the generator script
tests and invalidation checks in the node environment. `pnpm test --project scripts` runs only the
latter (see `scripts/api-v2-valibot/README.md`).

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
available for failure tests. There are no generated Faker mocks.

Collection list envelopes expose `docs`, `page`, `limit` and `hasNextPage`, plus optional
`totalDocs` and `totalIsLowerBound`. Request `page + 1` when `hasNextPage` is true; derive
a previous page from `page > 1` and page counts from `ceil(totalDocs / limit)` only for exact totals.
The separate `/count` endpoint and bounded `max` semantics remain unchanged.

## OpenAPI tags

For OpenAPI 3.2, replace `x-tagGroups` with `Tag.parent` and a `kind: "nav"` parent tag per group once swagger-core supports 3.2 (swagger-api/swagger-core#5326).
