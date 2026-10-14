import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { type Dispatch, type SetStateAction, useCallback, useState } from "react";
import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/collectionConfig";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";
import type {
  CollectionQueryState,
  FilterState,
  PageState,
  TableListDataSource,
  TableListFeatures,
  TableListProps,
} from "./tableListState";

type FeatureSelection = {
  filtering?: boolean;
  sorting?: boolean;
  pagination?: boolean;
  columns?: boolean;
};

export type UseTableListOptions<TDocument extends Record<string, unknown>> = {
  config: ResolvedCollectionConfig<TDocument>;
  dataSource: TableListDataSource<TDocument>;
  initialState?: Partial<CollectionQueryState<TDocument>>;
  features?: FeatureSelection;
  getRowId?: (row: TDocument) => string;
  queryString?: TableListProps<TDocument>["queryString"];
  reserveEmptyRows?: boolean;
};

export type UseTableListResult<TDocument extends Record<string, unknown>> = {
  state: CollectionQueryState<TDocument>;
  setState: Dispatch<SetStateAction<CollectionQueryState<TDocument>>>;
  setFilters: (filters: FilterState<TDocument>) => void;
  setSorting: (sorting: readonly SortRule<TDocument>[]) => void;
  setPage: (page: PageState) => void;
  setVisibleFields: (fields: readonly FieldName<TDocument>[]) => void;
  tableProps: TableListProps<TDocument>;
  refetch: () => Promise<unknown>;
};

function initialQueryState<TDocument>(
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

export function useTableList<TDocument extends Record<string, unknown>>({
  config,
  dataSource,
  initialState,
  features: selectedFeatures,
  getRowId,
  queryString,
  reserveEmptyRows,
}: UseTableListOptions<TDocument>): UseTableListResult<TDocument> {
  const [state, setState] = useState(() => initialQueryState(config, initialState));
  const remote = dataSource.type === "remote" ? dataSource : null;
  const canFetch =
    remote !== null && (typeof remote.enabled === "function" ? remote.enabled(state) : remote.enabled !== false);
  const queryKey = remote
    ? typeof remote.queryKey === "function"
      ? remote.queryKey(state)
      : [...remote.queryKey, state]
    : ["table-list", config.slug, "client"];
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => {
      if (!remote) throw new Error("A client data source does not fetch data");
      return remote.fetch(state, { signal });
    },
    enabled: canFetch,
    meta: remote
      ? { ...viewTransitionQueryMeta, ...(remote.dataScope === undefined ? {} : { dataScope: remote.dataScope }) }
      : undefined,
    staleTime: remote?.staleTime,
    gcTime: remote?.gcTime,
    retry: remote?.retry,
    refetchInterval: remote?.refetchInterval,
    placeholderData: remote?.keepPreviousData
      ? (previous, previousQuery) =>
          previousQuery?.meta?.dataScope === remote.dataScope ? keepPreviousData(previous) : undefined
      : undefined,
  });

  const setFilters = useCallback((filters: FilterState<TDocument>) => {
    setState((current) => ({ ...current, filters, page: { ...current.page, pageIndex: 0 } }));
  }, []);
  const setSorting = useCallback((sorting: readonly SortRule<TDocument>[]) => {
    setState((current) => ({ ...current, sorting, page: { ...current.page, pageIndex: 0 } }));
  }, []);
  const setPage = useCallback((page: PageState) => setState((current) => ({ ...current, page })), []);
  const setVisibleFields = useCallback(
    (visibleFields: readonly FieldName<TDocument>[]) => setState((current) => ({ ...current, visibleFields })),
    [],
  );

  const rows = dataSource.type === "client" ? dataSource.rows : canFetch ? (query.data?.rows ?? []) : [];
  const rowCount = dataSource.type === "client" ? dataSource.rows.length : canFetch ? (query.data?.rowCount ?? 0) : 0;
  const enabled = (feature: keyof FeatureSelection) => selectedFeatures?.[feature] !== false;
  const tableFeatures: TableListFeatures<TDocument> = {
    filtering: enabled("filtering") ? { value: state.filters, onChange: setFilters } : false,
    sorting: enabled("sorting") ? { value: state.sorting, onChange: setSorting } : false,
    pagination: enabled("pagination") ? { value: state.page, rowCount, onChange: setPage } : false,
    columns: enabled("columns") ? { value: state.visibleFields, onChange: setVisibleFields } : false,
  };

  return {
    state,
    setState,
    setFilters,
    setSorting,
    setPage,
    setVisibleFields,
    tableProps: {
      config,
      rows,
      getRowId: getRowId ?? ((row) => String(row[config.idField])),
      features: tableFeatures,
      clientSide: dataSource.type === "client",
      status:
        remote && !canFetch
          ? "loading"
          : query.isError
            ? "error"
            : query.isPending && remote
              ? "loading"
              : query.isFetching
                ? "refreshing"
                : "idle",
      error: query.error,
      queryString,
      reserveEmptyRows,
    },
    refetch: canFetch ? async () => query.refetch() : async () => undefined,
  };
}
