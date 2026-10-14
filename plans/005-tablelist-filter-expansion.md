# Plan 005: Generalise TableList filtering across API v2 relationships and runtime fields

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan in
> `plans/README.md`.

> User authorized continuation past the twice-failed verification stop on 2026-09-24.
> Correct routine implementation/test defects and continue verification.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: HIGH
- **Depends on**: existing baseline `dea38d23f43472c82c49488d9e4c1de4d1e0af43`
- **Category**: feature / architecture
- **Review**: implementation and adversarial fixes reviewed 2026-09-24; acceptance checks pass
- **Baseline**: `dea38d23f43472c82c49488d9e4c1de4d1e0af43`, confirmed 2026-09-23. All production changes are already committed; no additional baseline commit is needed. Existing untracked prototypes, screenshots, logs and plans are excluded from implementation drift.

## Drift check

The existing commit recorded above contains the production implementation required as baseline.
Use it in the drift check below; do not create an empty or unrelated baseline commit. Preserve the commit's existing scope; do not reset or selectively discard the
working changes merely to make the tree look clean.

Then run this before changing source files:

```bash
git diff --stat dea38d23f43472c82c49488d9e4c1de4d1e0af43 -- \
  src/main/java/com/researchspace/api/v2 \
  src/main/java/com/researchspace/dao/query \
  src/main/java/com/researchspace/model/collection \
  src/main/webapp/ui/src/modules/common/collection \
  src/main/webapp/ui/src/modules/common/relationship-picker \
  src/main/webapp/ui/src/modules/common/table-list
```

Also run `git status --short --untracked-files=all` for these paths: a commit-range diff alone
misses staged, unstaged, and untracked drift. Record and reconcile relevant changes before coding.

The executor must not reset, clean, or selectively discard existing files. Untracked prototypes
are not production implementation and remain untouched. If the baseline commit is not present,
stop before implementation.

## Why this matters

TableList currently has the foundations for filtering related records and runtime custom/extra fields,
but the capability is split between API v2 metadata, hand-written relationship sources, and query
compiler special cases. A new API v2 collection still needs bespoke frontend wiring to make a
relationship selectable, and the backend metadata and compiler rules can diverge from what the UI
offers. The expansion should provide one contract that lets every API v2 TableList filter by direct
fields, relationship identity, one-hop target properties, and runtime fields while preserving row-level
authorization, pagination totals, saved views, and bounded query complexity.

## Current state

### Frontend

- `src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata.ts`
  parses API metadata for direct selectors, `relationshipFields`, runtime namespaces, operator
  capabilities, wildcard support, and query limits. Relationship descriptors currently expose only
  operator/wildcard/title/type information (`:66-76`); they do not describe a generic value picker or
  the target resource's identity format.
- `src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/apiV2FilterFields.ts`
  derives one-hop target fields from `relationshipFields` (`:131-167`) and runtime fields from a
  catalog (`:169-232`). It maps API wire operators to semantic TableList operators and creates
  hidden filter fields. It does not provide a generic relationship option source.
- `src/main/webapp/ui/src/modules/common/table-list/components/filters/FilterValueInput.tsx`
  selects `RelationshipPicker` only when `relationshipSources[field.relationTo]` exists; the shared
  registry is currently domain-specific. Select fields use explicit options, and runtime fields
  with catalog options become select fields.
- `src/main/webapp/ui/src/modules/common/relationship-picker/relationshipSources.tsx`
  defines the reusable `RelationshipSource` contract, but the exported registry currently contains
  only the instruments source. Sources own search, restore, ownership checks, and option rendering.
- `src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/createApiV2CollectionAdapter.ts`
  adds derived relationship/runtime fields, requests `depth=1` when a derived relationship field is
  visible, and restores expanded target properties after sparse response validation (`:102-191`).
  The response projection and renderer logic assumes one relationship hop.
- `src/main/webapp/ui/src/modules/common/table-list/components/filters/TableListFilters.tsx`
  owns filter-row editing, operator transitions, value parsing, runtime-field selection, and saved
  groups. `defaultOperator` and `valueForOperator` are the central places where a field's metadata
  becomes UI state.
- `src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/useApiV2TableList.ts`
  discovers runtime selectors referenced by saved views/visible fields, blocks remote loading until
  saved state and runtime metadata are ready, and includes the effective request in the query key.

### Backend and API contract

- `src/main/java/com/researchspace/dao/query/RsqlRelationshipCompiler.java` compiles relationship
  identity and target-property predicates into correlated `EXISTS` subqueries. It applies target
  access constraints, but relationship target property descriptors are currently supplied by
  resource-specific registration and target paths are effectively one hop.
- `src/main/java/com/researchspace/dao/query/RsqlRuntimeFieldCompiler.java` compiles direct and
  relationship runtime-field predicates. Through-relationship runtime fields require exactly one
  target (`compileRuntimeFieldThroughRelationship`), and presence semantics are implemented as a
  value-table `EXISTS` predicate.
- `src/main/java/com/researchspace/dao/query/CollectionQueryExecutor.java` applies caller filters,
  server constraints, relationship access, and pagination/count through the same query builder. Any
  expansion must keep identical access/filter predicates for page and count queries and keep
  parameter namespaces separate. Separate SQL statements for page and total are allowed.
