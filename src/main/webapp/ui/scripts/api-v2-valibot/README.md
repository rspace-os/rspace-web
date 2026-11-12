# API v2 Valibot adapter

`pnpm generate:api-v2` generates the HTTP client, then Orval's reusable Valibot
output, then runs `generate.mjs` and `resources.mjs`. Both Orval projects read the committed API v2
OpenAPI document. The adapter reads `generated/valibot/native.ts`, adjusts it, and
writes `schemas.ts` and `operations.ts` beside it. No schema translator is retained.

The registry emitter (`resources.mjs`) reads tags and explicit `x-rspace-operation` metadata from OpenAPI and writes
`generated/apiV2Resources.ts` with request functions and query-key references. CUSTOM-only tags are omitted; runtime fields
use the RUNTIME_FIELDS kind. This step runs explicitly after the Valibot adapter.

The schema-only preflight rejects unsupported keywords, formats, references and
inline schemas, including errors and headers. New schema constructs require an
explicit extension of the supported subset and tests; Orval accepting a construct
does not bypass this drift check.

`schemas.ts` preserves `<ComponentName>Schema` exports and two-way assignability
assertions against Orval's HTTP models, compiled by `pnpm tsc`. Closing recursive
lazy references carry their corresponding model type. Readonly arrays are adjusted
from each local spec, including nested properties, references and composed schemas.
The adapter removes native URL validation to retain plain-string URI semantics;
other native validation actions, including Unicode-aware lengths, are preserved.

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
exercise their behavior through the adapter. Type tests include cycles and
readonly arrays, and prove that deliberate model drift fails compilation.
