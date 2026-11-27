import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import type { ApiV2CollectionParams, ApiV2ListOperation } from "@/modules/common/api/v2/apiV2Resources";
import type { ApiV2FilterResource } from "@/modules/common/api/v2/apiV2Where";
import type {
  CollectionConfig,
  CollectionRow,
  FieldName,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/config";
import { type ApiV2CollectionAdapter, buildApiV2CollectionAdapter } from "../api-v2/adapter";
import type { ApiV2CollectionMetadata } from "../api-v2/metadata";
import { useApiV2RuntimeFields } from "../api-v2/queries";
import {
  type ApiV2CollectionFetchOptions,
  type ApiV2CollectionResourceOf,
  type ApiV2FilterResourceOf,
  apiV2CollectionRequestParams,
  projectedState,
} from "../api-v2/requestParams";
import type { ApiV2DocumentMapping, ApiV2DocumentSchema } from "../api-v2/schema";
import type { TableViewDescriptor } from "../persistence";
import { rsqlSelectors } from "../rsql";
import type { CollectionQueryState, FilterExpression, TableListProps, TableListRestoredViewIssue } from "../types";
import { columnSelectors } from "../utils/queryString";
import { type ParsedTableView, parseRawTableView, type RawTableView } from "../utils/viewStorage";
import {
  initialQueryState,
  tableListResult,
  type UseTableListOptions,
  type UseTableListResult,
  useTableListQuery,
} from "./useTableList";
import { resolvedFromParsed, useTableViewState } from "./useTableViewState";
import { type TableViewStore, useLocalTableView, useUrlTableView } from "./useTableViewStore";

type TableOptions<TRow extends Record<string, unknown>> = Omit<UseTableListOptions<TRow>, "config" | "dataSource">;

function collectFilterFields<TDocument>(expression: FilterExpression<TDocument> | null, into: Set<string>): void {
  if (expression === null) return;
  if (expression.kind === "comparison") {
    into.add(expression.field);
    return;
  }
  for (const child of expression.children) collectFilterFields(child, into);
}

/**
 * The selectors an encoded view and the initial state name, whose runtime fields must be discovered before the view is
 * parsed, so unresolved fields never erase saved rules.
 */
function discoverySelectors<TRow extends Record<string, unknown>>(
  raw: Pick<RawTableView, "where" | "columns">,
  initialState: TableOptions<TRow>["initialState"],
): string[] {
  const selectors = new Set([
    ...(raw.where === null ? [] : rsqlSelectors(raw.where)),
    ...(raw.columns === null ? [] : columnSelectors(raw.columns)),
  ]);
  for (const name of initialState?.visibleFields ?? []) selectors.add(String(name));
  collectFilterFields(initialState?.filters?.expression ?? null, selectors);
  return [...selectors];
}

type ApiV2TableListQueryOptions = {
  staleTime?: number;
  gcTime?: number;
  retry?: boolean | number;
  refetchInterval?: number | false;
  keepPreviousData?: boolean;
};

/**
 * The total row count that labels pagination. Count identity excludes paging and projection, and prefix invalidation
 * after writes refreshes it along with the current page. `parameters` is only called while `enabled`.
 */
function useApiV2Totals<R extends ApiV2FilterResource>({
  list,
  enabled,
  parameters,
  selectRowCount,
  query,
}: {
  list: ApiV2ListOperation<R>;
  enabled: boolean;
  parameters: () => ApiV2CollectionParams<R>;
  selectRowCount: (body: unknown) => number;
  query: Pick<ApiV2TableListQueryOptions, "gcTime" | "refetchInterval" | "retry"> | undefined;
}): number | undefined {
  // Plain useQuery: the optional total only labels pagination; the table owns loading and errors.
  const totals = useQuery({
    queryKey: enabled ? list.queryKey(parameters()) : list.queryKey(),
    enabled,
    staleTime: Infinity,
    gcTime: query?.gcTime,
    refetchInterval: query?.refetchInterval,
    retry: query?.retry,
    queryFn: ({ signal }) => list.request(parameters(), { signal }),
    select: selectRowCount,
  });
  return totals.data;
}

/**
 * The table props with the API v2 status applied in order. A request the query codec rejects is an error. Pending
 * runtime fields then show loading with every control off, replacing `features`; otherwise a restored view that cannot
 * be used shows its issue. A later status keeps an earlier `error`.
 */
export function withApiV2Status<TRow extends Record<string, unknown>>(
  props: TableListProps<TRow>,
  {
    requestError,
    pending,
    restoredViewIssue,
  }: { requestError: unknown; pending: boolean; restoredViewIssue: TableListRestoredViewIssue | undefined },
): TableListProps<TRow> {
  const withRequestError: TableListProps<TRow> =
    requestError === null ? props : { ...props, status: "error", error: requestError };
  if (pending) {
    return {
      ...withRequestError,
      status: "loading",
      features: { filtering: false, columns: false, sorting: false, pagination: false },
    };
  }
  if (restoredViewIssue !== undefined) return { ...withRequestError, status: "error", restoredViewIssue };
  return withRequestError;
}

export type UseApiV2TableListOptions<
  TDocument extends Record<string, unknown>,
  TEntry extends ApiV2CollectionResourceOf<TEntry>,
  TId extends FieldName<TDocument> = FieldName<TDocument>,
  TTitle extends FieldName<TDocument> = FieldName<TDocument>,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
> = {
  resource: TEntry;
  config: CollectionConfig<TDocument, TId, TTitle>;
  request?: ApiV2CollectionFetchOptions<TDocument, ApiV2FilterResourceOf<NoInfer<TEntry>>>;
  metadata?: ApiV2CollectionMetadata<TDocument>;
  /** Recover page-owned presets after parsing, before queries or saved-view validation. */
  normalizeFilterExpression?: (expression: FilterExpression<TDocument> | null) => FilterExpression<TDocument> | null;
  table?: TableOptions<CollectionRow<TDocument, TId | TTitle>>;
  query?: ApiV2TableListQueryOptions;
} & ApiV2DocumentMapping<TDocument, TSchema, TEntry>;

export type UseApiV2TableListResult<
  TDocument extends Record<string, unknown>,
  TRow extends Record<string, unknown>,
  R extends ApiV2FilterResource = never,
> = UseTableListResult<TRow> & {
  adapter: ApiV2CollectionAdapter<TDocument, R>;
};

export type UsePersistedApiV2TableListOptions<
  TDocument extends Record<string, unknown>,
  TEntry extends ApiV2CollectionResourceOf<TEntry>,
  TId extends FieldName<TDocument> = FieldName<TDocument>,
  TTitle extends FieldName<TDocument> = FieldName<TDocument>,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
> = UseApiV2TableListOptions<TDocument, TEntry, TId, TTitle, TSchema> & {
  /** The table's descriptor, also spread into its route with `tableViewRouteOptions`. */
  persistence: TableViewDescriptor<"issue">;
};

function useApiV2TableListFromStore<
  TDocument extends Record<string, unknown>,
  TEntry extends ApiV2CollectionResourceOf<TEntry>,
  const TId extends FieldName<TDocument> = FieldName<TDocument>,
  const TTitle extends FieldName<TDocument> = FieldName<TDocument>,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
>(
  options: UseApiV2TableListOptions<TDocument, TEntry, TId, TTitle, TSchema>,
  store: TableViewStore,
  persistence?: TableViewDescriptor,
): UseApiV2TableListResult<TDocument, CollectionRow<TDocument, TId | TTitle>, ApiV2FilterResourceOf<TEntry>> {
  const {
    resource,
    config,
    documentSchema,
    request,
    metadata: suppliedMetadata,
    normalizeFilterExpression,
    table,
    query,
  } = options;
  type Row = CollectionRow<TDocument, TId | TTitle>;
  const featureEnabled = (feature: "filtering" | "sorting" | "columns") => table?.features?.[feature] !== false;

  const { raw } = store;
  const fields = useApiV2RuntimeFields({
    resource,
    selectors: discoverySelectors(raw, table?.initialState),
    request,
    metadata: suppliedMetadata,
  });
  const { runtimeFields, selectRuntimeField } = fields;
  const metadata = fields.metadata;
  const authScope = fields.scope;
  const dataScope = JSON.stringify([resource.name, authScope]);
  const adapter = useMemo(
    () =>
      buildApiV2CollectionAdapter<TDocument, ApiV2FilterResourceOf<TEntry>>({
        config,
        metadata,
        runtimeFields,
        documentSchema,
        mapDocument: options.mapDocument,
      }),
    [config, documentSchema, options.mapDocument, metadata, runtimeFields],
  );
  const rowConfig = adapter.config as unknown as ResolvedCollectionConfig<Row>;
  const [base] = useState(() => initialQueryState(rowConfig, table?.initialState));
  const normalizeExpression = normalizeFilterExpression as
    | ((expression: FilterExpression<Row> | null) => FilterExpression<Row> | null)
    | undefined;
  const parametersFor = (
    state: CollectionQueryState<Row>,
    totals = false,
  ): ApiV2CollectionParams<ApiV2FilterResourceOf<TEntry>> => ({
    ...apiV2CollectionRequestParams(adapter, state as unknown as CollectionQueryState<TDocument>, request),
    ...(totals ? { totals: true } : {}),
  });
  // The query codec throws for a state the metadata forbids (a limit, a wildcard, an invalid picker
  // value). Such a state must never be built while rendering, so callers check it here first.
  const requestError = (state: CollectionQueryState<Row>): unknown => {
    try {
      parametersFor(state);
      return null;
    } catch (error) {
      return error;
    }
  };
  const parsed = parseRawTableView(raw, rowConfig);
  const resolvedView: ParsedTableView<Row> = {
    search: featureEnabled("filtering") ? parsed.search : undefined,
    where: featureEnabled("filtering") ? parsed.where : undefined,
    sort: featureEnabled("sorting") ? parsed.sort : undefined,
    columns: featureEnabled("columns") ? parsed.columns : undefined,
  };
  const unresolved = resolvedView.where === null || resolvedView.sort === null || resolvedView.columns === null;
  const restoredState = (): CollectionQueryState<Row> => {
    const filters = {
      search: resolvedView.search ?? base.filters.search,
      expression: resolvedView.where === undefined ? base.filters.expression : resolvedView.where,
    };
    return {
      filters: {
        ...filters,
        expression: normalizeExpression ? normalizeExpression(filters.expression) : filters.expression,
      },
      sorting: resolvedView.sort ?? base.sorting,
      visibleFields: resolvedView.columns ?? base.visibleFields,
      page: { pageIndex: 0, pageSize: 1 },
    };
  };
  const invalid =
    store.malformed ||
    fields.missing.length > 0 ||
    unresolved ||
    (!fields.pending && requestError(restoredState()) !== null);
  const blocked = fields.pending || fields.error !== null || invalid;
  const view = useTableViewState<Row>({
    config: rowConfig,
    base,
    store,
    features: table?.features,
    normalizeFilterExpression: normalizeExpression,
    reject: requestError,
    resolved: blocked ? null : resolvedFromParsed(resolvedView),
  });
  const stateError = view.blocked ? null : requestError(view.state);
  const ready = (state: CollectionQueryState<Row>) => !view.blocked && requestError(state) === null;
  const totalState = (state: CollectionQueryState<Row>) => ({
    ...state,
    visibleFields: [],
    page: { pageIndex: 0, pageSize: 1 },
  });
  const parsePage = (body: unknown, state: CollectionQueryState<Row>) => {
    const projected = projectedState(adapter, state, request?.projection ?? "visible");
    const page = adapter.parseResponse(body, adapter.selectedFields(projected));
    request?.validateRows?.(page.rows);
    return page;
  };
  const tableOptions = {
    ...table,
    // The configuration keeps the strict document type, and a row carries the projection. A field
    // renderer reads its own field only, and it declares each other field it reads in
    // `list.dependencies`, which the request then selects. A renderer therefore never reads a field
    // that the response omitted.
    config: rowConfig,
    dataSource: {
      type: "remote" as const,
      dataScope,
      queryKey: (state: CollectionQueryState<Row>) =>
        ready(state) ? resource.list.queryKey(parametersFor(state)) : resource.list.queryKey(),
      wire: {
        request: (state: CollectionQueryState<Row>, { signal }: { signal: AbortSignal }) =>
          resource.list.request(parametersFor(state), { signal }),
        select: parsePage,
      },
      ...query,
      enabled: ready,
    },
  };
  const result = tableListResult<Row>(
    tableOptions,
    view,
    useTableListQuery(rowConfig, view.state, tableOptions.dataSource),
    persistence,
  );
  const totalRows = useApiV2Totals({
    list: resource.list,
    enabled: !view.blocked && stateError === null,
    parameters: () => parametersFor(totalState(view.state), true),
    selectRowCount: (body) => {
      const { rowCount } = parsePage(body, totalState(view.state));
      if (rowCount === undefined) throw new Error("API V2 totals response omitted totalDocs");
      return rowCount;
    },
    query,
  });
  // A rejected edit shows the table's error state and keeps the last valid view in the URL and storage,
  // so the toolbar stays usable; the next valid edit writes and fetches again.
  const features = result.tableProps.features;
  const tableProps = withApiV2Status(
    {
      ...result.tableProps,
      features: {
        ...features,
        pagination: features.pagination === false ? false : { ...features.pagination, rowCount: totalRows },
      },
    },
    {
      requestError: stateError,
      pending: fields.pending,
      restoredViewIssue:
        invalid || fields.error
          ? {
              kind: fields.error ? "network" : "invalid",
              encoded: raw.where ?? raw.columns ?? "",
              retry: () => {
                void fields.retry();
              },
              remove: store.remove,
            }
          : undefined,
    },
  );
  return {
    ...result,
    tableProps: {
      ...tableProps,
      runtimeFields: { definitions: runtimeFields, onSelect: selectRuntimeField },
      authScope: fields.scope,
    },
    adapter,
  };
}

/** An API v2 table whose view lives in component state only: it never touches the URL or browser storage. */
export function useApiV2TableList<
  TDocument extends Record<string, unknown>,
  TEntry extends ApiV2CollectionResourceOf<TEntry>,
  const TId extends FieldName<TDocument> = FieldName<TDocument>,
  const TTitle extends FieldName<TDocument> = FieldName<TDocument>,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
>(
  options: UseApiV2TableListOptions<TDocument, TEntry, TId, TTitle, TSchema>,
): UseApiV2TableListResult<TDocument, CollectionRow<TDocument, TId | TTitle>, ApiV2FilterResourceOf<TEntry>> {
  return useApiV2TableListFromStore(options, useLocalTableView());
}

/**
 * An API v2 table whose view lives in its URL parameters, and in browser storage when its route spreads
 * `tableViewRouteOptions`. A stored or shared view that cannot be restored shows `RestoredViewIssue`.
 * Requires a nuqs adapter.
 */
export function usePersistedApiV2TableList<
  TDocument extends Record<string, unknown>,
  TEntry extends ApiV2CollectionResourceOf<TEntry>,
  const TId extends FieldName<TDocument> = FieldName<TDocument>,
  const TTitle extends FieldName<TDocument> = FieldName<TDocument>,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
>(
  options: UsePersistedApiV2TableListOptions<TDocument, TEntry, TId, TTitle, TSchema>,
): UseApiV2TableListResult<TDocument, CollectionRow<TDocument, TId | TTitle>, ApiV2FilterResourceOf<TEntry>> {
  return useApiV2TableListFromStore(options, useUrlTableView(options.persistence), options.persistence);
}