- `src/main/java/com/researchspace/api/v2/resource/ApiV2ResourceCatalog.java` builds the validated
  resource/relationship graph and delegates runtime-field providers. Target-only resources can be
  readable without CRUD routes.
- `src/main/java/com/researchspace/api/v2/resource/ApiV2RelationshipTargetSpec.java` resolves
  readable target IDs in batches and narrows readable fields. It is the authorization boundary for
  target-only relationships.
- `src/main/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGenerator.java` emits
  `x-rspace-filter`, `x-rspace-relationship-fields`, and `x-rspace-runtime-fields` extensions. The
  relationship extension currently publishes field capabilities but no generic option-source
  contract. OpenAPI is a static/public schema; it cannot describe which fields a particular caller
  may read.
- `src/main/java/com/researchspace/api/v2/query/ApiV2ResourceRequestParser.java` and the collection
  model validate selectors, operators, values, field selection, sort, depth, and query limits before
  the DAO is called. Do not bypass this parser with raw frontend-specific query syntax.
- `DevDocs/DeveloperNotes/RestApiV2Collections.md` and
  `DevDocs/DeveloperNotes/TableListAdapters.md` document the current one-hop relationship and
  runtime-field behavior. They must be updated with the final generic contract and examples.

### Existing verification patterns

- Java query/compiler contracts: `src/test/java/com/researchspace/api/v2/contract/ApiV2QueryContractMVCIT.java`
  and `ApiV2RelationshipContractMVCIT.java`.
- Java metadata/OpenAPI/resource registration: `src/test/java/com/researchspace/api/v2/config/ApiV2ResourceConfigTest.java`
  and `src/test/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGeneratorTest.java`.
- Java authorization and hidden-field behavior: `src/test/java/com/researchspace/api/v2/resource/ApiV2ResourceAccessTest.java`.
- Frontend adapter and metadata behavior:
  `src/main/webapp/ui/src/modules/common/table-list/__tests__/adapters/apiV2/derivedFilterSelectors.test.ts`,
  `runtimeFields.test.ts`, `useApiV2TableList.test.tsx`, and
  `createApiV2CollectionFetcher.test.ts`.
- Frontend user flows: `TableListFilters.test.tsx`, `RelationshipPickerFilter.test.tsx`,
  `SuggestedValueInput.test.tsx`, and Browser Mode `TargetFieldFilter.spec.tsx`.

### Reviewed implementation boundary

Already implemented: select fields default to `equals`, text fields prefer `contains`, primitive
one-hop fields and runtime options are derived from metadata, visible columns request their
projection/depth dependencies, and saved-view hydration blocks incomplete requests. Preserve these
paths and their tests; change them only for a demonstrated gap introduced by the new metadata.

Remaining work: additive picker identity metadata, source lookup and bounded batch restore,
runtime catalog URL validation, and missing compatibility/access tests. Existing relationship
compilers and registry traversal are the default implementation, not candidates for replacement.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Frontend focused tests | `pnpm test src/modules/common/table-list` | All matching Vitest files pass. |
| Frontend Browser Mode | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/common/table-list/ApiV2FilterExpansion.spec.tsx` | Component/MSW scenarios pass without a backend. |
| Live E2E | `RSPACE_BASE_URL="$TABLELIST_APP_URL" E2E_INTEGRATION_MODE=real pnpm test-e2e src/modules/booking/__tests__/ApiV2FilterExpansion.e2e.ts --project=chromium` | Authenticated real-backend flow passes using the existing Playwright setup. |
| TypeScript | `pnpm tsc` | Exit 0 with no type errors. |
| Frontend lint | `pnpm lint` | Biome exits 0. |
| Java query/API unit tests | `mvn test -Dtest=CollectionQueryExecutorTest,RuntimeFieldQueryTest,ApiV2OpenApiGeneratorTest,ApiV2ResourceConfigTest -Dfast=true` | All selected tests pass. |
| Java API contracts | Use the isolated-database command below. | All selected MVC/authorization tests pass without changing the live development schema. |
| Diff hygiene | `git diff --check` | No whitespace errors. |

Set `TABLELIST_APP_URL` to the URL reported by `./docker/dev/rspace-dev ps`. The `.spec.tsx`
and `.e2e.ts` files above are new tests to add in Step 7; the runners must not be interchanged.

Prepare a separate test schema using the repository test setup. Confirm the schema and connection
before running database tests. Set `TABLELIST_TEST_DB`, `TABLELIST_TEST_JDBC_URL` (including the
schema), and `TABLELIST_TEST_SERVER_URL` for that isolated database, then run:

```bash
mvn test -Dtest=ApiV2QueryContractMVCIT,ApiV2RelationshipContractMVCIT,ApiV2ResourceAccessTest \
  -Denvironment=keepdbintact \
  -Djdbc.db.maven="$TABLELIST_TEST_DB" -Djdbc.db="$TABLELIST_TEST_DB" \
  -Djdbc.url="$TABLELIST_TEST_JDBC_URL" -Djdbc.url.maven="$TABLELIST_TEST_SERVER_URL"
