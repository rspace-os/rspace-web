# API v2 Valibot adapter

`pnpm generate:api-v2` generates the HTTP client, then Orval's reusable Valibot
output, then runs `generate.mjs`. Both Orval projects read the committed API v2
OpenAPI document. The adapter reads `generated/valibot/native.ts`, removes URL validation from it,
and writes `schemas.ts` and `operations.ts` beside it. No schema translator is retained.

The registry emitter (`resources.mjs`) reads collection operation metadata from OpenAPI and writes
`generated/apiV2Resources.ts` with request functions and query-key references.

The schema-only preflight rejects unsupported keywords, formats, references and
inline schemas, including errors and headers. New schema constructs require an
explicit extension of the supported subset and tests; Orval accepting a construct
does not bypass this drift check.

Orval 8.48 emits `v.readonly()` for `readOnly` arrays and types each lazy reference that closes a
cycle with the exported input and output types it generates. Orval maps `format: uri` to `v.url()`
and has no option to keep a plain string, so the adapter removes that action to retain plain-string
URI semantics. Other native validation actions, including Unicode-aware lengths, are preserved.

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
