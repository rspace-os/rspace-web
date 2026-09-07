# Connect an endpoint to Table List

This guide explains how to connect collection data to `TableList`.

Use the REST API v2 hook for a REST API v2 collection. Use a custom fetcher for an endpoint with
different wire rules.

Read the [REST API v2 collection guide](./RestApiV2Collections.md) before you add a backend
collection.

## Design summary

The module has one endpoint interface:

```ts
export type CollectionFetcher<TDocument> = (
  state: CollectionQueryState<TDocument>,
  context: { signal: AbortSignal },
) => Promise<CollectionPage<TDocument>>;
```

A REST API v2 fetcher and a custom fetcher use this interface. The table does not know which
fetcher it uses.

The `useTableList` hook owns these values:

- Filters.
- Sort rules.
- Page values.
- Visible fields.
- Remote query state.

The hook returns `tableProps` for `TableList`. It also returns the state and update functions for other components.

```text
OpenAPI metadata ----> REST API v2 adapter ----> CollectionFetcher
                                                 |
Custom endpoint ----> custom CollectionFetcher --+
                                                 |
                                                 v
                                          useTableList
                                         /            \
                               state and updates    tableProps
                                      |                 |
                                      v                 v
                              page components       TableList
```

## REST API v2 setup

Use this procedure for most new tables.

### 1. Define the document schema

Sparse REST API v2 responses omit fields that the request does not select. Make these fields
optional in the schema.

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

Put wire field types and display rules in one field entry.

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

### Fields of a relationship target

A collection config declares a relationship once. The adapter derives one optional field for each
target field in `x-rspace-relationship-fields`. For example, it names `target.name` as
"Bookable item → Name". You do not have to declare each target field separately.

`createApiV2CollectionAdapter` makes each derived field an optional hidden column and sets
`form: false`. A published field with operators is also a filter field. A field without operators
is only a column. For example, a global ID derived from an ID has no operators and does not appear
in the filter panel. The adapter excludes the relationship wire fields `value` and `relationTo`.

Derived field values come from the expanded relationship. When a derived field is visible, the
adapter requests `depth=1`. The page's document schema does not have to declare derived fields.
Valibot drops undeclared keys, so the adapter restores the other target fields after parsing. The
renderer reads each value through its owner relationship. Sparse field requests select the owner
relationship instead of the dotted selector.

The field label combines the translated relationship label and the target field title, such as
"Bookable item → Name". The adapter uses the selector when the server publishes no title.

### Search fields

Use `listSearchableFields` as an explicit search allowlist. A selector can name a direct field or
one field of a relationship target.

```ts
listSearchableFields: ["title", "target.name"]
```

The REST API v2 adapter requires each selector in `x-rspace-filter.selectors`. Each selector must
support `=contains=` and describe text. Only one relationship hop is supported.

The Search records input combines the selectors with OR. It combines that search group and an
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

Get the REST API v2 token with the existing authentication hook. Pass the token to
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

The hook performs these actions:

1. It loads `/api/v2/openapi.json` through the shared query cache.
2. It reads the resource fields, filters, sort rules, and limits.
3. It creates the REST API v2 query parameters.
4. It sends the collection request with the token.
5. It validates the REST API v2 list envelope.
6. It returns normalized rows and a row count.

OpenAPI metadata stays fresh for the application session. A deployment starts a new browser session with new assets and metadata.

By default, a REST API v2 table requests its visible fields. Use a fixed projection for a small
collection with lightweight fields. This option can avoid refetching after each column selection.

```tsx
request: {
  token,
  projection: { fixed: ["id", "title", "owner", "updatedAt"] },
},
```

The field names are checked against the document type. The adapter still adds the configured ID,
title, and renderer dependency fields. With a fixed projection, changing column visibility or order is
a local presentation change. Filtering, sorting, and pagination still create new server requests.

### 5. Change common query behavior

Use the `query` object for common TanStack Query settings.

```tsx
const table = useApiV2TableList({
  resourceName: "experiments",
  config: experimentConfig,
  documentSchema: ExperimentSchema,
  request: { token },
  query: {
    staleTime: 30_000,
    gcTime: 5 * 60_000,
    retry: 2,
    refetchInterval: false,
    keepPreviousData: true,
  },
});
```

The hook keys queries by the effective API request parameters. A filter, sort, or page change creates
the correct cache entry. Presentation changes reuse the current query when they do not change the
effective field projection.

The hook passes an `AbortSignal` to the fetcher. TanStack Query can cancel an obsolete request.

## Host row interactions once

Use `rowActions` when a row control opens a dialog, drawer, form, or other stateful interaction.
The table stores only the active action and row IDs, so the interaction is mounted once outside the
rows. Links and immediate controls can remain inside `renderCell`. Call `activate` only when the
list-level interaction is required.

