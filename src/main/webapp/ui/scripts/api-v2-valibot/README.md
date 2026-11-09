# API v2 Valibot adapter

`pnpm generate:api-v2` generates the HTTP client, then Orval's reusable Valibot
output, then runs `generate.ts`, `resources.ts` and `filters.ts` with Node, which runs TypeScript directly. Both Orval projects read the committed API v2
OpenAPI document. The adapter reads `generated/valibot/native.ts` unchanged
and writes `schemas.ts` and `operations.ts` beside it. Biome then checks and formats all three
files, as Orval does for its own output. No schema translator is retained.

The registry emitter (`resources.ts`) reads tags and explicit `x-rspace-operation` metadata from OpenAPI and writes
`generated/apiV2Resources.ts` with request functions and query-key references. CUSTOM-only tags are omitted; runtime fields
use the RUNTIME_FIELDS kind. This step runs explicitly after the Valibot adapter. It also writes
`generated/guaranteedReadFields.ts` from the `x-rspace-read-guaranteed` extension on each READ operation's `*Read`
schema, for `apiV2Selected.ts`, and `generated/apiV2ReadSchemas.ts`, which maps each of those resources to its
`*Read` Valibot schema. The map is a separate module so importing the registry does not load every schema.
Each registry entry also carries `filterResource` (the list's `x-rspace-filter`, omitted when the list has none) and
`runtimeFieldNamespaces` (the runtime-field path parameter's enum). The registry ends with per-resource assertions
that each list and runtime-field operation is assignable to `ApiV2ListOperation` and `ApiV2RuntimeFieldsOperation`,
so a resource that diverges from the shared shape fails `pnpm tsc`.

`mutators.ts` chooses each operation's transport mutator for `orval.config.ts`: binary-only successes use
`apiV2BlobFetch`, an explicit allowlist uses `apiV2HeadersFetch`, and unsupported success shapes or unread `ETag`
headers fail generation.

The filter step (`filters.ts`) reads `components.x-rspace-filters` and writes `generated/filters.ts`: per filter, each
filterable selector with its allowed RSQL operators and value type. It then narrows each Orval `<Name>Parameter = string`
model for a `where` parameter that carries `x-rspace-filter` to `ApiV2Where<"<Filter>">`, and fails if Orval's model shape
changes. This is the adapter's only rewrite of Orval output. `orval.config.ts` gives the same
operations `apiV2SerializeQuery` as Orval's `paramsSerializer`, so a `where` object reaches the server as RSQL.

The schema-only preflight rejects unsupported keywords, formats, references and
inline schemas, including errors and headers. New schema constructs require an
explicit extension of the supported subset and tests; Orval accepting a construct
does not bypass this drift check.

Orval 8.49 emits `v.readonly()` for `readOnly` arrays and types each lazy reference that closes a
cycle with the exported input and output types it generates. Native validation actions are kept as
Orval emits them, including `v.url()` for `format: uri` and Unicode-aware lengths.

`schemas.ts` preserves `<ComponentName>Schema` exports and two-way assignability
assertions against Orval's HTTP models, compiled by `pnpm tsc`.

`operations.ts` registers JSON bodies and JSON success schemas by operation ID,
status and media type. It keeps public and browser-session metadata, and empty
entries for downloads and empty responses. Binary bodies are omitted. Duplicate
operation IDs, missing JSON schemas and missing native exports fail generation.
Because native output selects one body and one response schema, distinct JSON
request media schemas or distinct JSON success schemas fail with a request for
synthetic operations. They must never silently map to a single native schema.

Objects retain Orval's native parsing behavior. The transport validates and
returns the original JSON, preserving extension members and runtime metadata even
when a schema's parsed output strips them. Projection code keeps its existing
schema exports and can still pick and require object members.

Fragment tests generate native schemas through the pinned Orval installation and
exercise their behavior through the adapter. Type tests compare the schemas with HTTP models that
Orval generates from the same fragment, including cycles and readonly arrays, and prove that
deliberate model drift fails compilation.

The scripts name generated exports with Orval's own helpers (`pascal`, `camel`, `sanitize`, `getTagKey`
and `Verbs`, imported from `orval`), so registry and adapter names always match Orval's output.
Importing Orval loads esbuild, which only works in Node's realm, so these tests and the
`api-v2-invalidation` checks form the `scripts` Vitest project in the node environment, separate from the
`jsdom` and `node` projects. `pnpm test` runs all three; `pnpm test --project scripts` runs only these. CI runs them
in the Vitest job and again after regeneration in the `api-v2-contract` job.
