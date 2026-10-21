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
[`CollectionFetcher`](../../src/main/webapp/ui/src/modules/common/table-list/tableListState.ts).
[`useTableList`](../../src/main/webapp/ui/src/modules/common/table-list/useTableList.ts) keeps the
table and query state and returns `tableProps` plus functions that change that state.

The OpenAPI reader validates the current generated contract with Valibot. Selector and runtime
flags must be booleans, and query/catalog limits must be positive integers; `maximumProjections`
may be zero. Runtime descriptors must include their catalog limits and `via`/`viaResource` values.
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
  "@/modules/common/collection/collectionConfig";

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

### Declared object arrays

Use `type: "array"` with a non-empty `fields` list of named child fields. Child fields reuse
scalar metadata such as `required`, `nullable`, `readOnly`, `defaultValue` and
`writeConstraints`. Embedded `type: "object"` and array children declare their own fields,
so custom editors receive the complete recursive definition. Configuration resolution
rejects duplicate names, cycles, missing children and embedded relationships.

Arrays and embedded objects cannot be filtered or sorted. Set `form: false` for a dedicated
editor outside `RenderFields`, or provide `form.component` for a custom editor that receives
`form`, `disabled`, `fieldConfig` and the control's `id`, `describedBy` and `invalid` state.
The shared field wrapper handles labels, errors and visibility conditions; read-only fields
disable their custom editor. Embedded children use their parent editor and cannot
declare query capabilities. There is no generic row editor. Relationship fields
retain their existing `hasMany` behavior; scalar multiplicity remains deferred.

The API v2 metadata reader follows the list response's `docs.items` reference to its Read
component. Each declared array or object property publishes recursive backend `FieldSchema[]`
directly in `x-rspace-fields`. The adapter uses these children for the configured field,
including child types, titles, required/nullable/read-only flags, defaults and write constraints.
The extension remains on the property when `items` uses a named reference, as `roleSources`
does. The reader does not derive children from that reference or from a resource's name.
Configured array/object fields must have this extension when using generated metadata;
handwritten metadata can retain the configured child definitions. Invalid or empty recursive
definitions fail before rendering. Array row bounds come from `minItems` and `maxItems`.

### Fields of a relationship target

A collection config declares a relationship once. If an experiment schema uses an `owner`
relationship instead of the scalar owner in the example above, the adapter derives one optional
field for each target field in `x-rspace-relationship-fields`, such as `owner.name`, labelled
"Owner → Name". You need not declare each target field separately. The adapter also
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

The field label combines the translated relationship label and the target field title, such as
"Owner → Name". When the server publishes no title, the adapter uses the shared
`common:tableList.targetFields.<field>` label, such as "Created by → First name", and falls back to
the field name only when the catalog does not name it. An owner without a configured
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
  "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";

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

## Reuse collection fields in forms

Use `RenderFields` when a create or edit page uses the same collection field configuration. Pass
the resolved fields and a Formisch form store.

```tsx
const fields = resolveCollectionConfig(experimentConfig).fields;
const form = useForm({ schema: ExperimentInputSchema, initialInput });

<Form of={form} onSubmit={saveExperiment}>
  <RenderFields fields={fields} form={form} />
</Form>
```

Set `form: false` for a field that the form must omit. A field form configuration can set its
description, widget, width, or display condition. `RenderFields` also supports row and section
layout entries.

A relationship field uses `relationshipSources` when its target has a registered remote source.
Otherwise, pass static choices through `relationshipOptions`. Pass
`relationshipOptionAvailability` when another record can make an option unavailable.

With `hasMany`, both static and remote relationship fields keep an array of IDs in the form.
The remote picker converts that array to and from its global-ID selection string.

Custom controls must forward change events and the Formisch field
[focus and blur props](https://formisch.dev/react/api/useField/)
so touched state and the configured validation mode work.

The [form renderer](../../src/main/webapp/ui/src/modules/common/collection-form/RenderFields.tsx)
and [form types](../../src/main/webapp/ui/src/modules/common/collection-form/RenderFields.types.ts)
define the supported layout and control contracts.

## Related guides

See [Table List interactions](./TableListInteractions.md) for shared interactions and filters,
and [Table List data sources](./TableListDataSources.md) for rows already in the browser and other
endpoints.
