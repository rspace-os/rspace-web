import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/config";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { persistenceIdentity, type TableViewDescriptor } from "../persistence";
import type {
  CollectionPage,
  CollectionQueryState,
  FilterState,
  PageState,
  RemoteTableListDataSource,
  TableListDataSource,
  TableListProps,
  TableListStatus,
} from "../types";
import { parseRawTableView } from "../utils/viewStorage";
import { resolvedFromParsed, type TableListFeatureSelection, useTableViewState } from "./useTableViewState";
import { type TableViewStore, useLocalTableView, useUrlTableView } from "./useTableViewStore";

export type UseTableListOptions<TDocument extends Record<string, unknown>> = {
  config: ResolvedCollectionConfig<TDocument>;
  dataSource: TableListDataSource<TDocument>;
  initialState?: Partial<CollectionQueryState<TDocument>>;
  features?: TableListFeatureSelection;
  getRowId?: (row: TDocument) => string;
  reserveEmptyRows?: boolean;
};

export type UsePersistedTableListOptions<TDocument extends Record<string, unknown>> = UseTableListOptions<TDocument> & {
  /** The table's descriptor, also spread into its route with `tableViewRouteOptions`. */
  persistence: TableViewDescriptor<"discard">;
};

export type UseTableListResult<TDocument extends Record<string, unknown>> = {
  state: CollectionQueryState<TDocument>;
  setFilters: (filters: FilterState<TDocument>) => void;
  setSorting: (sorting: readonly SortRule<TDocument>[]) => void;
  setPage: (page: PageState) => void;
  setVisibleFields: (fields: readonly FieldName<TDocument>[]) => void;
  tableProps: TableListProps<TDocument>;
  refetch: () => Promise<unknown>;
};

export function initialQueryState<TDocument>(
  config: ResolvedCollectionConfig<TDocument>,
  initialState?: Partial<CollectionQueryState<TDocument>>,
): CollectionQueryState<TDocument> {
  return {
    filters: initialState?.filters ?? { search: "", expression: null },
    sorting: initialState?.sorting ?? config.defaultSort ?? [],
    page: initialState?.page ?? {
      pageIndex: 0,
      pageSize: config.pagination?.defaultLimit ?? 20,
    },
    visibleFields: initialState?.visibleFields ?? config.defaultColumns,
  };
}

function queryKeyFor<TDocument>(
  remote: RemoteTableListDataSource<TDocument> | null,
  state: CollectionQueryState<TDocument>,
  slug: string,
): readonly unknown[] {
  if (!remote) return ["table-list", slug, "client"];
  if (typeof remote.queryKey === "function") return remote.queryKey(state);
  return [...remote.queryKey, state];
}

function currentPage<TDocument>(
  dataSource: TableListDataSource<TDocument>,
  canFetch: boolean,
  data: CollectionPage<TDocument> | undefined,
): Pick<CollectionPage<TDocument>, "rows" | "rowCount"> {
  if (dataSource.type === "client") return { rows: dataSource.rows, rowCount: dataSource.rows.length };
  if (!canFetch) return { rows: [], rowCount: 0 };
  return { rows: data?.rows ?? [], rowCount: data?.rowCount };
}

function tableListStatus(
  remote: boolean,
  canFetch: boolean,
  query: { isError: boolean; isPending: boolean; isFetching: boolean },
): TableListStatus {
  if (remote && !canFetch) return "loading";
  if (query.isError) return "error";
  if (query.isPending && remote) return "loading";
  if (query.isFetching) return "refreshing";
  return "idle";
}

function staleFieldPolicy<TDocument>(config: ResolvedCollectionConfig<TDocument>) {
  const namespaces = config.runtimeNamespaces ?? [];
  if (namespaces.length === 0) return undefined;
  const known = new Set(config.fields.map((field) => String(field.name)));
  return {
    isStale: (name: string) =>
      !known.has(name) && namespaces.some((namespace) => isRuntimeFieldInNamespace(name, namespace)),
  };
}