```tsx
const rowActions = {
  id: "actions",
  label: "Actions",
  renderCell: ({ row, activate }) => (
    <button type="button" onClick={() => activate("delete")}>
      Delete {row.title}
    </button>
  ),
  renderInteraction: ({ actionId, row, close }) =>
    actionId === "delete" ? <DeleteDialog row={row} onClose={close} /> : null,
} satisfies TableListRowActions<Experiment>;

<TableList {...table.tableProps} rowActions={rowActions} />;
```

Keep `uiColumns` for display-only custom columns. Do not mount a stateful dialog in every
`uiColumns.renderCell` call.

## Add controlled row selection

Pass `selection` when a page supplies bulk actions. The selected IDs stay in the page component,
so selection can continue across remote pages.

```tsx
<TableList
  {...table.tableProps}
  selection={{
    value: selectedRowIds,
    onChange: setSelectedRowIds,
    maximumCount: 1000,
    getRowLabel: (row) => row.title,
    renderActions: ({ selectedRowIds, clearSelection }) => (
      <BulkActions ids={selectedRowIds} onComplete={clearSelection} />
    ),
  }}
/>
```

`maximumCount` must be positive. The header checkbox changes only rows on the visible page.
Previously selected IDs on other pages remain selected. Set `disabled` while a bulk request is in
progress. The table keeps a selected row removable after the selection reaches its limit.

## Access and update table state

The table does not hide its state. The hook returns the complete state and focused update functions.

```ts
const {
  state,
  setState,
  setFilters,
  setSorting,
  setPage,
  setVisibleFields,
  tableProps,
  refetch,
} = useApiV2TableList(options);
```

Any component in the same React owner can read these values. Pass only the required value and function to a child.

```tsx
function ExperimentPage() {
  const table = useApiV2TableList(options);

  return (
    <>
      <SelectionSummary
        search={table.state.filters.search}
        clearSearch={() =>
          table.setFilters({
            ...table.state.filters,
            search: "",
          })
        }
      />
      <TableList {...table.tableProps} />
    </>
  );
}
```

`setFilters` and `setSorting` return the table to page zero. This behavior prevents an empty later page after a query change.

Use `setPage` to add an external page control.

```tsx
<button
  type="button"
  onClick={() => table.setPage({ ...table.state.page, pageIndex: 0 })}
>
  First page
</button>
```

Use `setVisibleFields` to implement a saved column preset.

```tsx
<button
  type="button"
  onClick={() => table.setVisibleFields(["title", "owner"])}
>
  Use compact columns
</button>
```

Use `setState` when one action must change several state parts atomically.

```tsx
function showEnabledExperiments() {
  table.setState((current) => ({
    ...current,
    filters: {
      search: "",
      expression: {
        kind: "comparison",
        field: "enabled",
        operator: "equals",
        value: true,
      },
    },
    page: { ...current.page, pageIndex: 0 },
  }));
}
```

Lift the hook to the nearest common owner when distant siblings need the same state.

## Browser query and storage defaults

`TableList` writes search text, RSQL filters, visible fields, and ordered sorting to the query string
by default. Users can share the configured view.

The default parameter prefix is the collection slug.

```text
?experiments.q=Ada
&experiments.where=enabled==true;timezone=in=(Europe/Berlin,America/New_York)
&experiments.columns=...
&experiments.sort=title,-modifiedAt
```

The `q` parameter contains the search text. The `where` parameter contains the RSQL filter expression.
The `sort` parameter lists sort priority. A leading minus sign selects descending order.

`TableList` parses the `where` parameter into `FilterExpression` state. REST API v2 adapters and
custom fetch functions receive the same state.

`TableList` can read the old `filters` parameter. It writes `q` and `where` after the next filter change.

The table also stores filters, sorting, and visible fields in
`rspace.tableList.<tableId>.view`. The browser profile owns this storage. Pagination and column
widths are not stored.

The collection slug is the default `tableId`. Set a stable, unique ID when independent views use
the same collection configuration.

```tsx
const table = useApiV2TableList({
  table: {
    queryString: {
      parameterPrefix: "active-experiments",
      tableId: "experiments-active-view",
    },
  },
});
```

An initial URL parameter overrides the complete stored view. Empty and invalid owned parameters
also override storage. The table then writes the parsed canonical state. It removes malformed or
unsupported storage. A stale stored member returns to its configured default without changing valid
members.

Set a unique prefix when one page contains multiple tables.

```tsx
const table = useApiV2TableList({
  table: {
    queryString: { parameterPrefix: "active-experiments" },
  },
});
```

