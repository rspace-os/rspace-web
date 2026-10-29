import { parseAsString, useQueryStates } from "nuqs";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/config";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import { parseRsqlExpression, serializeRsqlExpression } from "../rsql";
import type { TableListFeatures, TableListQueryStringOptions } from "../types";
import { parseColumns, parseSorting, serializeColumns, serializeFilters, serializeSorting } from "../utils/queryString";
import { loadStoredTableView, saveStoredTableView, tableViewStorageKey } from "../utils/viewStorage";

const emptySorting = [] as const;

type RestoringState = Partial<Record<"filters" | "columns" | "sorting", string>>;

function staleFieldPolicy<TDocument>(config: ResolvedCollectionConfig<TDocument>) {
  const namespaces = config.runtimeNamespaces ?? [];
  if (namespaces.length === 0) return undefined;
  const known = new Set(config.fields.map((field) => String(field.name)));
  return {
    isStale: (name: string) =>
      !known.has(name) && namespaces.some((namespace) => isRuntimeFieldInNamespace(name, namespace)),
  };
}

export function parameterNames(slug: string, options: true | TableListQueryStringOptions) {
  const configuredPrefix = typeof options === "object" ? options.parameterPrefix?.trim() : undefined;
  const prefix = configuredPrefix || slug;
  return {
    search: `${prefix}.q`,
    where: `${prefix}.where`,
    columns: `${prefix}.columns`,
    sort: `${prefix}.sort`,
  };
}

export function tableIdFor(slug: string, options: boolean | TableListQueryStringOptions): string {
  return (typeof options === "object" ? options.tableId?.trim() : undefined) || slug;
}

export function useTableListQueryString<TDocument>(
  config: ResolvedCollectionConfig<TDocument>,
  features: TableListFeatures<TDocument>,
  options: true | TableListQueryStringOptions,
  preserveInvalid = false,
): void {
  const names = parameterNames(config.slug, options);
  const storageKey = tableViewStorageKey(tableIdFor(config.slug, options));
  const defaultSort = config.defaultSort ?? emptySorting;
  const stale = useMemo(() => (preserveInvalid ? undefined : staleFieldPolicy(config)), [config, preserveInvalid]);
  // All readers of these nuqs keys use strings. Its shared state broadcasts parsed values,
  // so mixing a raw reader and typed parsers would pass arrays/ASTs into the raw reader.
  const [rawQueryState, setRawQueryState] = useQueryStates(
    {
      search: parseAsString,
      where: parseAsString,
      columns: parseAsString,
      sort: parseAsString,
    },
    { history: "replace", urlKeys: names },
  );
  const parsedQueryState = useMemo(
    () => ({
      search: rawQueryState.search,
      where: rawQueryState.where === null ? null : parseRsqlExpression(rawQueryState.where, config, stale),
      columns: rawQueryState.columns === null ? null : parseColumns(rawQueryState.columns, config, stale),
      sort: rawQueryState.sort === null ? null : parseSorting(rawQueryState.sort, config),
    }),
    [rawQueryState, config, stale],
  );
  const viewValid =
    !preserveInvalid ||
    ((rawQueryState.where === null || parsedQueryState.where !== null) &&
      (rawQueryState.columns === null || parsedQueryState.columns !== null) &&
      (rawQueryState.sort === null || parsedQueryState.sort !== null));
  const queryState = {
    ...parsedQueryState,
    columns: parsedQueryState.columns ?? config.defaultColumns,
    sort: parsedQueryState.sort ?? defaultSort,
  };
  const setQueryState = useCallback(
    (next: Partial<typeof queryState>) =>
      setRawQueryState({
        search: next.search,
        where: next.where === undefined ? undefined : next.where === null ? null : serializeRsqlExpression(next.where),
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
  // Track URL values sent to controlled features until their matching serialized echo arrives.
  // This prevents the reverse effect from writing the previous value in between.
  const restoring = useRef<RestoringState>({});
  featuresRef.current = features;
  const queryFilterState = useMemo(
    () => ({ search: queryState.search ?? "", expression: queryState.where }),
    [queryState.search, queryState.where],
  );
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
    let columns: typeof queryState.columns | undefined;
    let sort: typeof queryState.sort | undefined;

    if (featureFilters !== null) {
      if (restoring.current.filters !== undefined) {
        if (restoring.current.filters === featureFilters) delete restoring.current.filters;
      } else if (current.filtering !== false && featureFilters !== queryFilters) {
        search = current.filtering.value.search || null;
        where = current.filtering.value.expression;
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
    if (search !== undefined || where !== undefined || columns !== undefined || sort !== undefined) {
      void setQueryState({ search, where, columns, sort });
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
}
