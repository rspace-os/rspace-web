import { parseAsString, useQueryStates } from "nuqs";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import {
  parseColumns,
  parseFilters,
  parseSorting,
  serializeColumns,
  serializeFilters,
  serializeSorting,
} from "./queryStringState";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "./rsql/rsqlCodec";
import type { TableListFeatures, TableListQueryStringOptions } from "./tableListState";
import {
  columnSelectors,
  loadStoredTableView,
  saveStoredTableView,
  storedTableViewSelectors,
  tableViewStorageKey,
} from "./tableViewStorage";

const emptyFilters = { search: "", expression: null } as const;
const emptySorting = [] as const;

type RestoringState = Partial<Record<"filters" | "columns" | "sorting", string>>;

function staleFieldPolicy<TDocument>(config: ResolvedCollectionConfig<TDocument>) {
  const namespaces = config.runtimeNamespaces ?? [];
  if (namespaces.length === 0) return undefined;
  const known = new Set(config.fields.map((field) => String(field.name)));
  return {
    isStale: (name: string) => !known.has(name) && namespaces.some((namespace) => name.startsWith(`${namespace}.`)),
  };
}

export function parameterNames(slug: string, options: true | TableListQueryStringOptions) {
  const configuredPrefix = typeof options === "object" ? options.parameterPrefix?.trim() : undefined;
  const prefix = configuredPrefix || slug;
  return {
    search: `${prefix}.q`,
    where: `${prefix}.where`,
    legacyFilters: `${prefix}.filters`,
    columns: `${prefix}.columns`,
    sort: `${prefix}.sort`,
  };
}

export function savedViewSelectors(slug: string, options: true | TableListQueryStringOptions): readonly string[] {
  if (typeof window === "undefined") return [];
  const names = parameterNames(slug, options);
  const parameters = new URLSearchParams(window.location.search);
  const where = parameters.get(names.where);
  const columns = parameters.get(names.columns);
  const fromUrl = [
    ...(where === null ? [] : rsqlSelectors(where)),
    ...(columns === null ? [] : columnSelectors(columns)),
  ];
  if (Object.values(names).some((name) => parameters.has(name))) return fromUrl;
  const configuredTableId = typeof options === "object" ? options.tableId?.trim() : undefined;
  return storedTableViewSelectors(tableViewStorageKey(configuredTableId || slug));
}

