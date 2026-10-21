# Table List interactions

## Host row interactions once

Use `rowActions` when a row control opens a dialog, drawer, form, or other stateful interaction.
The table stores only the active action and row IDs, mounting the interaction once outside the
rows. Links and immediate controls can remain inside `renderCell`. Call `activate` only for a
list-level interaction.

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

## Report action results with alerts

Any control inside a `TableList` (row actions, selection actions, toolbar controls, and
`renderInteraction`) can use `useTableListAlerts()` to show a one-line result alert above the table.
`push` accepts several alerts at once; pushing an existing `id` replaces that alert, and the stack shows the newest first. Each alert has a Dismiss control and an optional
Undo. Use `actions` for links or buttons that apply to the result. The controls wrap with the alert
on narrow screens.

```tsx
const alerts = useTableListAlerts();

alerts.push({
  id: `record-archived-${row.id}`,
  tone: "warning",
  icon: <ArchiveIcon aria-hidden="true" />,
  message: t("records.archived.alert", { title: row.title }),
  undo: {
    run: () => restoreRecord(row.id, token).then(invalidateRecords),
    focusRowId: String(row.id),
    describeError: (error) => t("records.archived.undoFailed", { reason: errorMessage(error) }),
  },
});
```

The table owns the state, and `push` is stable. An action can therefore push from a callback that
outlives its row (a dialog whose confirmation removes the row). A polite live region announces
new alerts. Focus rules:

| # | Guidance |
| --- | --- |
| 1. | A pushed alert takes focus only after the acting control disappears, for example when a refetch removes its row. A persistent button, such as a bulk action, keeps focus. |
| 2. | Dismiss focuses the next alert, or the table's Filters control (else its search) when none is left. |
| 3. | Undo disables the alert's buttons while it runs. On success the alert goes and focus moves to the first control of `focusRowId`'s row once that row is shown again (after about five seconds, to Filters). On failure the alert stays, shows `describeError`'s text (or a generic message) without Undo, and takes focus. |

Row-action cells carry `data-table-list-row-actions` with the row ID for this lookup. `getRowId`
from `useTableList` is stable, and TableList reads a caller's `getRowId` through a ref: a new
identity would rebuild the columns and re-mount every row's cells, closing open menus and dialogs.

An owner callback outside the `TableList` context can use its imperative alert ref:

```tsx
const alertsRef = useRef<TableListAlertsApi>(null);

const mutation = useMutation({
  onSuccess: (result) => alertsRef.current?.push(resultAlert(result)),
});

return <TableList {...table.tableProps} alertsRef={alertsRef} />;
```

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

Remote tables (`clientSide={false}`) show an offline notice using React Query's online state.
Cached rows remain visible; paused queries resume automatically when the connection returns.
Do not add per-page browser connectivity listeners or force retries while offline.

The hook returns the complete table state and focused update functions.

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

`setFilters` and `setSorting` return to page zero to prevent an empty later page after a query change.

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

Move the hook to the nearest common owner when distant siblings need the same state.

## Browser query and storage defaults

`TableList` writes search text, RSQL filters, visible fields, and ordered sorting to the query string
by default. Users can share the configured view.

The default parameter prefix is the collection slug.

```text
?experiments.q=Ada
&experiments.where=enabled==true
&experiments.columns=...
&experiments.sort=title,-modifiedAt
```

`q` contains search text; `where` contains the RSQL filter expression.
`sort` lists sort priority. A leading minus sign selects descending order.

`TableList` parses the `where` parameter into `FilterExpression` state. REST API v2 adapters and
custom fetch functions receive the same state.

Flat AND or OR chains do not consume the codec's nesting allowance. The codec rejects expressions
with more than 20 nested logical groups and returns no selectors for rejected expressions.
The row editor edits direct AND comparisons. Restored nested AND/OR groups remain visible as saved
groups and survive Apply alongside edited comparisons. Remove a saved group explicitly, or use
Clear all to discard the entire expression. The row editor cannot edit inside a nested group;
callers must not flatten or discard it when opening the panel.

Date-time controls display local calendar time and apply an
instant, preserving seconds and milliseconds. Client date-time comparisons use the same instant
before and after a saved view restores Date operands.

`TableList` can read the old `filters` parameter. It writes `q` and `where` after the next filter change.

The table also stores filters, sorting, and visible fields in
`rspace.tableList.<tableId>.view`. The browser profile owns this storage. Pagination and column
widths are not stored.

A customised column selection records the configured `defaultColumns` at save time.
When a later release adds a default column, loading the view inserts it after the preceding default
instead of hiding it behind the old selection. A default the user removed after seeing it stays
hidden. Views saved before this record existed gain every missing default once.

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

The Filters, Sorting, and Columns panels use drag handles. The handles support mouse, touch,
and keyboard input. Filters keep a draft order until the user applies them. Sorting changes immediately.

The Columns panel separates shown and hidden fields. A user can reorder shown fields or move a
field between sections. Hidden-field order is not stored because it does not affect the table.

Each data-column header has a resize handle. A double-click restores the configured width. Widths
remain local to the mounted table and are not written to the URL or browser storage.

