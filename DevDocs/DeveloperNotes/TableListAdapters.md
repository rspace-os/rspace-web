# Connect an endpoint to Table List

Use the REST API v2 hook to show a REST API v2 collection in `TableList`. Tables can also show rows
already in the browser or rows from any other endpoint, without a backend collection; see
[Table List data sources](./TableListDataSources.md).

Read the [REST API v2 collection guide](./RestApiV2Collections.md) before adding a backend
collection. The experiment examples below are made up; RSpace has no experiments endpoint. See
[Table List interactions](./TableListInteractions.md) for reset controls, quick filters, and
selection.

## Design summary

Every remote table, REST API v2 or not, loads its rows through a
[`CollectionFetcher`](../../src/main/webapp/ui/src/modules/common/table-list/types.ts).
[`useTableList`](../../src/main/webapp/ui/src/modules/common/table-list/hooks/state.ts) keeps the
table and query state and returns `tableProps` plus functions that change that state.

The OpenAPI reader validates the current generated contract with Valibot. Selector and runtime
flags must be booleans, and query/catalog limits must be positive integers; `maximumProjections`
may be zero. Runtime descriptors must include their catalog limits and `via` value.
Missing values are errors rather than client-side defaults. The list response must reference
a local Read component through `docs.items.$ref`.

Absent relationship/runtime extensions mean no such capabilities. Optional titles, picker metadata
and formats remain optional. Selector schemas support declared JSON types, nullable type unions
and nonempty scalar `oneOf` variants. Mixed primitive variants use a text input. Other
composition/reference forms are rejected in selectors. Unrelated OpenAPI properties and
extensions do not need table-specific validation.

## REST API v2 setup

Use this procedure for most new tables.

### 1. Define the document schema

Sparse REST API v2 responses omit unselected fields. Make these fields optional in the schema.

Keep the ID and title fields required when every table request selects them.

```ts
import * as v from "valibot";

export const ExperimentSchema = v.object({
  id: v.string(),
  title: v.string(),
  owner: v.optional(v.string()),
  ownerId: v.optional(v.number()),
  enabled: v.optional(v.boolean()),
  modifiedAt: v.optional(v.string()),
});

export type Experiment = v.InferOutput<typeof ExperimentSchema>;
```

### 2. Define one collection configuration

A `CollectionConfig` describes the rows on the frontend. Each field entry holds both the field's API
type and how the table shows it.

```tsx
import type { CollectionConfig } from
  "@/modules/common/collection/config";

export const experimentConfig = {
  slug: "experiments",
  idField: "id",
  useAsTitle: "title",
  defaultColumns: ["title", "owner", "enabled", "modifiedAt"],
  listSearchableFields: ["title", "owner"],
  pagination: {
    defaultLimit: 25,
    limits: [10, 25, 50, 100],
  },
  labels: {
    singularKey: "experiments.labels.singular",
    pluralKey: "experiments.labels.plural",
    descriptionKey: "experiments.labels.description",
  },
  fields: [
    {
      name: "id",
      type: "text",
      labelKey: "experiments.fields.id",
      readOnly: true,
      list: false,
    },
    {
      name: "title",
      type: "text",
      labelKey: "experiments.fields.title",
      maximumLength: 255,
    },
    {
      name: "owner",
      type: "text",
      labelKey: "experiments.fields.owner",
    },
    {
      name: "ownerId",
      type: "number",
      labelKey: "experiments.fields.ownerId",
      readOnly: true,
      list: false,
    },
    {
      name: "enabled",
      type: "boolean",
      labelKey: "experiments.fields.enabled",
    },
    {
      name: "modifiedAt",
      type: "dateTime",
      labelKey: "experiments.fields.modifiedAt",
      readOnly: true,
    },
  ],
} satisfies CollectionConfig<Experiment>;
```

The adapter replaces field capabilities with the generated API rules. The table cannot offer an unsupported sort or filter.

### Fields of a relationship target

A collection config declares a relationship once. If an experiment schema uses an `owner`
relationship instead of the scalar owner in the example above, the adapter derives one optional
field for each `<relationship>.<field>` selector in `x-rspace-filter`, such as `owner.name`,
labelled "Owner → Name". You need not declare each target field separately. The adapter also
exposes published relationship fields when their owner is absent from the collection config, such
as `createdBy.username` and `updatedBy.username`. These fields remain filter-only and serialize the
published selector without adding response fields.

`createApiV2CollectionAdapter` makes each derived field an optional hidden column and sets
`form: false`. A published field with operators is also a filter field. A field without operators
is only a column. For example, a global ID derived from an ID has no operators and does not appear
in the filter panel. The adapter excludes the relationship wire fields `value` and `relationTo`.

Derived field values come from the expanded relationship. When a derived field is visible, the
adapter requests `depth=1`. The page's document schema need not declare derived fields.
Valibot drops undeclared keys, so the adapter restores the other target fields after parsing. The
renderer reads each value through its owner relationship. Sparse field requests select the owner
relationship instead of the dotted selector.

