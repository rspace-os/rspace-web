# Table List data sources

`TableList` does not need a backend collection. `useTableList` takes either rows that are already
in the browser or a fetcher for any endpoint. The REST API v2 hook in
[Connect an endpoint to Table List](TableListAdapters.md) is one optional way to build that fetcher.

Every table still needs a `CollectionConfig`. Despite its name, it only describes the rows on the
frontend: their fields, labels, columns, ID and what each field supports.

## Rows already in the browser

Use a client data source when the browser already has every row.

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

TanStack Table filters, sorts, and pages these rows in the browser. It only sees the rows you
pass, so pass all of them.

## Any other endpoint

Write a fetcher when the rows come from an endpoint that is not a REST API v2 collection. The
fetcher turns the table's search, filters, sorting and page into the endpoint's parameters and
returns `{ rows, rowCount }`. The endpoint does not need the REST API v2 response format or RSQL.

### 1. Resolve the configuration

Write the configuration as described in
[Define one collection configuration](TableListAdapters.md#2-define-one-collection-configuration).
Without the REST API v2 hook, resolve it yourself:

```ts
import { resolveCollectionConfig } from
  "@/modules/common/collection/resolveCollectionConfig";

const config = resolveCollectionConfig(customExperimentConfig);
```

### 2. Write the fetcher

Keep parameter mapping and response validation in the endpoint module.

```ts
import type { CollectionFetcher } from
  "@/modules/common/table-list/state/tableListState";

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

### 3. Connect the fetcher

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

Keep each mapping next to its endpoint.

### Reuse REST API v2 fields with another endpoint

`useApiV2RuntimeFields` loads static resource metadata and hydrates the runtime selectors used by
the current route. Pass the effective subject ID as `request.authScope` with the OAuth token;
metadata is shared, while selected definitions remain scoped to the caller, resource, and catalogue.
Token callbacks require an explicit `authScope`.

Use `enrichApiV2FilterConfig({ config, metadata, runtimeFields, localFields })` when an endpoint
returns its own DTO. It derives relationship and runtime filters but leaves derived fields out of
column selection. `localFields` names filters resolved by the page; other operators come from API
metadata. Keep DTO parsing, pagination, and query composition in the endpoint's fetcher.

A collection's `relationshipSources`, keyed by `relationTo`, override default picker sources. Keep
search and saved-ID resolution in the source, and preserve an unavailable saved ID in the filter.

## Tests

For a REST API v2 table, test the OpenAPI metadata reader with a small document fixture, table state
conversion to REST API v2 parameters, and response validation with the REST API v2 envelope. Test
the HTTP fetcher with MSW and important custom renderers through the visible table.

Test any other fetcher with MSW. Assert its parameters, credentials, cancellation signal, and normalized result.

Use semantic queries in component tests. Do not test private adapter functions.

## Common errors

| # | Symptom | Guidance |
| --- | --- | --- |
| 1. | Unsupported control | Check the generated OpenAPI metadata and backend resource schema. |
| 2. | Missing cell data | Check [renderer dependencies](TableListAdapters.md#3-add-custom-renderers-when-required) and the sparse-response schema. |
| 3. | Only one server page is processed | See [Rows already in the browser](#rows-already-in-the-browser). |
| 4. | A page component cannot change state | See [Access and update table state](TableListInteractions.md#access-and-update-table-state). |
| 5. | Tables overwrite URL parameters | See [Browser query and storage defaults](TableListInteractions.md#browser-query-and-storage-defaults). |

### The request uses the wrong page

Store a zero-based `pageIndex`. The REST API v2 adapter converts it to a one-based page.

## Source files

| # | Guidance |
| --- | --- |
| 1. | [Collection configuration](../../src/main/webapp/ui/src/modules/common/collection/collectionConfig.ts) |
| 2. | [Table state and fetch interface](../../src/main/webapp/ui/src/modules/common/table-list/state/tableListState.ts) |
| 3. | [Shared table hook](../../src/main/webapp/ui/src/modules/common/table-list/state/useTableList.ts) |
| 4. | [RSQL query codec](../../src/main/webapp/ui/src/modules/common/table-list/rsql/rsqlCodec.ts) |
| 5. | [Persisted view codec](../../src/main/webapp/ui/src/modules/common/table-list/state/tableViewState.ts) |
| 6. | [Browser view storage](../../src/main/webapp/ui/src/modules/common/table-list/state/tableViewStorage.ts) |
| 7. | [REST API v2 metadata reader](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/apiV2CollectionMetadata.ts) |
| 8. | [REST API v2 adapter](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/createApiV2CollectionAdapter.ts) |
| 9. | [REST API v2 fetcher](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/createApiV2CollectionFetcher.ts) |
| 10. | [REST API v2 hook](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/useApiV2TableList.ts) |
| 11. | [RSQL serialization](../../src/main/webapp/ui/src/modules/common/table-list/adapters/apiV2/serializeRsql.ts) |
| 12. | [REST API v2 Storybook example](../../src/main/webapp/ui/src/modules/common/table-list/TableListApiV2.stories.tsx) |