`variant="card"` adds a bordered container around the table. It does not turn each record into a
card. `variant="transparent"` removes that container treatment.

## Runtime fields

The REST API v2 adapter reads runtime-field namespaces from `x-rspace-runtime-fields`. Each namespace
states whether its fields support filters, columns, or both.

The Filters panel offers only namespaces with `filterable: true`. The Columns panel offers only
namespaces with `columnSelectable: true`. Both panels search the published catalog URL. A selected
definition becomes a normal resolved field in the table configuration.

The catalog picker requests one namespace after two characters.
It sends the REST API v2 bearer token and uses React Query to cancel obsolete requests. Catalog
searches and saved-field hydration include catalog URLs and authorization identity in their cache
keys, so collections that reuse a namespace do not reuse another catalog's definitions.

Free-text fields do not suggest stored values, including for `in` and `notIn`. Those
operators accept user-entered chips. A select field uses only its published
`options` values.

Filter rows use one parent grid and CSS subgrid tracks. All rows therefore share the field, operator,
value, optional-column, and remove-control widths.

## Relationship filters

A `relationship` field filters on the target global ID. When the target has a registered source,
the filter panel shows a relationship picker. A user can search the target collection by name or
global ID and select a record.

API v2 identity selectors can publish an optional `picker` descriptor containing
`resource`, `identity: "globalId"`, and `globalIdPrefix`. The adapter carries this
into `filterPicker` on the resolved field. Metadata selects an application-owned
source; it never supplies a fetch URL. Without picker metadata, existing relationship
fields retain their `relationTo` lookup. An unregistered source retains typed input.

Register sources in `src/modules/common/relationship-picker/relationshipSources.tsx`,
or provide a collection's `relationshipSources` override keyed by resource name.

See the [RelationshipSource type](../../src/main/webapp/ui/src/modules/common/relationship-picker/relationshipSources.tsx)
for source operations and their signatures. Keep option content noninteractive inside the listbox;
show global IDs as plain text so choosing an option cannot navigate away from an unsaved selection.
Unknown and inaccessible saved values must produce the same missing result.

The source's batch limit must not exceed its endpoint's page or argument limits.
Instrument restoration uses the collection's typed-ID `in` filter and sparse display
fields. Deduplicate canonical values before chunking. Query keys include the source,
caller scope, token, and selected values; never share cached options across callers.
Keep the older optional `resolve` for callers that have not migrated to batch restore.

A saved predicate stays present while restoration loads, fails, or cannot find its
value. Restoring a label must never remove part of a filter. The `equals` and
`notEquals` operators select one value; `in` and `notIn` select several.

Runtime catalog routes are checked against the application allowlist both when
metadata is parsed and immediately before fetch. The current approved routes are
`/api/v2/instruments/fields/customFields` and
`/api/v2/instruments/fields/extraFields`. New providers require an explicit allowlist entry. Queries are constructed
locally and authenticated catalog requests refuse redirects.

### Metadata-backed relationship filters

API v2 metadata can supply a relationship's scalar filters even when the relationship is omitted
from local config; declare it locally when the table needs a column or custom label. Relationship
identity uses a registered picker, while related scalar fields use their declared input types and do
not suggest record names. The picker groups target fields and custom-field options under the
relationship label; shared audit relationships use localized `Created by` and `Updated by` labels
unless the config overrides them, and their untitled user fields use the localized target field
labels described in [Fields of a relationship target](./TableListAdapters.md#fields-of-a-relationship-target). New rules
default to `equals` when supported, and restored rules keep their saved operator.

Use the operators published for each target field. For text fields, `equals` anchors the complete
value, including `*`, while `matches` finds words anywhere. Labels and placeholders must distinguish
them. See the [query guide](./RestApiV2QueriesAndAccess.md) for server-side restrictions.

Browser tests pass `queryString: false` to avoid retaining a filter between mounts and scope locators
with `page.elementLocator(container)` when a story renders multiple copies.

## Restore saved views without broadening requests

`useApiV2TableList` mounts its persistence reader through `tableProps.stateSync`. Spread the complete
`tableProps` onto `TableList`; the collection query waits for that reader when persistence is enabled.
`queryString: false` needs no Nuqs provider. Persisted tables require the normal Nuqs adapter.

Runtime definition loading blocks collection requests. A network failure offers Retry; malformed
or unavailable fields preserve the encoded view and offer an explicit reset. Navigation into an
unresolved rule cannot overwrite the saved view or issue an unfiltered request. Custom route-owned
pages must apply the same gating using `pending`, `error`, and `missing` from the runtime hook and
pass `restoredViewIssue` to the table. Pass `runtimeFieldAuthScope` with runtime definitions and the
selection callback so picker searches use the same caller scope.

Custom remote sources can use `dataSource.enabled`, including a state predicate, to defer a query
until their route state is ready. `dataScope` prevents `keepPreviousData` from displaying the previous
caller's rows when the effective subject changes.

See [Browser query and storage defaults](#browser-query-and-storage-defaults) for saved Boolean
groups and their removal rules.
