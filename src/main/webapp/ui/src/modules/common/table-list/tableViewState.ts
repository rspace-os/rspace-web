import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/collectionConfig";
import { parseColumns, parseSorting, serializeColumns, serializeSorting } from "./queryStringState";
import { parseRsqlExpression, serializeRsqlExpression } from "./rsql/rsqlCodec";
import type { FilterExpression } from "./tableListState";

export type TableViewState = {
  v: 1;
  search: string | null;
  where: string | null;
  columns: string | null;
  /**
   * The configured default columns when `columns` was saved. A default added to the configuration later is
   * missing from this list, so loading shows it instead of silently hiding it behind the saved selection.
   */
  defaults?: string;
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

export type StaleViewFields = {
  isStale: (name: string) => boolean;
  onDropped?: (name: string) => void;
};

export function parseTableViewState<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: StaleViewFields,
): TableViewValues<TDocument> | null {
  try {
    const state: unknown = JSON.parse(serialized);
    if (
      !isRecord(state) ||
      state.v !== 1 ||
      !(typeof state.search === "string" || state.search === null) ||
      !(typeof state.where === "string" || state.where === null) ||
      !(typeof state.columns === "string" || state.columns === null) ||
      !(state.defaults === undefined || typeof state.defaults === "string") ||
      !(typeof state.sort === "string" || state.sort === null)
    ) {
      return null;
    }
    return {
      search: state.search,
      where: state.where === null ? null : parseRsqlExpression(state.where, config, stale),
      columns: savedColumns(state.columns, state.defaults, config, stale),
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
 * after the default that precedes it. Views saved before `defaults` was recorded gain every missing default once.
 */
function savedColumns<TDocument>(
  columns: string | null,
  defaults: string | undefined,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: StaleViewFields,
): readonly FieldName<TDocument>[] {
  if (columns === null) return config.defaultColumns;
  const saved = parseColumns(columns, config, stale);
  if (saved === null) return config.defaultColumns;
  const seen = new Set<string>(defaults === undefined ? [] : columnNames(defaults));
  const merged = [...saved];
  config.defaultColumns.forEach((column, index) => {
    if (seen.has(column) || merged.includes(column)) return;
    const previous = config.defaultColumns.slice(0, index).findLast((candidate) => merged.includes(candidate));
    merged.splice(previous === undefined ? 0 : merged.indexOf(previous) + 1, 0, column);
  });
  return merged;
}

function columnNames(serialized: string): readonly string[] {
  try {
    const value: unknown = JSON.parse(serialized);
    const raw = isRecord(value) ? value.fields : value;
    return Array.isArray(raw) ? raw.filter((name): name is string => typeof name === "string") : [];
  } catch {
    return [];
  }
}