export function useTableListQueryString<TDocument>(
  config: ResolvedCollectionConfig<TDocument>,
  features: TableListFeatures<TDocument>,
  options: true | TableListQueryStringOptions,
  preserveInvalid = false,
): TableListFeatures<TDocument> {
  const names = parameterNames(config.slug, options);
  const configuredTableId = typeof options === "object" ? options.tableId?.trim() : undefined;
  const storageKey = tableViewStorageKey(configuredTableId || config.slug);
  const defaultSort = config.defaultSort ?? emptySorting;
  const stale = useMemo(() => (preserveInvalid ? undefined : staleFieldPolicy(config)), [config, preserveInvalid]);
  // All readers of these nuqs keys use strings. Its shared state broadcasts parsed values,
  // so mixing a raw reader and typed parsers would pass arrays/ASTs into the raw reader.
  const [rawQueryState, setRawQueryState] = useQueryStates(
    {
      search: parseAsString,
      where: parseAsString,
      legacyFilters: parseAsString,
      columns: parseAsString,
      sort: parseAsString,
    },
    { history: "replace", urlKeys: names },
  );
  const queryState = useMemo(
    () => ({
      search: rawQueryState.search,
      where: rawQueryState.where === null ? null : parseRsqlExpression(rawQueryState.where, config, stale),
      legacyFilters: rawQueryState.legacyFilters === null ? null : parseFilters(rawQueryState.legacyFilters, config),
      columns:
        rawQueryState.columns === null
          ? config.defaultColumns
          : (parseColumns(rawQueryState.columns, config, stale) ?? config.defaultColumns),
      sort: rawQueryState.sort === null ? defaultSort : (parseSorting(rawQueryState.sort, config) ?? defaultSort),
    }),
    [rawQueryState, config, stale, defaultSort],
  );
  const viewValid =
    !preserveInvalid ||
    ((rawQueryState.where === null || queryState.where !== null) &&
      (rawQueryState.legacyFilters === null || queryState.legacyFilters !== null) &&
      (rawQueryState.columns === null || parseColumns(rawQueryState.columns, config) !== null) &&
      (rawQueryState.sort === null || parseSorting(rawQueryState.sort, config) !== null));
  const setQueryState = useCallback(
    (next: Partial<typeof queryState>) =>
      setRawQueryState({
        search: next.search,
        where: next.where === undefined ? undefined : next.where === null ? null : serializeRsqlExpression(next.where),
        legacyFilters:
          next.legacyFilters === undefined
            ? undefined
            : next.legacyFilters === null
              ? null
              : serializeFilters(next.legacyFilters),
        columns:
          next.columns === undefined
            ? undefined
            : serializeColumns(next.columns) === serializeColumns(config.defaultColumns)
              ? null
              : serializeColumns(next.columns),
        sort:
          next.sort === undefined
            ? undefined
            : serializeSorting(next.sort) === serializeSorting(defaultSort)
              ? null
              : serializeSorting(next.sort),
      }),
    [setRawQueryState, config.defaultColumns, defaultSort],
  );
  const urlOwnsInitialState = useRef(Object.values(rawQueryState).some((value) => value !== null));
  const [storageReady, setStorageReady] = useState(urlOwnsInitialState.current);
  const featuresRef = useRef(features);
  const restoring = useRef<RestoringState>({});
  featuresRef.current = features;
  const queryFilterState = useMemo(() => {
    const hasRsqlState = queryState.search !== null || queryState.where !== null;
    if (hasRsqlState) return { search: queryState.search ?? "", expression: queryState.where };
    return queryState.legacyFilters ?? emptyFilters;
  }, [queryState.legacyFilters, queryState.search, queryState.where]);
  const queryFilters = serializeFilters(queryFilterState);
  const queryColumns = serializeColumns(queryState.columns);
  const querySorting = serializeSorting(queryState.sort);
  const filteringEnabled = features.filtering !== false;
  const columnsEnabled = features.columns !== false;
  const sortingEnabled = features.sorting !== false;
  const featureFilters = features.filtering === false ? null : serializeFilters(features.filtering.value);
  const featureColumns = features.columns === false ? null : serializeColumns(features.columns.value);
  const featureSorting = features.sorting === false ? null : serializeSorting(features.sorting.value);

  useEffect(() => {
    if (!viewValid) return;
    if (urlOwnsInitialState.current) return;
    let active = true;
    const restored = loadStoredTableView(storageKey, config, stale);
    const currentFeatures = featuresRef.current;
    void setQueryState({
      search: currentFeatures.filtering === false ? null : (restored?.search ?? null),
      where: currentFeatures.filtering === false ? null : (restored?.where ?? null),
      legacyFilters: null,
      columns: currentFeatures.columns === false ? config.defaultColumns : (restored?.columns ?? config.defaultColumns),
      sort: currentFeatures.sorting === false ? defaultSort : (restored?.sort ?? defaultSort),
    }).then(() => active && setStorageReady(true));
    return () => {
      active = false;
    };
  }, [config, defaultSort, setQueryState, stale, storageKey, viewValid]);

  useEffect(() => {
    if (!viewValid) return;
    const current = featuresRef.current;
    if (current.filtering !== false) {
      if (serializeFilters(current.filtering.value) === queryFilters) {
        delete restoring.current.filters;
      } else {
        restoring.current.filters = queryFilters;
        current.filtering.onChange(queryFilterState);
      }
    }
    if (current.columns !== false) {
      if (serializeColumns(current.columns.value) === queryColumns) {
        delete restoring.current.columns;
      } else {
        restoring.current.columns = queryColumns;
        current.columns.onChange(queryState.columns);
      }
    }
    if (current.sorting !== false) {
      if (serializeSorting(current.sorting.value) === querySorting) {
        delete restoring.current.sorting;
      } else {
        restoring.current.sorting = querySorting;
        current.sorting.onChange(queryState.sort);
      }
    }
  }, [queryColumns, queryFilterState, queryFilters, querySorting, queryState.columns, queryState.sort, viewValid]);

  useEffect(() => {
    if (!viewValid) return;
    const current = featuresRef.current;
    let search: string | null | undefined;
    let where: typeof queryState.where | undefined;
    let legacyFilters: null | undefined;
    let columns: typeof queryState.columns | undefined;
    let sort: typeof queryState.sort | undefined;

    if (featureFilters !== null) {
      if (restoring.current.filters !== undefined) {
        if (restoring.current.filters === featureFilters) delete restoring.current.filters;
      } else if (current.filtering !== false && featureFilters !== queryFilters) {
        search = current.filtering.value.search || null;
        where = current.filtering.value.expression;
        legacyFilters = null;
      }
    }
    if (featureColumns !== null) {
      if (restoring.current.columns !== undefined) {
        if (restoring.current.columns === featureColumns) delete restoring.current.columns;
      } else if (current.columns !== false && featureColumns !== queryColumns) {
        columns = current.columns.value;
      }
    }
    if (featureSorting !== null) {
      if (restoring.current.sorting !== undefined) {
        if (restoring.current.sorting === featureSorting) delete restoring.current.sorting;
      } else if (current.sorting !== false && featureSorting !== querySorting) {
        sort = current.sorting.value;
      }
    }
    if (
      search !== undefined ||
      where !== undefined ||
      legacyFilters !== undefined ||
      columns !== undefined ||
      sort !== undefined
    ) {
      void setQueryState({ search, where, legacyFilters, columns, sort });
    }
  }, [
    featureColumns,
    featureFilters,
    featureSorting,
    queryColumns,
    queryFilters,
    querySorting,
    setQueryState,
    viewValid,
  ]);

  useEffect(() => {
    if (!viewValid) return;
    if (!storageReady) return;
    saveStoredTableView(
      storageKey,
      {
        search: filteringEnabled ? queryFilterState.search : null,
        where: filteringEnabled ? queryFilterState.expression : null,
        columns: columnsEnabled ? queryState.columns : config.defaultColumns,
        sort: sortingEnabled ? queryState.sort : defaultSort,
      },
      config,
    );
  }, [
    config,
    defaultSort,
    columnsEnabled,
    filteringEnabled,
    queryFilterState,
    queryState.columns,
    queryState.sort,
    storageKey,
    storageReady,
    viewValid,
    sortingEnabled,
  ]);

  return features;
}