```

Use these overrides for every database-test invocation below. `keepdbintact` alone is not database
isolation and does not prevent test code from committing writes or running cleanup.
Do not run Maven `install` or deploy goals. Use the project's supported JDK for Maven/Spotless.

## Scope

### In scope

- The API v2 filter metadata contract and its OpenAPI extension parsing/generation.
- Generic relationship identity and one-hop target-property filtering for API v2 collections.
- Generic runtime custom/extra-field filtering, including fields exposed through one relationship.
- Shared frontend field derivation, value controls, relationship option-source registration, query
  serialization, sparse projections, saved-view restoration, and accessibility.
- Authorization, query complexity limits, pagination/count correctness, and performance tests.
- Focused Java/TypeScript/Browser tests and the two developer guides listed above.

### Out of scope

- Changing the RSQL operator vocabulary or wire tokens.
- Arbitrary-depth relationship traversal, recursive relationship search, or free-form client-supplied
  joins. A later extension must introduce a separate bounded path contract and complexity budget.
- New CRUD resources unrelated to demonstrating the generic filter contract.
- Changing existing booking permission semantics, table sorting, pagination controls, or saved-view
  storage format except where required to preserve filter compatibility.
- Client-side filtering of a remotely paginated collection.
- Exposing unreadable target values, runtime catalog values, or relationship IDs as a side channel.
- Actor-specific field lists in the public OpenAPI document; public metadata describes the safe
  schema, while query predicates and response field narrowing enforce caller-specific access.

## Contract decisions

The compatibility and access decisions below are fixed for this implementation. Record the
workload, predicate ceiling and performance budget before compiler changes; do not infer new
identity formats or authorization semantics during implementation.

1. **Supported relationship depth:** v1 remains one hop. The metadata must reject selectors with
   more than one relationship boundary.
2. **Relationship identity value:** the source adapter owns normalization, validation and wire
   serialization. Prefer canonical global IDs such as `IN1234` where the target already supports
   them. Preserve typed numeric `.value`, `.relationTo`, and `createdBy.value==me` filters and saved
   views. User/audit relationships without global-ID prefixes keep their existing typed-ID contract;
   do not invent prefixes, migrate saved values, or remove published selectors. New metadata is
   optional and additive. Normalize global-ID case/whitespace only where valid for that source;
   do not apply that transformation to case-sensitive values such as the existing `me` token.
3. **Option source:** relationship search and restore use the existing static client registry keyed
   by API resource name. Sources own same-origin routes, query encoding, validation, batch restore
   and display policy; the server enforces read access. Metadata may select a source, not define a
   route or executable query. Apply the catalog URL policy below to runtime sources as well.
4. **Target property publication:** publish safe primitive target fields with explicit operators.
   Do not add internal entity properties merely because they exist. Existing identity selectors
   (`value`, `relationTo`) remain published with unchanged semantics. Response narrowing and query
   field authorization still enforce caller-specific access; public metadata is not authorization.
5. **Runtime fields:** preserve direct and existing single-target, one-hop runtime filtering and
   opt-in projection limits. Catalog search/restore uses the collection's caller authorization.
   Extending runtime traversal to multiple targets is out of scope.
6. **Target shape:** the new picker supports one target and one relationship hop. This restriction
   does not invalidate existing multi-target API registrations, scalar property predicates, or
   OpenAPI metadata. Preserve the registry's compatible operator/type intersections and the
   compiler's authorized branch per target. Existing filters without a supported picker keep their
   typed input/restore path; never silently drop them. Do not add multi-hop traversal.
7. **Null and negation semantics:** preserve the explicit truth tables below. Inaccessible and absent
   targets must remain indistinguishable within each selector family; identity `exists=false` and
   through-relationship runtime `exists=false` intentionally have different meanings.
8. **Saved selector stability:** direct, relationship, and runtime selectors are stable resource-
   scoped names. A missing catalog definition is a distinct stale-view error; it is never silently
   dropped or treated as an empty filter.
9. **Budgets:** use the API-published maximum comparisons, LIKE comparisons, nesting, arguments,
   where length, catalog IDs, and runtime projections. Also define a maximum combined relationship/
   runtime predicate count and a measured p95 query budget. Never raise limits to make a test pass.

### Implementation contract frozen 2026-09-23

The optional `picker` member belongs to the existing `x-rspace-filter.selectors`
identity descriptor. Its shape is `{resource, identity: "globalId", globalIdPrefix}`.
Publish it only for single-target relationships with an existing global-ID selector.
The client registry retains explicit target aliases such as `booking-instruments`
and their existing booking-specific discovery and archived-reference source. No endpoint URL or new identity format is introduced.
Runtime catalogs retain their existing metadata and use a static client allowlist
of supported resource/namespace pairs, including delegated instrument catalogs.

The combined relationship/runtime predicate ceiling is the existing maximum of 50
comparisons, with direct comparisons consuming the same budget. No limit is raised.
The acceptance workload is 100 source configurations and target instruments with
runtime values, page size 25, and up to 50 comparisons including fixture scoping.
Compare SQL counts at one and 25 matching rows. After three warmups, measure 20
list/count samples, with a local Docker MariaDB p95 budget of 2,000 ms per request.
Record the observed counts and representative EXPLAIN plan before acceptance.
Compiler changes still require a demonstrated failing test.

### Existing negation semantics to preserve

For relationship **identity** selectors, matching means equality to the operand, or membership in
the operand set for `in`/`notIn`:

| Target state | `exists=true` | `exists=false` | `equals` / `in` | `notEquals` / `notIn` |
| --- | --- | --- | --- | --- |
| No relationship | false | true | false | false |
| Inaccessible target | false | true | false | false |
| Readable, nonmatching target | true | false | false | true |
| Readable, matching target | true | false | true | false |

For a **single-value runtime field through a relationship**, a present value is neither NULL nor empty:

| Target/value state | `exists=true` | `exists=false` | `equals` / `in` | `notEquals` / `notIn` |
| --- | --- | --- | --- | --- |
| No relationship or inaccessible target | false | false | false | false |
| Readable target, missing/NULL/empty value | false | true | false | false |
| Readable target, present nonmatching value | true | false | false | true |
| Readable target, present matching value | true | false | true | false |

Direct single-value runtime fields use the value rows of the second table without the target-access
condition. Preserve existing per-type membership semantics for multi-valued fields and test them
separately; do not extrapolate scalar negation to multiple value rows.
Target primitive-property predicates require a readable target and then apply the existing scalar
operator/null semantics. Test nullable scalar fields separately; do not turn a negated property
comparison into `NOT EXISTS` over readable targets. Preserve existing multi-target predicate behavior.

### Runtime catalog URL policy

Keep the existing `catalog` metadata field for older clients, but validate it before any fetch or
Authorization header is sent. Accept only root-relative paths matching registered runtime catalog
routes, currently `/api/v2/{resource}/fields/{namespace}`, including known delegated catalog sources.
Validate the resource/namespace pairing, not just the `/api/v2/` prefix. Do not infer the source from
`viaResource` alone: target-only aliases can delegate to another resource.

Reject absolute and protocol-relative URLs, credentials, traversal or encoded path separators,
queries/fragments in the supplied catalog URL, and unrelated same-origin paths. Construct query
parameters locally with `URLSearchParams`. Recheck at the shared fetch boundary so direct callers
cannot bypass metadata validation; disallow redirects for authenticated catalog requests. Retain
valid server-generated catalog paths. Tests must assert zero requests for rejected metadata and
that an allowed endpoint cannot redirect the request to an unapproved route/origin.

### Batched selection restore contract

Add a source method `resolveMany(values, token, signal)` returning options keyed by the source's
canonical value. Each source declares its batch limit, no greater than the endpoint's published
argument/page limits. Deduplicate values, split larger selections into bounded batches, and restore
without a prior search. For instruments, validate global IDs inside the adapter and use the existing
collection endpoint with a typed ID `in` filter and sparse display fields. Other adapters use their
existing identity contract; filter controls do not parse database IDs.

The query key includes source, caller scope and canonical values. Preserve selected values while
loading or after a failure; omit unknown/inaccessible values from returned options identically,
without discarding their saved filter rules. Report malformed values as validation errors rather
than silently applying fewer filters. Keep the existing single-value method for other callers until
migrated; the new filter path must not make one restore request per selected value.

## Steps

### Step 0: Establish the implementation baseline

Use the recorded existing baseline commit and verify the in-scope production paths before starting
implementation. Preserve untracked prototypes and other artifacts without including them in feature
changes. No additional baseline commit is required.

**Verify**: `git rev-parse --short HEAD` matches the SHA recorded above, no in-scope production path is
untracked, and `git diff --stat dea38d23f43472c82c49488d9e4c1de4d1e0af43 -- <in-scope paths>` is empty. An unrelated
dirty path is not a failure of this step.

### Step 1: Freeze the generic contract and capability matrix

Define the typed shape for:

- direct field capabilities;
- relationship identity selectors and target-property selectors;
- runtime field namespaces and catalog definitions;
- the resource identity/canonical-ID information needed to look up an allowlisted picker source;
- per-resource limits and one-hop path validation.

Use the existing semantic `FilterOperator` model in
`src/main/webapp/ui/src/modules/common/collection/collectionConfig.ts` and the existing API wire
operator mapping in `apiV2FilterFields.ts`. Keep resource names, selector names, and canonical global
IDs opaque to filter controls except through the allowlisted source adapter. Add parser rejection
tests for unknown operators, malformed relationship descriptors, unsupported field types, duplicate
selectors, malformed picker descriptors, and paths deeper than one hop. Add regression tests that
existing multi-target API metadata and typed-ID selectors are still accepted; an unsupported picker
must not make the resource or its saved filters invalid. Explicitly decide that target
select/enum fields are out of scope for v1 unless a safe option descriptor is added to the contract.

**Verify**: run `pnpm test src/modules/common/table-list/__tests__/adapters/apiV2/apiV2CollectionMetadata.test.ts src/modules/common/table-list/__tests__/adapters/apiV2/derivedFilterSelectors.test.ts` and `mvn test -Dtest=ApiV2OpenApiGeneratorTest,ApiV2ResourceConfigTest -Dfast=true`; metadata and startup validation tests pass.

### Step 2: Make API v2 metadata publish the same capabilities the query compiler accepts

Update resource descriptions and `ApiV2OpenApiGenerator` so every published relationship field has
the exact operator/type/title rules enforced by `RsqlRelationshipCompiler`. Publish only the static,
caller-independent schema and canonical resource identity needed by an allowlisted picker source;
never publish arbitrary picker URLs or actor-specific field lists. Ensure target field publication
never exposes an internal field merely because it exists on the entity. Keep caller-specific access
in the query predicate and response field narrowing.

Validate at catalog startup that:

- every relationship selector names a registered relationship and target field;
- every operator is compatible with the target field type;
- new picker descriptors name one target and one hop; existing multi-target API selectors retain
  their compatible type/operator intersections;
- every runtime namespace has a valid source, an approved catalog route and bounded catalog limits;
- every delegated target has compatible ID/entity metadata;
- no duplicate relationship/runtime selector shadows a source field.

Keep OpenAPI output deterministic so contract snapshots do not change with map iteration order.

**Verify**: run `mvn test -Dtest=ApiV2OpenApiGeneratorTest,ApiV2ResourceConfigTest -Dfast=true` and inspect the generated test document for `x-rspace-filter`, `x-rspace-relationship-fields`, and `x-rspace-runtime-fields` on at least one direct resource and one target-only resource. Assert that the document contains no arbitrary picker URL and makes no claim that a field is readable for every actor.

### Step 3: Verify backend compatibility and fill demonstrated compiler gaps

Reuse `RsqlRelationshipCompiler`, `RsqlRuntimeFieldCompiler`, and any required model/registry
interfaces. Extend them only where a failing contract test demonstrates a missing capability:

- compare relationship references by source-owned kind/ID pairs for equals, not-equals, in, and
  not-in;
- compile target primitive fields through a correlated `EXISTS` subquery;
- preserve readable target constraints in every positive and negative predicate;
- preserve `exists` and null semantics;
- support all declared target types/operators without string concatenation of user values;
- canonicalise and validate global-ID/reference values before converting them to typed pairs;
- keep one-hop validation explicit, preserve existing multi-target scalar/identity queries, and
  retain single-target runtime traversal; reject only new unsupported or ambiguous bindings;
- keep caller, access, request, and nested-subquery parameter names isolated;
- keep `count`, paged list, list-by-ID, and bulk selection on the same predicate path.

Do not fetch target entities into Java to filter them. Use the existing `CollectionQueryExecutor`
path so filtering, totals, ordering, and pagination are calculated in the database. Add indexes or
query-plan work only when an explain/test measurement shows a real regression. Before accepting the
implementation, measure the agreed maximum combined relationship/runtime predicate count on a
representative fixture and record the p95 query time and an `EXPLAIN` shape. A query that exceeds the
budget must be rejected or simplified; do not silently fall back to in-memory filtering.

**Verify**: run `mvn test -Dtest=CollectionQueryExecutorTest,RuntimeFieldQueryTest -Dfast=true`, then `mvn test -Dtest=ApiV2QueryContractMVCIT,ApiV2RelationshipContractMVCIT,ApiV2ResourceAccessTest`. The tests must cover readable and unreadable targets, missing/null relationships, equals/in/not-in, exists, target number/boolean/date/text fields, runtime fields, count totals, unauthorized selector rejection, malformed/canonical global IDs, and the full negative-predicate truth table. The performance fixture must show identical compiled predicates for page and count, a recorded bounded SQL statement count independent of row count, no per-row target lookup, and p95 within the recorded budget. Separate page/count statements are valid; do not redesign pagination to force one statement.

### Step 4: Replace page-specific relationship pickers with a generic source registry

Refine `RelationshipSource` and `RelationshipPicker` so a TableList can obtain a source from a
small, static, allowlisted application registry keyed by API resource name. Metadata may select a
registered source but may not create one or supply its URL. Each source must:

- search by display text and the source's supported identity format;
- restore selected values using the bounded `resolveMany` contract above without a prior search;
- validate malformed values without throwing during render or silently dropping saved predicates;
- return a stable value and accessible display content;
- expose loading, empty, and failed states;
- preserve selected options while a new search is in flight;
- support single and multi-value operators using the same source.

Keep domain-specific availability checks outside the generic picker. A source may add an optional
availability adapter, but filtering must still work when no availability endpoint exists. Every
search and restore endpoint must enforce the caller's read access and return indistinguishable
results for unknown versus inaccessible IDs. Register the existing instruments source through the
new path and add a second non-booking fixture source in tests to prove the abstraction is not
instrument-specific.

**Verify**: run `pnpm test src/modules/common/table-list/__tests__/components/RelationshipPickerFilter.test.tsx src/modules/common/table-list/__tests__/components/SuggestedValueInput.test.tsx src/modules/common/table-list/__tests__/components/TableListFilters.test.tsx`; add assertions for search, paste/restore, multi-select, malformed values, loading, failure, and accessibility.

### Step 5: Integrate picker metadata with existing field and saved-view behavior

Reuse `apiV2FilterFields.ts`, `createApiV2CollectionAdapter.ts`, `FilterValueInput.tsx`, and
`TableListFilters.tsx` so the same metadata drives field options, operators, values, projections,
and serialization while leaving the public `TableList` component API unchanged. The items below
are regression requirements, not instructions to rebuild working behavior. Add only source lookup,
batch restore, catalog validation, or fixes demonstrated by failing focused tests:

- include relationship identity fields and target primitive fields with stable hierarchical labels;
- choose `equals` as the default for select/single-value equality and retain `contains` for text search;
- render select options without a misleading placeholder; keep multi-value selection scrollable and
  keyboard accessible;
- use the generic relationship picker for relationship fields and relationship identity virtual
  fields;
- resolve relationship sources only from the allowlisted registry; an unavailable source leaves the
  filter usable with its existing validated typed identity input or reports a clear source-unavailable
  state without invalidating otherwise supported API metadata or saved rules;
- use catalog-backed controls for runtime options and free-text/suggestion controls for unbounded
  values;
- set runtime/relationship projection dependencies so a visible derived column has its owner/value
  data selected;
- preserve operator/value transitions when changing between scalar and list operators;
- keep filters active when the selected value is not currently in the suggestion page;
- preserve and clearly report stale/malformed saved selectors instead of issuing a request with an
  incomplete field catalog.

Reuse existing tests for field kinds and saved-view metadata loading. Add missing cases for the new
picker contract, caller changes, denied catalog URLs and compatibility with legacy identity values. The filter UI must never offer an operator that the API
metadata does not publish.

**Verify**: run `pnpm test src/modules/common/table-list/__tests__/components/TableListFilters.test.tsx src/modules/common/table-list/__tests__/adapters/apiV2/useApiV2TableList.test.tsx src/modules/common/table-list/__tests__/adapters/apiV2/runtimeFields.test.ts src/modules/common/table-list/__tests__/components/TableListControlPanel.test.tsx`; all focused tests pass.

### Step 6: Verify query-state, projection, and API compatibility

Exercise the full request path from a TableList state to API v2 query parameters and back:

- direct and relationship fields serialize to the documented RSQL tokens;
- identity values are validated by their source; quote/escape valid string values and reject malformed
  global IDs rather than claiming arbitrary punctuation is valid in a prefixed numeric global ID;
- scalar/list values round-trip through URL state and saved views;
- base filters combine with user filters using AND and retain parameter isolation;
- required relationship depth is raised only when a derived field is selected;
- fixed projections still include runtime selectors and renderer dependencies;
- API responses with omitted sparse fields validate correctly;
- old saved views using direct fields, numeric relationship `.value`, `me`, and existing multi-target
  selectors remain readable with unchanged wire values;
- relationship/runtime selectors remain resource-scoped and stable across deployments;
- unknown/removed fields show the existing restored-view error and do not make a request;
- a temporarily unavailable runtime catalog/source is distinguishable from a removed selector and
  does not silently clear a saved rule;
- query keys include the effective filter/field/runtime scope and do not reuse another caller's data.

Use the existing query-string, RSQL, fetcher, and saved-view tests as the structure. Add a contract
fixture that combines search, a direct filter, a relationship ID filter, a target property filter,
and a runtime custom-field filter in one request. Verify that an older client which ignores the new
metadata extensions still receives the existing direct-field API contract and that the extensions are
additive.

**Verify**: run `pnpm test src/modules/common/table-list/__tests__/queryStringState.test.ts src/modules/common/table-list/__tests__/rsqlCodec.test.ts src/modules/common/table-list/__tests__/adapters/apiV2/collectionQueryParams.test.ts src/modules/common/table-list/__tests__/adapters/apiV2/createApiV2CollectionFetcher.test.ts src/modules/common/table-list/__tests__/adapters/apiV2/useApiV2TableList.test.tsx` and `pnpm tsc`.

### Step 7: Add component and real-stack coverage, then document the reusable pattern

Add component/MSW scenarios for loading, failure, no-match, malformed-value, and catalog pagination
states, including Browser Mode `.spec.tsx` tests using the existing MSW setup. These tests do not
require a backend. Add a separate Playwright `ApiV2FilterExpansion.e2e.ts` test using the existing
authentication/fixture helpers, `RSPACE_BASE_URL`, and a running concrete API v2 collection with:

- a direct select filter defaulting to equals;
- relationship filtering by pasted global ID and by selected option;
- a target property filter with a readable and an inaccessible target;
- a runtime custom/extra field filter and a saved-view reload;
- empty, loading, no-match, malformed-value, and pagination states;
- mobile/narrow layout with many selected values in a scrollable control;
- no console errors and no request containing unescaped raw user input.

Use MSW for component/Browser Mode tests, never for the live E2E flow. Before E2E, verify the per-worktree dev
stack using the `rspace-dev-stack` instructions; seed two readable targets, one inaccessible target,
and runtime values for the selected resource. Capture the request URLs and assert that inaccessible
and unknown IDs produce the same observable result. Reuse an already-running stack; starting one
must follow repository authorization rules. Clean up only records created by the test and preserve
other fixtures. Run database contract tests in the separate schema specified above. Update
`DevDocs/DeveloperNotes/TableListAdapters.md` with the generic source registration and filter
configuration steps, and `DevDocs/DeveloperNotes/RestApiV2Collections.md` with the metadata
extensions, one-hop rule, access semantics, limits, and examples. Add an OpenAPI example showing
how a new resource opts into relationship target filtering without booking-specific code.

**Verify**: run the focused MSW/Vitest and Browser Mode commands, then the separate `test-e2e` command from the command table against the real `booking-configurations` fixture. Follow with `pnpm lint`, `pnpm tsc`, the isolated Java contract command, and `git diff --check`. The E2E run must pass with no page console errors, no request containing unescaped raw user input, and no observable distinction between unknown and inaccessible target IDs.

## Release slices

Keep the implementation reviewable by shipping these vertical slices in order:

1. **Contract and backend slice:** baseline commit, additive metadata, legacy identity compatibility,
   one-hop target fields, SQL predicates, access truth table, and Java contract tests.
2. **Relationship-picker slice:** the allowlisted source registry, batched restore, source access
   behavior, and TableList relationship identity controls.
3. **Runtime/saved-view slice:** direct and one-hop runtime catalogs, projections, selector restore,
   stale/unavailable states, and query-key coverage.
4. **Browser/documentation slice:** real-stack workflow, mobile/scrollable multi-value behavior,
   performance evidence, and developer-guide updates.

Each slice must leave existing direct-field filtering and old clients working. Do not begin the next
slice until the preceding slice's focused verification passes.

## Test plan

### Backend

- Query compiler unit tests for every supported relationship operator/type and runtime field type.
- MVC contract tests for metadata publication, filtering, count/pagination, target-only resources,
  malformed selectors, and unauthorized/inaccessible targets.
- Access tests proving negative operators cannot reveal inaccessible target existence.
- A truth-table fixture covering no relationship, inaccessible target, readable non-matching target,
  and readable matching target for `exists=false`, `notEquals`, and `notIn`.
- Canonical global-ID tests for case, whitespace, malformed prefixes, mixed target kinds, and escaped
  `in` values.
- Limits tests for comparison count, LIKE count, nesting, argument count, where length, catalog IDs,
  and runtime projection ceilings.
- Query-plan/performance fixture with many target rows and runtime values; record that filtering is
  performed in SQL and does not issue one query per row; record the p95 budget and `EXPLAIN` shape.

### Frontend

- Metadata parser and field-derivation tests for direct, relationship, runtime, select, and invalid
  descriptors.
- Filter panel tests for default operators, scalar/list transitions, option selection, free text,
  relationship values, loading/failure/restore, accessibility, and scrollable multi-value controls.
- Picker-source tests proving only allowlisted same-origin resources are used, restore is batched,
  malformed values do not throw, and unknown/inaccessible values are indistinguishable.
- Query-state/fetcher tests for serialization, escaping, depth, projection dependencies, cache keys,
  base-filter composition, and malformed saved views.
- Browser Mode/MSW coverage for component states and separate authenticated Playwright E2E coverage
  for one real collection and one target-only/runtime-field combination.
- Catalog URL tests covering valid delegated routes, off-origin and unrelated same-origin URLs,
  redirects, and zero requests for rejected metadata.
- Compatibility tests for numeric `.value`, `me`, existing multi-target filters, and older metadata.
- Batched restore tests covering deduplication, endpoint limits, partial missing results, failure,
  caller changes, and cancellation; no per-value request fan-out.

## Done criteria

- [x] A new API v2 collection can opt into relationship ID/property and runtime-field filters through
  its resource metadata and collection config, without changing the public `TableList` component API.
- [x] OpenAPI metadata and backend query compilation publish and enforce the same fields, operators,
  types, wildcard rules, and limits; OpenAPI remains static/public and access is enforced per caller
  in queries and response narrowing.
- [x] New pickers use each source's existing identity contract, preferring global IDs where supported.
  Legacy typed-ID selectors and saved values remain compatible; no inaccessible target values leak.
- [x] Picker sources are static and allowlisted. Runtime catalogs are validated against approved
  same-origin routes before authenticated requests, including redirect protection.
- [x] Direct, relationship, and runtime filters work in list, count, pagination, saved-view restore,
  and fixed/visible projection modes.
- [ ] One-hop boundaries, single-target picker/runtime limits, existing multi-target API support,
  typed/global-ID validation, the recorded truth tables, budgets, and malformed input are tested.
- [x] Select fields default to `equals`; text fields retain `contains`; multi-value controls have no
  misleading placeholder and remain usable with many selected values.
- [ ] Focused isolated Java tests, frontend tests, Browser Mode/MSW tests, live Playwright E2E,
  `pnpm tsc`, `pnpm lint`, and `git diff --check` pass.
- [x] Both TableList/API v2 developer guides document the generic extension path and its limits.
- [ ] The baseline commit is recorded in this plan and every release slice passes before the next
  slice begins.
- [x] No source files outside the agreed API v2/query/TableList/relationship-picker/docs/test scope
  are modified.

## STOP conditions

Stop and report instead of improvising if:

- the current uncommitted implementation cannot be committed as a clean, reviewable baseline;
- implementation requires an unapproved breaking API or saved-view change. The additive optional
  metadata and client source-method extensions specified here are authorized scope, not a stop;
- the proposed contract requires arbitrary URLs, routes, or executable query descriptions from
  OpenAPI metadata;
- the new picker requires multiple target choices or more than one hop. Existing multi-target API
  registrations and filters must continue working; their presence alone is not a stop;
- target select/enum fields are required for v1 but no safe, bounded option representation exists;
- authorization requires loading target rows in Java to decide whether the source row is readable;
- a negative relationship/runtime predicate would change the existing inaccessible-target semantics;
- the measured query p95 or `EXPLAIN` shape exceeds the agreed budget;
- a test requires raising a query/catalog/projection limit;
- a saved-view format change is required to preserve compatibility;
- a source cannot preserve its existing identity format or the implementation requires inventing a
  new global-ID prefix/migrating legacy typed-ID filters;
- a source search, restore, or runtime catalog endpoint cannot enforce the same read access as the
  collection query, or an authenticated request cannot be restricted to approved routes;
- any focused test still sends a request after metadata reports an invalid/stale selector;
- the implementation needs changes outside the Scope list, or any verification command fails twice.

## Maintenance notes

- Keep the API metadata contract authoritative. Do not add a frontend-only operator or field because
  a page wants a shortcut.
- Keep resource-specific relationship sources small, static, and allowlisted. Search/restore/display
  rules belong to the source adapter; filter parsing and query serialization belong to shared
  TableList/API v2 code. Validate legacy runtime catalog paths before using them as request targets;
  never accept arbitrary metadata-supplied URLs.
- Treat OpenAPI as a public schema contract, not an access decision. Review any target-field addition
  for both static schema exposure and caller-specific query/response authorization.
- Review every future relationship compiler change against positive, negative, `exists`, and access
  constrained queries. A seemingly harmless `NOT` can turn an inaccessible target into an existence
  oracle.
- If arbitrary-depth filtering becomes necessary, design a separate bounded path/complexity model;
  do not silently extend the one-hop selector grammar.
- Keep performance measurements with the contract tests. Target-property and runtime predicates must
  remain correlated SQL predicates, and count queries must use the same access/filter constraints as
  page queries.
- Keep the baseline commit and release-slice boundaries in the plan index so later work does not
  silently rebase the feature onto unrelated booking changes.


## Verification evidence (2026-09-24)

Implementation adds optional picker descriptors to root selectors, validates catalog routes,
derives compatible identity fields, and restores selected values in bounded caller-scoped batches.
Booking retains its own catalogue/configuration source. No query compiler rewrite or schema
migration was needed.

Acceptance testing found that `ApiV2ResourceRegistration.narrowSelection` rebuilt requests without
their resolved runtime fields. The copy now preserves `runtime`; a focused access test and the
HTTP runtime truth table cover the correction. Independent review also found a quick-search
compatibility gap for unavailable picker sources. Valid legacy IN searches are preserved and tested.

Verified so far:

- 239 focused frontend unit tests pass across 25 files.
- After the final nullability cleanup, 31 query/derived-selector tests pass again.
- `pnpm tsc`, i18n lint, scoped production/unit Biome checks, and `git diff --check` pass.
- Existing target-field Browser Mode test passes in Chromium, Firefox, and WebKit.
- The five new filter-expansion Browser Mode scenarios pass in all three engines, 15/15, with no retries.
- Both live browser MCPs passed instrument selection, filtered results, reload restoration, and
  clearing filters. No console errors. One Apply action makes one filtered collection request.
- Existing query/relationship MVC contract suites pass 63/63 against the isolated schema.
- Runtime-preservation access tests pass 18/18, parser tests 23/23, and the two new HTTP
  identity/runtime truth-table methods pass against the isolated test schema.

Live runtime filtering and reload restoration pass in both browser MCPs, using two owned
fixtures that were cleaned up afterward. The authenticated Chromium E2E against the Docker
stack also passes: six authentication setup cases and the filter-expansion scenario. It verifies
runtime values containing quotes, commas and slashes, related-name filtering, private-target
invisibility and equivalent private/unknown identity results. Its fixture cleanup assertion passes.
TypeScript and scoped E2E Biome checks pass after the final test changes. Scoped Spotless
checks pass for all six changed/new Java files. Earlier failed E2E runs left nine fixture pairs;
the configurations were removed with ETags and the instruments soft-deleted as their owners,
with follow-up reads confirming deletion.

The first maximum-comparison performance run exposed row-dependent Instrument hydration in
the booking permission projection: median list statements grew from 22 to 142 between one
and 25 matching rows. The final implementation fetches scalar permission facts for the
page in one DAO query while preserving Inventory's read, direct-edit, owner, and sysadmin
rules. The transactional DAO matrix covers owner, group, whitelist, PI, community admin,
private, deleted, and missing instruments. The final maximum-comparison contract passes:
one-row and 25-row lists both use 14 statements; both counts use 13. Across 20 samples
after three warmups, p95 is 391 ms and 414 ms for the lists and 409 ms and 389 ms for
the counts, all below the unchanged 2,000 ms budget.

A read-only EXPLAIN of the live related-name booking filter used the booking configuration
primary index for the 14-row local table, then primary-key `eq_ref` lookups for Instrument
and User, each estimating one row. This is a representative live filter plan, not an
EXPLAIN of the 50-comparison workload; the latter's original bind values were not retained.
The contract test supplies the exact 50-comparison workload's statement counts and timing.

The final picker refresh and malformed-response checks pass 19/19 focused unit tests.
The new and adjacent Browser Mode specs pass 18/18 across Chromium, Firefox, and WebKit.
The live Docker E2E passes 7/7, including fixture cleanup. Final manual Chrome DevTools
and Firefox Playwright passes filtered Bookable Items by related instrument name, returned
one matching row, and restored all 14 rows on Clear all. Each Apply/Clear action made one
successful collection request in each browser; neither console reported errors. The DAO
and booking permission tests pass 7/7, and the maximum-workload backend contract passes
3/3. `pnpm tsc`, scoped Biome, and `git diff --check` pass. Full repository lint still
reports four unrelated untracked prototype errors; those files remain untouched.

Detailed logs and browser notes are in `.claude/plan005/`. Docker is running with the live
`rspace` database preserved; Java integration tests use `booking_subscription_test`.