Disable query updates for a temporary or sensitive view.

```tsx
const table = useApiV2TableList({
  table: { queryString: false },
});
```

This option also disables browser storage.

## Reorder and resize the view

The Filters, Sorting, and Columns panels use drag handles. The handles support a mouse, touch input,
and a keyboard. Filters keep a draft order until the user applies them. Sorting changes immediately.

The Columns panel separates shown and hidden fields. A user can reorder shown fields or move a
field between sections. Hidden-field order is not stored because it does not affect the table.

Each data-column header has a resize handle. A double-click restores the configured width. Widths
remain local to the mounted table and are not written to the URL or browser storage.

`variant="card"` adds a bordered container around the table. It does not render each record as a
card. `variant="transparent"` removes that container treatment.

## Runtime fields

The REST API v2 adapter reads runtime-field namespaces from `x-rspace-runtime-fields`. Each namespace
states whether its fields support filters, columns, or both.

The Filters panel offers only namespaces with `filterable: true`. The Columns panel offers only
namespaces with `columnSelectable: true`. Both panels search the published catalog URL. A selected
definition becomes a normal resolved field in the table configuration.

The catalog picker starts a request after two characters. It scopes each request to one namespace.
It sends the REST API v2 bearer token and cancels obsolete requests through React Query.

Free-text fields do not suggest stored values. This rule also applies to `in` and `notIn`. Those
operators accept values that the user enters as chips. A select field uses only its published
`options` values.

Filter rows use one parent grid and CSS subgrid tracks. All rows therefore share the field, operator,
value, optional-column, and remove-control widths.

## Relationship filters

A `relationship` field filters on the target global ID. When the target has a registered source,
the filter panel shows a relationship picker. A user can search the target collection by name or
global ID and select a record.

The picker finds its backend from the `relationTo` value on the field. Add one entry for each
pickable target in
`src/modules/common/relationship-picker/relationshipSources.tsx`:

```tsx
const instruments: RelationshipSource = {
  resourceName: "instruments",
  globalIdPrefix: "IN",
  fields: ["id", "name", "globalId"],
  searchField: "name",
  toOption: (document, context) => {
    const instrument = parseOrThrow(InstrumentSchema, document);
    return {
      value: instrument.globalId,
      label: instrument.name,
      content: (
        <InventoryItem
          name={instrument.name}
          globalId={instrument.globalId}
          idLinkLabel={context.idLinkLabel(instrument.globalId)}
          compact={context.compact}
          size="xs"
        />
      ),
    };
  },
};
```

Rules for one source:

- `resourceName` must be a REST API v2 collection that permits `LIST` and `READ`.
- Keep `fields` to the values that `toOption` uses. The request asks for these fields only.
- `searchField` must accept `=contains=`.
- `globalIdPrefix` lets the picker recognize a pasted global ID. The picker then searches by row
  ID because the derived global ID is not filterable.

The picker caches each search and selected record in React Query for minutes. Reopening a dropdown
or restoring a shared filter does not repeat those requests.

The `equals` and `notEquals` operators select one record. The `in` and `notIn` operators select
several.

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

## Complete client-side collections

Use an explicit client data source when the browser has every row.

```tsx
const table = useTableList({
  config,
  dataSource: {
    type: "client",
    rows: allExperiments,
  },
});

return <TableList {...table.tableProps} />;
```

TanStack Table filters, sorts, and pages these rows in the browser.

Give client mode the complete collection. The table can process only the supplied rows.

## Custom endpoint setup

Use a custom fetcher when an endpoint has different parameters or response data.

### 1. Resolve the collection configuration

```ts
import { resolveCollectionConfig } from
  "@/modules/common/collection/resolveCollectionConfig";

const config = resolveCollectionConfig<CustomExperiment>({
  slug: "custom-experiments",
  idField: "id",
  useAsTitle: "name",
  defaultColumns: ["name", "owner"],
  listSearchableFields: ["name"],
  pagination: { defaultLimit: 20, limits: [20, 50] },
  labels: {
    singularKey: "customExperiments.labels.singular",
    pluralKey: "customExperiments.labels.plural",
  },
  fields: [
    { name: "id", type: "number", labelKey: "customExperiments.fields.id" },
    { name: "name", type: "text", labelKey: "customExperiments.fields.name" },
    { name: "owner", type: "text", labelKey: "customExperiments.fields.owner" },
  ],
});
```

### 2. Implement the common fetch interface

Keep parameter mapping and response validation in the endpoint module.

