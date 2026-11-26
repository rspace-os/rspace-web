import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import type { ApiV2CollectionParams } from "@/modules/common/api/v2/apiV2Resources";
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
import type { CollectionQueryState, FilterExpression } from "../types";
import { columnSelectors } from "../utils/queryString";
import { parseRawTableView } from "../utils/viewStorage";
import {
  initialQueryState,
  tableListResult,
  type UseTableListOptions,
  type UseTableListResult,
  useTableListQuery,
} from "./useTableList";
import { useTableViewState } from "./useTableViewState";
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
  query?: {
    staleTime?: number;
    gcTime?: number;
    retry?: boolean | number;
    refetchInterval?: number | false;
    keepPreviousData?: boolean;
  };
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
  // Discover runtime fields from the encoded view before parsing, so unresolved fields never erase saved rules.
  const selectors = new Set([
    ...(raw.where === null ? [] : rsqlSelectors(raw.where)),
    ...(raw.columns === null ? [] : columnSelectors(raw.columns)),
  ]);
  for (const name of table?.initialState?.visibleFields ?? []) selectors.add(String(name));
  collectFilterFields(table?.initialState?.filters?.expression ?? null, selectors);
  const fields = useApiV2RuntimeFields({
    resource,
    selectors: [...selectors],
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
  const resolvedView = {
    search: featureEnabled("filtering") ? parsed.search : undefined,
    expression: featureEnabled("filtering") ? parsed.where : undefined,
    sorting: featureEnabled("sorting") ? parsed.sort : undefined,
    visibleFields: featureEnabled("columns") ? parsed.columns : undefined,
  };
  const unresolved =
    resolvedView.expression === null || resolvedView.sorting === null || resolvedView.visibleFields === null;
  const restoredState = (): CollectionQueryState<Row> => {
    const filters = {
      search: resolvedView.search ?? base.filters.search,
      expression: resolvedView.expression === undefined ? base.filters.expression : resolvedView.expression,
    };
    return {
      filters: {
        ...filters,
        expression: normalizeExpression ? normalizeExpression(filters.expression) : filters.expression,
      },
      sorting: resolvedView.sorting ?? base.sorting,
      visibleFields: resolvedView.visibleFields ?? base.visibleFields,
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
    resolved: blocked
      ? null
      : {
          search: resolvedView.search,
          expression: resolvedView.expression ?? undefined,
          sorting: resolvedView.sorting ?? undefined,
          visibleFields: resolvedView.visibleFields ?? undefined,
        },
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
  // Count identity excludes paging and projection. Prefix invalidation after writes
  // refreshes this active query along with the current page.
  // Plain useQuery: the optional total only labels pagination; the table owns loading and errors.
  const totalsReady = !view.blocked && stateError === null;
  const totalsParameters = () => parametersFor(totalState(view.state), true);
  const totals = useQuery({
    queryKey: totalsReady ? resource.list.queryKey(totalsParameters()) : resource.list.queryKey(),
    enabled: totalsReady,
    staleTime: Infinity,
    gcTime: query?.gcTime,
    refetchInterval: query?.refetchInterval,
    retry: query?.retry,
    queryFn: ({ signal }) => resource.list.request(totalsParameters(), { signal }),
    select: (body) => {
      const { rowCount } = parsePage(body, totalState(view.state));
      if (rowCount === undefined) throw new Error("API V2 totals response omitted totalDocs");
      return rowCount;
    },
  });
  // A rejected edit shows the table's error state and keeps the last valid view in the URL and storage,
  // so the toolbar stays usable; the next valid edit writes and fetches again.
  return {
    ...result,
    tableProps: {
      ...result.tableProps,
      features: {
        ...result.tableProps.features,
        pagination:
          result.tableProps.features.pagination === false
            ? false
            : {
                ...result.tableProps.features.pagination,
                rowCount: totals.data,
              },
      },
      ...(stateError === null ? {} : { status: "error" as const, error: stateError }),
      ...(fields.pending
        ? {
            status: "loading" as const,
            features: {
              filtering: false as const,
              columns: false as const,
              sorting: false as const,
              pagination: false as const,
            },
          }
        : {}),
      ...(!fields.pending && (invalid || fields.error)
        ? {
            status: "error" as const,
            restoredViewIssue: {
              kind: fields.error ? ("network" as const) : ("invalid" as const),
              encoded: raw.where ?? raw.columns ?? "",
              retry: () => {
                void fields.retry();
              },
              remove: store.remove,
            },
          }
        : {}),
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
