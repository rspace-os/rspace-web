import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/collectionConfig";
import { parseColumns, parseSorting, serializeColumns, serializeSorting } from "./queryStringState";
import { parseRsqlExpression, serializeRsqlExpression } from "./rsql/rsqlCodec";
import type { FilterExpression } from "./tableListState";

export type TableViewState = {
  v: 1;
  search: string | null;
  where: string | null;
  columns: string | null;
  sort: string | null;
};

export type TableViewValues<TDocument> = {
  search: string | null;
  where: FilterExpression<TDocument> | null;
  columns: readonly FieldName<TDocument>[];
  sort: readonly SortRule<TDocument>[];
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function serializeTableViewState<TDocument>(
  values: TableViewValues<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
): string | null {
  const state: TableViewState = {
    v: 1,
    search: values.search || null,
    where: values.where === null ? null : serializeRsqlExpression(values.where),
    columns:
      serializeColumns(values.columns) === serializeColumns(config.defaultColumns)
        ? null
        : serializeColumns(values.columns),
    sort:
      serializeSorting(values.sort) === serializeSorting(config.defaultSort ?? [])
        ? null
        : serializeSorting(values.sort),
  };
  return state.search === null && state.where === null && state.columns === null && state.sort === null
    ? null
    : JSON.stringify(state);
}

export function parseTableViewState<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
): TableViewValues<TDocument> | null {
  try {
    const state: unknown = JSON.parse(serialized);
    if (
      !isRecord(state) ||
      state.v !== 1 ||
      !(typeof state.search === "string" || state.search === null) ||
      !(typeof state.where === "string" || state.where === null) ||
      !(typeof state.columns === "string" || state.columns === null) ||
      !(typeof state.sort === "string" || state.sort === null)
    ) {
      return null;
    }
    return {
      search: state.search,
      where: state.where === null ? null : parseRsqlExpression(state.where, config),
      columns:
        state.columns === null ? config.defaultColumns : (parseColumns(state.columns, config) ?? config.defaultColumns),
      sort:
        state.sort === null
          ? (config.defaultSort ?? [])
          : (parseSorting(state.sort, config) ?? config.defaultSort ?? []),
    };
  } catch {
    return null;
  }
}
