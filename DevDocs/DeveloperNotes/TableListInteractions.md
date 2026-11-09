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
| 2. | Dismissing a focused alert focuses the next alert, or the table's Filters control (else its search) when none is left. A programmatic `dismiss` leaves focus where it is. |
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

See [`UseTableListResult`](../../src/main/webapp/ui/src/modules/common/table-list/hooks/useTableList.ts)
for the hook's state, update functions and table props.

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

For an external first-page control, call
`table.setPage({ ...table.state.page, pageIndex: 0 })`. For a saved column preset, call
`table.setVisibleFields(["title", "owner"])`.

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

## Reset and quick filters

Pass `onReset` to `TableList` when a page owns table filters, pagination, and extra controls
in one route search state. That callback replaces the default per-feature resets,
including `filterButtons.onReset`; it must reset the complete view in one update.
Independent state owners can omit the callback and keep the built-in reset behavior.

Quick filters (`filterButtons.buttons`) render as toolbar toggles by default. Set
`filterButtons.presentation: "menu"` to move them into the Filters button's popover as switches,
next to an "Edit filters" action that opens the filter panel; the Filters badge then counts
pressed quick filters as well as panel filters. The page must then show which quick filters are on,
for example as removable chips, because the toolbar no longer does. Give
each button a `description`; the popover shows it under the switch. Without a filter panel
(`filtering: false` or `hideFilterPanel`), quick filters stay as toolbar toggles.

## Browser query and storage defaults

`useTableList` and `useApiV2TableList` keep the view in component state: they never touch the URL or
browser storage and need no Nuqs adapter. Use them for embedded, temporary or sensitive views.

`usePersistedTableList` and `usePersistedApiV2TableList` write search text, RSQL filters, visible
fields, and ordered sorting to the query string, so users can share the configured view. They take a
table descriptor and require a Nuqs adapter (`AppShell` provides one).

```tsx
// One constant per table, shared by the hook and the route.
export const experimentsTable = tableViewDescriptor(experimentsConfig, "issue", {
  parameterPrefix: "active-experiments",
  tableId: "experiments-active-view",
});

const table = usePersistedApiV2TableList({ persistence: experimentsTable /* , ... */ });
```

The default parameter prefix and `tableId` are the collection slug.

```text
?experiments.q=Ada
&experiments.where=enabled==true
&experiments.columns=...
&experiments.sort=title,-modifiedAt
```

`q` contains search text; `where` contains the RSQL filter expression.
`sort` lists sort priority. A leading minus sign selects descending order.

The hook parses the `where` parameter into `FilterExpression` state. REST API v2 adapters and
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

The route stores the view in `rspace.tableList.<tableId>.view`. Spread
`tableViewRouteOptions([experimentsTable])` into the route that renders the table; list every
persisted table on the page. The browser profile owns this storage. Pagination and column widths are
not stored. A persisted table on a route without the helper keeps its view in the URL only.

| # | Guidance |
| --- | --- |
| 1. | Entering the route (first load, reload, or arrival from another route) with none of a table's parameters in the URL restores its stored view in one replacing redirect. Unrelated parameters, the fragment and history state are kept. Edits, Back and Forward within the route, and hover preloads never restore, so returning to an empty entry shows the defaults. |
| 2. | Every navigation within the route saves the table's URL view, so edits need no storage write of their own. A URL without the table's parameters removes the stored view. |
| 3. | The route checks syntax only. A syntactically valid URL naming an unknown field or sort is saved and restored until the next edit replaces it. A URL filter that is not RSQL removes the stored view. |
| 4. | A stored view that does not parse (or whose filter is not RSQL) is never restored. Descriptors made with `"discard"` (client-row tables) remove it and show the defaults; `"issue"` (REST API v2 tables) keep it and show `RestoredViewIssue` until the user resets it or opens a URL view. |
| 5. | A stored column selection that is not a column list restores the default columns and keeps the other fields. |

A customised column selection records the configured `defaultColumns` at save time.
When a later release adds a default column, restoring the view inserts it after the preceding default
instead of hiding it behind the old selection. A default the user removed after seeing it stays
hidden. Views saved before this record existed gain every missing default once.

Several tables on one page need distinct `parameterPrefix` and `tableId` options. A page may not
assume it renders one table: the control panel IDs are unique per table, and under `AppShell`, a
persisted table that shares either value with another mounted table logs a `console.error` naming
both tables and the option to change.

The table list and the relationship picker own no React effects (Biome rejects `useEffect`,
`useLayoutEffect` and `useInsertionEffect` imports there): derive state during render, act in event
handlers or ref callbacks, or use a library hook. Debounce with TanStack Pacer.

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

The catalog picker requests one namespace after two characters, using `q` for the search term.
The REST API v2 transport attaches credentials, and React Query cancels obsolete requests. Catalog
searches and saved-field hydration use the catalog's generated query keys, so collections that
reuse a namespace do not reuse another catalog's definitions.

