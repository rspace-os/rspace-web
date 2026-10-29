import { createElement, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type {
  CollectionConfig,
  CollectionRow,
  FieldName,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/config";
import { type ApiV2CollectionAdapter, type ApiV2DocumentSchema, createApiV2CollectionAdapter } from "../api-v2/adapter";
import { type ApiV2CollectionFetchOptions, createApiV2CollectionFetcher, projectedState } from "../api-v2/fetcher";
import type { ApiV2CollectionMetadata } from "../api-v2/metadata";
import { useApiV2RuntimeFields } from "../api-v2/queries";
import { isPickerExpressionValid } from "../api-v2/queryParams";
import type { RuntimeFieldDefinition } from "../api-v2/runtimeFieldCatalog";
import { parseRsqlExpression } from "../rsql";
import type { CollectionFetcher, CollectionQueryState, FilterExpression } from "../types";
import { parseColumns, parseSorting, serializeColumns, serializeFilters, serializeSorting } from "../utils/queryString";
import { emptySavedTableView, type SavedTableView, SavedTableViewReader } from "./savedView";
import { type UseTableListOptions, type UseTableListResult, useTableList } from "./state";

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
  TId extends FieldName<TDocument> = FieldName<TDocument>,
  TTitle extends FieldName<TDocument> = FieldName<TDocument>,
> = {
  resourceName: string;
  config: CollectionConfig<TDocument, TId, TTitle>;
  documentSchema: ApiV2DocumentSchema<TDocument>;
  request?: ApiV2CollectionFetchOptions<TDocument>;
  metadata?: ApiV2CollectionMetadata<TDocument>;
  table?: TableOptions<CollectionRow<TDocument, TId | TTitle>>;
  query?: {
    staleTime?: number;
    gcTime?: number;
    retry?: boolean | number;
    refetchInterval?: number | false;
    keepPreviousData?: boolean;
  };
};

export type UseApiV2TableListResult<
  TDocument extends Record<string, unknown>,
  TRow extends Record<string, unknown>,
> = UseTableListResult<TRow> & {
  adapter: ApiV2CollectionAdapter<TDocument>;
  selectRuntimeField: (namespace: string, definition: RuntimeFieldDefinition) => void;
};

export function useApiV2TableList<
  TDocument extends Record<string, unknown>,
  const TId extends FieldName<TDocument> = FieldName<TDocument>,
  const TTitle extends FieldName<TDocument> = FieldName<TDocument>,
>({
  resourceName,
  config,
  documentSchema,
  request,
  metadata: suppliedMetadata,
  table,
  query,
}: UseApiV2TableListOptions<TDocument, TId, TTitle>): UseApiV2TableListResult<
  TDocument,
  CollectionRow<TDocument, TId | TTitle>
> {
  type Row = CollectionRow<TDocument, TId | TTitle>;
  const { t, i18n } = useTranslation();

  const [savedState, setSavedState] = useState<SavedTableView | null>(null);
  const persistenceEnabled = table?.queryString !== false;
  const saved = persistenceEnabled ? (savedState ?? emptySavedTableView) : emptySavedTableView;
  const selectors = new Set(saved.selectors);
  for (const name of table?.initialState?.visibleFields ?? []) selectors.add(String(name));
  collectFilterFields(table?.initialState?.filters?.expression ?? null, selectors);
  const fields = useApiV2RuntimeFields({
    resourceName,
    selectors: [...selectors],
    request,
    metadata: suppliedMetadata,
  });
  const { runtimeFields, selectRuntimeField } = fields;
  const metadata = fields.metadata;
  const endpoint = request?.endpoint ?? `/api/v2/${metadata.resourceName}`;
  const authScope = fields.scope;
  const dataScope = JSON.stringify([resourceName, endpoint, authScope]);
  const adapter = useMemo(
    () =>
      createApiV2CollectionAdapter({
        config,
        documentSchema,
        metadata,
        runtimeFields,
        translate: (key, values) => String(t(key as never, values as never)),
        language: i18n.resolvedLanguage,
      }),
    [config, documentSchema, metadata, runtimeFields, t, i18n.resolvedLanguage],
  );
  const rowConfig = adapter.config as unknown as ResolvedCollectionConfig<Row>;
  const restoredExpression = saved.raw.where === null ? null : parseRsqlExpression(saved.raw.where, rowConfig);
  const restoredFilters = { search: saved.raw.search ?? "", expression: restoredExpression };
  const restoredColumns =
    saved.raw.columns === null ? rowConfig.defaultColumns : parseColumns(saved.raw.columns, rowConfig);
  const restoredSorting =
    saved.raw.sort === null ? (rowConfig.defaultSort ?? []) : parseSorting(saved.raw.sort, rowConfig);
  const restoredPickerValuesValid =
    restoredFilters.expression === null ||
    isPickerExpressionValid(restoredFilters.expression, rowConfig, fields.metadata);
  const invalid =
    saved.malformed ||
    !restoredPickerValuesValid ||
    fields.missing.length > 0 ||
    (saved.raw.where !== null && restoredExpression === null) ||
    restoredColumns === null ||
    restoredSorting === null;
  const blocked = (persistenceEnabled && savedState === null) || fields.pending || fields.error !== null || invalid;
  const restoredState = {
    filters: restoredFilters,
    visibleFields: restoredColumns ?? rowConfig.defaultColumns,
    sorting: restoredSorting ?? rowConfig.defaultSort ?? [],
  };
  const fetchCollection = useMemo(() => createApiV2CollectionFetcher(adapter, request), [adapter, request]);
  // Acknowledge each restored signature so later edits may diverge and fetch.
  // A new signature must match its saved state before it is acknowledged.
  const [restoredSignature, setRestoredSignature] = useState<string | null>(null);
  const matchesSaved = (state: CollectionQueryState<Row>) =>
    (table?.features?.filtering === false || serializeFilters(state.filters) === serializeFilters(restoredFilters)) &&
    (table?.features?.columns === false ||
      serializeColumns<Row>(state.visibleFields) === serializeColumns<Row>(restoredState.visibleFields)) &&
    (table?.features?.sorting === false || serializeSorting(state.sorting) === serializeSorting(restoredState.sorting));
  const ready = (state: CollectionQueryState<Row>) =>
    !blocked && (table?.queryString === false || restoredSignature === saved.signature || matchesSaved(state));
  const result = useTableList<Row>({
    ...table,
    // The configuration keeps the strict document type, and a row carries the projection. A field
    // renderer reads its own field only, and it declares each other field it reads in
    // `list.dependencies`, which the request then selects. A renderer therefore never reads a field
    // that the response omitted.
    config: rowConfig,
    initialState: { ...table?.initialState, ...(saved.active ? restoredState : {}) },
    dataSource: {
      type: "remote",
      dataScope,
      queryKey: (state) => [
        "api-v2",
        resourceName,
        endpoint,
        authScope,
        ready(state)
          ? {
              state: projectedState(adapter, state, request?.projection ?? "visible"),
              depth: request?.depth,
              projection: request?.projection,
              baseFilter: request?.baseFilter,
              metadata,
              runtimeFields,
              config: {
                idField: config.idField,
                useAsTitle: config.useAsTitle,
                listSearchableFields: config.listSearchableFields,
                fields: config.fields.map((field) => ({
                  name: field.name,
                  type: field.type,
                  dependencies: field.list === false ? undefined : field.list?.dependencies,
                  relationTo: field.type === "relationship" ? field.relationTo : undefined,
                })),
              },
              schemaFields: Object.keys(documentSchema.entries),
            }
          : ["pending-saved-view", saved.signature],
      ],
      fetch: fetchCollection as unknown as CollectionFetcher<Row>,
      ...query,
      enabled: ready,
    },
  });
  useEffect(() => {
    if (!blocked && matchesSaved(result.state) && restoredSignature !== saved.signature) {
      setRestoredSignature(saved.signature);
    }
  }, [blocked, restoredSignature, result.state, saved.signature]);
  return {
    ...result,
    tableProps: {
      ...result.tableProps,
      stateSync: persistenceEnabled
        ? createElement(SavedTableViewReader, {
            key: config.slug,
            slug: config.slug,
            options: table?.queryString || true,
            onChange: setSavedState,
          })
        : undefined,
      queryString: blocked ? false : result.tableProps.queryString,
      ...(fields.pending || (persistenceEnabled && savedState === null)
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
              encoded: saved.raw.where ?? saved.raw.columns ?? "",
              retry: () => {
                void fields.retry();
              },
              remove: saved.remove,
            },
          }
        : {}),
      onSelectRuntimeField: selectRuntimeField,
      runtimeFieldDefinitions: runtimeFields,
      runtimeFieldAuthScope: fields.scope,
    },
    adapter,
    selectRuntimeField,
  };
}