/** Shared by every table-list entry point. */
export function useTableListQuery<TDocument extends Record<string, unknown>>(
  config: ResolvedCollectionConfig<TDocument>,
  state: CollectionQueryState<TDocument>,
  dataSource: TableListDataSource<TDocument>,
) {
  const remote = dataSource.type === "remote" ? dataSource : null;
  const canFetch =
    remote !== null && (typeof remote.enabled === "function" ? remote.enabled(state) : remote.enabled !== false);
  const queryKey = queryKeyFor(remote, state, config.slug);
  const dataScope = remote?.wire
    ? JSON.stringify([remote.dataScope ?? null, [...state.visibleFields].sort()])
    : remote?.dataScope;
  // Plain useQuery: Table List keeps previous rows and renders its own loading, empty and error states.
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => {
      if (!remote) throw new Error("A client data source does not fetch data");
      return remote.wire ? remote.wire.request(state, { signal }) : remote.fetch(state, { signal });
    },
    select: (body) => (remote?.wire ? remote.wire.select(body, state) : (body as CollectionPage<TDocument>)),
    enabled: canFetch,
    meta: remote ? { ...navigationProgressQueryMeta, ...(dataScope === undefined ? {} : { dataScope }) } : undefined,
    staleTime: remote?.staleTime,
    gcTime: remote?.gcTime,
    retry: remote?.retry,
    refetchInterval: remote?.refetchInterval,
    placeholderData: remote?.keepPreviousData
      ? (previous, previousQuery) =>
          previousQuery?.meta?.dataScope === dataScope ? keepPreviousData(previous) : undefined
      : undefined,
  });
  const { rows, rowCount } = currentPage(dataSource, canFetch, query.data);
  return {
    rows,
    rowCount,
    hasNextPage: query.data?.hasNextPage,
    status: tableListStatus(remote !== null, canFetch, query),
    error: query.error,
    refetch: canFetch ? async () => query.refetch() : async () => undefined,
  };
}

/** The setters and `TableList` props for a query state held by `useTableViewState`. */
export function tableListResult<TDocument extends Record<string, unknown>>(
  options: UseTableListOptions<TDocument>,
  view: ReturnType<typeof useTableViewState<TDocument>>,
  query: ReturnType<typeof useTableListQuery<TDocument>>,
  persistence?: TableViewDescriptor,
): UseTableListResult<TDocument> {
  const { config, dataSource, features: selectedFeatures, getRowId, reserveEmptyRows } = options;
  const { state, commit } = view;
  const setFilters = (filters: FilterState<TDocument>) => commit((current) => ({ ...current, filters }));
  const setSorting = (sorting: readonly SortRule<TDocument>[]) => commit((current) => ({ ...current, sorting }));
  const setPage = (page: PageState) => commit((current) => ({ ...current, page }));
  const setVisibleFields = (visibleFields: readonly FieldName<TDocument>[]) =>
    commit((current) => ({ ...current, visibleFields }));
  const enabled = (feature: keyof TableListFeatureSelection) => selectedFeatures?.[feature] !== false;
  const idField = config.idField;
  return {
    state,
    setFilters,
    setSorting,
    setPage,
    setVisibleFields,
    tableProps: {
      config,
      rows: query.rows,
      getRowId: getRowId ?? ((row: TDocument) => String(row[idField])),
      features: {
        filtering: enabled("filtering") ? { value: state.filters, onChange: setFilters } : false,
        sorting: enabled("sorting") ? { value: state.sorting, onChange: setSorting } : false,
        pagination: enabled("pagination")
          ? { value: state.page, rowCount: query.rowCount, hasNextPage: query.hasNextPage, onChange: setPage }
          : false,
        columns: enabled("columns") ? { value: state.visibleFields, onChange: setVisibleFields } : false,
      },
      clientSide: dataSource.type === "client",
      status: query.status,
      error: query.error,
      persistenceIdentity: persistence ? persistenceIdentity(persistence) : undefined,
      reserveEmptyRows,
    },
    refetch: query.refetch,
  };
}

function useClientTableList<TDocument extends Record<string, unknown>>(
  options: UseTableListOptions<TDocument>,
  store: TableViewStore,
  persistence?: TableViewDescriptor,
): UseTableListResult<TDocument> {
  const { config, initialState, features } = options;
  const [base] = useState(() => initialQueryState(config, initialState));
  const stale = staleFieldPolicy(config);
  const parsed = parseRawTableView(store.raw, config, stale);
  // A member that does not resolve against the configuration falls back to the base view.
  const view = useTableViewState({
    config,
    base,
    store,
    features,
    resolved: resolvedFromParsed(parsed),
  });
  const query = useTableListQuery(config, view.state, options.dataSource);
  return tableListResult(options, view, query, persistence);
}

/** A table whose view lives in component state only: it never touches the URL or browser storage. */
export function useTableList<TDocument extends Record<string, unknown>>(
  options: UseTableListOptions<TDocument>,
): UseTableListResult<TDocument> {
  return useClientTableList(options, useLocalTableView());
}

/**
 * A table whose view lives in its URL parameters, and in browser storage when its route spreads
 * `tableViewRouteOptions`. Requires a nuqs adapter.
 */
export function usePersistedTableList<TDocument extends Record<string, unknown>>(
  options: UsePersistedTableListOptions<TDocument>,
): UseTableListResult<TDocument> {
  return useClientTableList(options, useUrlTableView(options.persistence), options.persistence);
}