Free-text fields do not suggest stored values, including for `in` and `notIn`. Those
operators accept user-entered chips. A select field uses only its published
`options` values.

## Relationship filters

A `relationship` field filters on the target global ID. When the target has a registered source,
the filter panel shows a relationship picker. A user can search the target collection by name or
global ID and select a record.

API v2 identity selectors can publish an optional `picker` descriptor containing
`resource`, `identity: "globalId"`, and `globalIdPrefix`. The adapter carries this
into `filterPicker` on the resolved field. Metadata selects an application-owned
source; it never supplies a fetch URL. Without picker metadata, existing relationship
fields retain their `relationTo` lookup. An unregistered source retains typed input.

Register sources in `src/modules/common/relationship-picker/sources.tsx`,
or provide a collection's `relationshipSources` override keyed by resource name.

A source that answers an empty term with a useful first page sets `browsable: true`. The filter
value picker then lists choices on opening, without typing. Leave the flag unset for a source
that needs a term, such as a grantee directory.

See the [RelationshipSource type](../../src/main/webapp/ui/src/modules/common/relationship-picker/sources.tsx)
for source operations and their signatures. Keep option content noninteractive inside the listbox;
show global IDs as plain text so choosing an option cannot navigate away from an unsaved selection.
Unknown and inaccessible saved values must produce the same missing result.

The source's batch limit must not exceed its endpoint's page or argument limits.
Instrument restoration uses the collection's typed-ID `in` filter and sparse display
fields. Deduplicate canonical values before chunking. Query keys include the source,
caller scope, and selected values; never share cached options across callers.
Search results, selected-value labels, restores, and availability checks all include the
caller scope and query identity.

A saved predicate stays present while restoration loads, fails, or cannot find its
value. Restoring a label must never remove part of a filter. The `equals` and
`notEquals` operators select one value; `in` and `notIn` select several.

Runtime catalog routes are checked against the application allowlist both when
metadata is parsed and immediately before fetch. The current approved routes are
`/api/v2/instruments/fields/customFields` and
`/api/v2/instruments/fields/extraFields`. The custom-field picker searches every catalog of one
resource in one request to `/api/v2/<resource>/fields`, so a failure in one namespace fails the
whole search. New providers require an explicit allowlist entry. Queries are constructed
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

Browser tests use the local hooks to avoid retaining a filter between mounts and scope locators
with `page.elementLocator(container)` when a story renders multiple copies.

## Restore saved views without broadening requests

Spread the complete `tableProps` onto `TableList`. A URL view that is malformed, waits for runtime
definitions or names missing fields is blocked: the table shows its defaults, sends no list or
totals request, and ignores edits until the view resolves or the user resets it. An edit the
request codec rejects (over a filter or projection limit) stays in the toolbar with the table's error
state and is not written; the next valid edit or any navigation replaces it.

Runtime definition loading blocks collection requests. A network failure offers Retry; malformed
or unavailable fields preserve the encoded view and offer an explicit reset. Navigation into an
unresolved rule cannot overwrite the saved view or issue an unfiltered request. Custom route-owned
pages must apply the same gating using `pending`, `error`, and `missing` from the runtime hook and
pass `restoredViewIssue` to the table. Pass `runtimeFields` (the definitions and the selection callback)
and `authScope`, so filter value searches use the caller's scope.

Custom remote sources can use `dataSource.enabled`, including a state predicate, to defer a query
until their route state is ready. `dataScope` prevents `keepPreviousData` from displaying the previous
caller's rows when the effective subject changes.

See [Browser query and storage defaults](#browser-query-and-storage-defaults) for saved Boolean
groups and their removal rules.

Quick filters can act as page-owned query presets using `hasPreset`, `togglePreset`
and `withoutPresets` from `table-list/presets`. Bind each control to its expression;
only flattened top-level AND conjuncts participate. An OR subtree is never edited by
preset toggles, including exclusive swaps. Recovery when loading stale or invalid
current-user rules may remove comparisons within OR groups and must show a notice.

Set a quick-filter button's `inExpression` flag only when its newly introduced rule
belongs to this TableList's counted expression. The rule count subtracts each pressed
flagged button, preserving the existing Filters badge in both menu and button
presentations. Rules in another expression, and presets already counted as rules,
remain unflagged. This flag affects counts only and does not change the expression.

The Booking Calendar projects its item-owner preset into its existing event filter
panel and routes edits to that rule back to the item expression. The projection is
counted by the outer TableList, so its owner button uses `inExpression` too. Other
item rules retain their separate scope. This keeps one filter panel and preserves
the toolbar badge without moving ownership into the event URL parameter.

## Add sorting rules

The Sort priority panel offers Add sort for every unused sortable field, including fields
without a visible column. Selecting a field appends an ascending rule. Column headers can also
change the sorting. The button is disabled once every sortable field is in use.
