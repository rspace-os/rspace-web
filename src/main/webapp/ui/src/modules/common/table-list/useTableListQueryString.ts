import { createParser, parseAsString, useQueryStates } from "nuqs";
import { useEffect, useMemo, useRef, useState } from "react";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import {
  parseColumns,
  parseFilters,
  parseSorting,
  serializeColumns,
  serializeFilters,
  serializeSorting,
} from "./queryStringState";
import { parseRsqlExpression, serializeRsqlExpression } from "./rsql/rsqlCodec";
import type { TableListFeatures, TableListQueryStringOptions } from "./tableListState";
import { loadStoredTableView, saveStoredTableView, tableViewStorageKey } from "./tableViewStorage";

const emptyFilters = { search: "", expression: null } as const;
const emptySorting = [] as const;

type RestoringState = Partial<Record<"filters" | "columns" | "sorting", string>>;

function parameterNames(slug: string, options: true | TableListQueryStringOptions) {
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

export function useTableListQueryString<TDocument>(
  config: ResolvedCollectionConfig<TDocument>,
  features: TableListFeatures<TDocument>,
  options: true | TableListQueryStringOptions,
): TableListFeatures<TDocument> {
  const names = parameterNames(config.slug, options);
  const configuredTableId = typeof options === "object" ? options.tableId?.trim() : undefined;
  const storageKey = tableViewStorageKey(configuredTableId || config.slug);
  const defaultSort = config.defaultSort ?? emptySorting;
  const parsers = useMemo(
    () => ({
      search: parseAsString,
      where: createParser({
        parse: (value) => parseRsqlExpression(value, config),
        serialize: serializeRsqlExpression,
        eq: (left, right) => serializeRsqlExpression(left) === serializeRsqlExpression(right),
      }),
      legacyFilters: createParser({
        parse: (value) => parseFilters(value, config),
        serialize: serializeFilters,
        eq: (left, right) => serializeFilters(left) === serializeFilters(right),
      }),
      columns: createParser({
        parse: (value) => parseColumns(value, config),
        serialize: serializeColumns,
        eq: (left, right) => serializeColumns(left) === serializeColumns(right),
      }).withDefault(config.defaultColumns),
      sort: createParser({
        parse: (value) => parseSorting(value, config),
        serialize: serializeSorting,
        eq: (left, right) => serializeSorting(left) === serializeSorting(right),
      }).withDefault(defaultSort),
    }),
    [config, defaultSort],
  );
  const [queryState, setQueryState] = useQueryStates(parsers, {
    history: "replace",
    urlKeys: {
      search: names.search,
      where: names.where,
      legacyFilters: names.legacyFilters,
      columns: names.columns,
      sort: names.sort,
    },
  });
  const urlOwnsInitialState = useRef(
    typeof window !== "undefined" &&
      Object.values(names).some((name) => new URLSearchParams(window.location.search).has(name)),
  );
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
    if (urlOwnsInitialState.current) return;
    let active = true;
    const restored = loadStoredTableView(storageKey, config);
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
  }, [config, defaultSort, setQueryState, storageKey]);

  useEffect(() => {
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
  }, [queryColumns, queryFilterState, queryFilters, querySorting, queryState.columns, queryState.sort]);

  useEffect(() => {
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
  }, [featureColumns, featureFilters, featureSorting, queryColumns, queryFilters, querySorting, setQueryState]);

  useEffect(() => {
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
    sortingEnabled,
  ]);

  return features;
}