```ts
import type { CollectionFetcher } from
  "@/modules/common/table-list/tableListState";

export const fetchCustomExperiments:
  CollectionFetcher<CustomExperiment> = async (state, { signal }) => {
    const parameters = new URLSearchParams({
      pageNumber: String(state.page.pageIndex),
      pageSize: String(state.page.pageSize),
      query: state.filters.search,
      orderBy: state.sorting
        .map((rule) => `${rule.field} ${rule.direction}`)
        .join(","),
    });
    const response = await fetch(`/custom/experiments?${parameters}`, {
      signal,
    });
    if (!response.ok) {
      throw new Error(`Experiment request failed with status ${response.status}`);
    }
    const input: unknown = await response.json();
    const result = parseOrThrow(CustomExperimentPageSchema, input);
    return {
      rows: result.items,
      rowCount: result.total,
    };
  };
```

### 3. Connect the custom fetcher

```tsx
const table = useTableList({
  config,
  dataSource: {
    type: "remote",
    queryKey: ["custom-experiments"],
    fetch: fetchCustomExperiments,
    staleTime: 30_000,
    retry: 1,
    keepPreviousData: true,
  },
});

return <TableList {...table.tableProps} />;
```

Keep each custom mapping next to its endpoint.

## Tests

Test a REST API v2 table at these seams:

1. Test the OpenAPI metadata reader with a small document fixture.
2. Test table state conversion to REST API v2 parameters.
3. Test response validation with the REST API v2 envelope.
4. Test the HTTP fetcher with MSW.
5. Test important custom renderers through the visible table.

Test a custom fetcher with MSW. Assert its parameters, credentials, cancellation signal, and normalized result.

Use semantic queries in component tests. Do not test private adapter functions.

## Common errors

### The table offers an unsupported control

Check the generated OpenAPI metadata. Check the backend resource schema when the metadata is incorrect.

### A custom cell has missing data

Add the missing field to the renderer `dependencies`. Confirm that the document schema accepts the sparse response.

### The request uses the wrong page

Store a zero-based `pageIndex`. The REST API v2 adapter converts it to a one-based page.

### A table processes only one server page

Replace the client source with a remote source. Client mode requires the complete collection.

### A page component cannot change table state

Keep the result of the hook in the common React owner. Pass the required update function to the component.

### Two tables overwrite the same URL parameters

Give each table a unique `parameterPrefix`.

## Source files

- [Collection configuration](../../src/main/webapp/ui/src/modules/common/collection/collectionConfig.ts)
- [Table state and fetch interface](../../src/main/webapp/ui/src/modules/common/table-list/tableListState.ts)
- [Shared table hook](../../src/main/webapp/ui/src/modules/common/table-list/useTableList.ts)
- [RSQL query codec](../../src/main/webapp/ui/src/modules/common/table-list/rsql/rsqlCodec.ts)
- [Persisted view codec](../../src/main/webapp/ui/src/modules/common/table-list/tableViewState.ts)
- [Browser view storage](../../src/main/webapp/ui/src/modules/common/table-list/tableViewStorage.ts)
- [Collection form renderer](../../src/main/webapp/ui/src/modules/common/collection-form/RenderFields.tsx)
- [Collection form types](../../src/main/webapp/ui/src/modules/common/collection-form/RenderFields.types.ts)
- [REST API v2 metadata reader](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata.ts)
- [REST API v2 adapter](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/createApiV2CollectionAdapter.ts)
- [REST API v2 fetcher](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/createApiV2CollectionFetcher.ts)
- [REST API v2 hook](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/useApiV2TableList.ts)
- [RSQL serialization](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/rsql/serializeRsql.ts)
- [REST API v2 Storybook example](../../src/main/webapp/ui/src/modules/common/table-list/TableListApiV2.stories.tsx)

## Configure and test relationship-target filters

For example, a booking-configuration table can filter by its instrument name. Declare only the
relationship in the collection config:

```ts
{ name: "target", labelKey: "...", type: "relationship", relationTo: "instruments", hasMany: false },
```

When the target has a registered relationship source, the value box suggests records from that
collection. The value remains free text because the server matches text. Without a registered
source, the field uses a plain input. An input bound to a datalist has the ARIA role `combobox`.
Tests must query `combobox`, not `textbox`.

The server permits the `equals`, `in`, `contains`, and `matches` operators on a target field. It
rejects all negative operators.

Keep both `equals` and `matches`. Equality accepts `*` and anchors the complete value. `matches`
finds words anywhere in the value. UI labels and placeholders must show this difference.

Follow these rules in browser tests of the filter panel:

- Pass `queryString: false`. Otherwise, nuqs retains one test's filter in the URL, and the next
  mount starts with that filter.
- Scope locators with `page.elementLocator(container)`. A page-wide query matches every mounted
  copy of a story.