The field label combines the translated relationship label and the target field label, such as
"Owner → Name". Built-in target fields use the shared `common:tableList.targetFields.<field>`
catalog label ahead of the server title, so "Bookable item → Instrument name" becomes
"Bookable item → Name". Unknown target fields use the server title, then the field name.
Explicit labels for configured structured child fields are retained. An owner without a configured
field uses `common:tableList.fields.<owner>`, such as "Created by", then the published
`viaTitle`, then the owner name. A metadata-derived identity field takes its
label from the same `common:tableList.fields.<selector>` key.

These keys include their `common:` namespace, so translators bound to another namespace, such as
`useTranslation("inventory")`, still resolve them. Pass a translator that forwards interpolation
values: `(key, values) => String(t(key as never, values as never))`.

### Search fields

Use `listSearchableFields` as an explicit search allowlist. A selector can name a direct field or
one field of a relationship target.

```ts
listSearchableFields: ["title", "owner.name"]
```

The REST API v2 adapter requires each selector in `x-rspace-filter.selectors`. Each selector must
support `=contains=` and describe text. Only one relationship hop is supported.

The Search records input combines selectors with OR, then combines that group and an
advanced filter with AND. Remote tables commit search input after 300 ms. Enter and Clear commit
immediately. Client-side tables search immediately.

Do not add every published selector to the allowlist. Each added selector creates another pattern
comparison. The adapter rejects an allowlist above the API comparison limits.

You can split large configuration parts into files. Keep the final exported value as one `CollectionConfig`.

```ts
export const experimentFields = [
  // Field entries.
] satisfies CollectionConfig<Experiment>["fields"];

export const experimentConfig = {
  // Collection settings.
  fields: experimentFields,
} satisfies CollectionConfig<Experiment>;
```

### 3. Add custom renderers when required

Keep a custom renderer on the field that owns it. List each extra sparse field in `dependencies`.

```tsx
{
  name: "owner",
  type: "text",
  labelKey: "experiments.fields.owner",
  list: {
    dependencies: ["ownerId"],
    renderCell: ({ row }) => (
      <OwnerLink id={row.ownerId}>{row.owner}</OwnerLink>
    ),
  },
}
```

The REST API v2 adapter includes `ownerId` in the sparse field request. The column can remain
hidden.

Move renderers to a separate UI file when the configuration becomes difficult to scan.

### 4. Use the REST API v2 hook

Get the REST API v2 token with the existing authentication hook and pass it to
`useApiV2TableList`.

```tsx
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { TableList } from "@/modules/common/table-list/TableList";
import { useApiV2TableList } from
  "@/modules/common/table-list/hooks/apiV2TableList";

export function ExperimentTable() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const table = useApiV2TableList({
    resourceName: "experiments",
    config: experimentConfig,
    documentSchema: ExperimentSchema,
    request: { token },
    query: { keepPreviousData: true },
  });

  return <TableList {...table.tableProps} />;
}
```

The cache separates collection endpoints and literal bearer tokens. When authorization comes from
headers or a token getter, pass `request.authScope` as a stable user or tenant identity and change it
when that identity changes. `keepPreviousData` retains rows only within the same endpoint and
authorization scope. Request validation runs inside the query so an invalid filter leaves the table's
error state and reset controls available.

The hook loads `/api/v2/openapi.json` through the shared query cache and reads the resource fields,
filters, sort rules, and limits. It creates REST API v2 query parameters, sends the collection request
with the token, validates the REST API v2 list envelope, and returns normalized rows and a row count.

OpenAPI metadata stays fresh for the application session. A deployment starts a new browser session with new assets and metadata.

By default, a REST API v2 table requests its visible fields. Use a fixed projection for a small
collection with lightweight fields. This can avoid refetching after each column selection.

```tsx
request: {
  token,
  projection: { fixed: ["id", "title", "owner", "modifiedAt"] },
},
```

The field names are checked against the document type. The adapter still adds the configured ID,
title, and renderer dependency fields. With a fixed projection, changing column visibility or order is
a local presentation change. Filtering, sorting, and pagination still create new server requests.

### 5. Change common query behavior

Pass `query: { staleTime: 30_000, gcTime: 5 * 60_000, retry: 2, refetchInterval: false,
keepPreviousData: true }` in the hook options.

The hook keys queries by the effective API request parameters. Filter, sort, or page changes create
the corresponding cache entry. Presentation changes reuse the current query when they do not change the
effective field projection.

The hook passes an `AbortSignal` to the fetcher. TanStack Query can cancel an obsolete request.

## Related guides

See [Table List interactions](./TableListInteractions.md) for shared interactions and filters,
and [Table List data sources](./TableListDataSources.md) for rows already in the browser and other
endpoints.
