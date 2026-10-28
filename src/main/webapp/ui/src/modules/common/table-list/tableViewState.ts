import * as v from "valibot";
import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/collectionConfig";
import { columnSelectors, parseColumns, parseSorting, serializeColumns, serializeSorting } from "./queryStringState";
import { parseRsqlExpression, type StaleFieldPolicy, serializeRsqlExpression } from "./rsql/rsqlCodec";
import type { FilterExpression } from "./tableListState";

export const tableViewStateSchema = v.object({
  v: v.literal(1),
  search: v.nullable(v.string()),
  where: v.nullable(v.string()),
  columns: v.nullable(v.string()),
  /**
   * The configured default columns when `columns` was saved. A default added to the configuration later is
   * missing from this list, so loading shows it instead of silently hiding it behind the saved selection.
   */
  defaults: v.optional(v.string()),
  sort: v.nullable(v.string()),
});

export type TableViewState = v.InferOutput<typeof tableViewStateSchema>;

export type TableViewValues<TDocument> = {
  search: string | null;
  where: FilterExpression<TDocument> | null;
  columns: readonly FieldName<TDocument>[];
  sort: readonly SortRule<TDocument>[];
};

export function serializeTableViewState<TDocument>(
  values: TableViewValues<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
): string | null {
  const columns =
    serializeColumns(values.columns) === serializeColumns(config.defaultColumns)
      ? null
      : serializeColumns(values.columns);
  const state: TableViewState = {
    v: 1,
    search: values.search || null,
    where: values.where === null ? null : serializeRsqlExpression(values.where),
    columns,
    ...(columns === null ? {} : { defaults: serializeColumns(config.defaultColumns) }),
    sort:
      serializeSorting(values.sort) === serializeSorting(config.defaultSort ?? [])
        ? null
        : serializeSorting(values.sort),
  };
  return state.search === null && state.where === null && state.columns === null && state.sort === null
    ? null
    : JSON.stringify(state);
}

export type StaleViewFields = StaleFieldPolicy;

export function parseTableViewState<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: StaleViewFields,
): TableViewValues<TDocument> | null {
  try {
    const result = v.safeParse(tableViewStateSchema, JSON.parse(serialized));
    if (!result.success) return null;
    const state = result.output;
    let columns = config.defaultColumns;
    if (state.columns !== null) {
      if (state.defaults === undefined) return null;
      columns = savedColumns(state.columns, state.defaults, config, stale);
    }
    return {
      search: state.search,
      where: state.where === null ? null : parseRsqlExpression(state.where, config, stale),
      columns,
      sort:
        state.sort === null
          ? (config.defaultSort ?? [])
          : (parseSorting(state.sort, config) ?? config.defaultSort ?? []),
    };
  } catch {
    return null;
  }
}

/**
 * A saved column selection plus any default column added to the configuration since it was saved, each placed
 * after the default that precedes it.
 */
function savedColumns<TDocument>(
  columns: string,
  defaults: string,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: StaleViewFields,
): readonly FieldName<TDocument>[] {
  const saved = parseColumns(columns, config, stale);
  if (saved === null) return config.defaultColumns;
  const seen = new Set<string>(columnSelectors(defaults));
  const merged = [...saved];
  config.defaultColumns.forEach((column, index) => {
    if (seen.has(column) || merged.includes(column)) return;
    const previous = config.defaultColumns.slice(0, index).findLast((candidate) => merged.includes(candidate));
    merged.splice(previous === undefined ? 0 : merged.indexOf(previous) + 1, 0, column);
  });
  return merged;
}
