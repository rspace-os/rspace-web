import * as v from "valibot";
import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/config";
import { isRsqlSyntaxValid, parseRsqlExpression, type StaleFieldPolicy } from "../rsql";
import type { FilterExpression } from "../types";
import { parseColumns, parseSorting, serializeColumns } from "./queryString";

const tableViewStateSchema = v.object({
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

const storedTableViewStateSchema = v.pipe(v.string(), v.parseJson(), tableViewStateSchema);
const columnListSchema = v.pipe(v.string(), v.parseJson(), v.object({ fields: v.array(v.string()) }));

/** A table's view as the strings its URL parameters hold; `null` is an absent parameter. */
export type RawTableView = {
  search: string | null;
  where: string | null;
  columns: string | null;
  sort: string | null;
};

export const rawTableViewKeys = ["search", "where", "columns", "sort"] as const;

export const emptyRawTableView: RawTableView = { search: null, where: null, columns: null, sort: null };

/** An empty parameter counts as absent. */
const presentOrNull = (value: string | null | undefined): string | null => value || null;

const serializeColumnList = (fields: readonly string[]) => serializeColumns<Record<string, unknown>>(fields);

function columnList(serialized: string): readonly string[] | null {
  const result = v.safeParse(columnListSchema, serialized);
  return result.success ? result.output.fields : null;
}

/**
 * A saved column selection plus any default column added to the configuration since it was saved, each placed
 * after the default that precedes it. `null` when the result is the default columns.
 */
function mergedColumns(columns: string, defaults: string, defaultColumns: readonly string[]): string | null {
  const saved = columnList(columns);
  // A selection that is not a column list falls back to the default columns; the other fields are kept.
  if (saved === null) return null;
  const seenDefaults = columnList(defaults);
  const merged = [...saved];
  if (seenDefaults !== null) {
    const seen = new Set(seenDefaults);
    defaultColumns.forEach((column, index) => {
      if (seen.has(column) || merged.includes(column)) return;
      const previous = defaultColumns.slice(0, index).findLast((candidate) => merged.includes(candidate));
      merged.splice(previous === undefined ? 0 : merged.indexOf(previous) + 1, 0, column);
    });
  }
  const serialized = serializeColumnList(merged);
  return serialized === serializeColumnList(defaultColumns) ? null : serialized;
}

/**
 * The stored view as raw URL values, `"malformed"` when it cannot be restored, or `null` when nothing is stored.
 * Only syntax is checked: field names are resolved by the table once its runtime fields are known.
 */
export function readRawTableView(
  serialized: string | null,
  defaultColumns: readonly string[],
): RawTableView | "malformed" | null {
  if (serialized === null) return null;
  const result = v.safeParse(storedTableViewStateSchema, serialized);
  if (!result.success) return "malformed";
  const { search, where, columns, defaults, sort } = result.output;
  if (columns !== null && defaults === undefined) return "malformed";
  if (where && !isRsqlSyntaxValid(where)) return "malformed";
  return {
    search: presentOrNull(search),
    where: presentOrNull(where),
    columns: columns && defaults !== undefined ? mergedColumns(columns, defaults, defaultColumns) : null,
    sort: presentOrNull(sort),
  };
}

export function readStoredTableView(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function removeStoredTableView(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // Browser privacy settings must not prevent the table from loading.
  }
}

/** Stores the raw view, or removes the stored view when every value is empty. */
export function writeRawTableView(key: string, raw: RawTableView, defaultColumns: readonly string[]): void {
  const search = presentOrNull(raw.search);
  const where = presentOrNull(raw.where);
  const columns = presentOrNull(raw.columns);
  const sort = presentOrNull(raw.sort);
  if (search === null && where === null && columns === null && sort === null) {
    removeStoredTableView(key);
    return;
  }
  const state: v.InferOutput<typeof tableViewStateSchema> = {
    v: 1,
    search,
    where,
    columns,
    ...(columns === null ? {} : { defaults: serializeColumnList(defaultColumns) }),
    sort,
  };
  try {
    window.localStorage.setItem(key, JSON.stringify(state));
  } catch {
    // Browser privacy settings and storage quotas must not affect the table.
  }
}

/**
 * The URL parameters a nuqs reader sees for a parsed TanStack Router search object. Router JSON-decodes values
 * (`true` arrives as a boolean, the columns parameter as an object); this is the installed nuqs adapter's own
 * conversion back to strings, so every reader of the table parameters agrees with nuqs.
 */
export function searchParamsFromRouterSearch(search: unknown): URLSearchParams {
  const entries = typeof search === "object" && search !== null ? Object.entries(search) : [];
  return new URLSearchParams(
    entries.flatMap(([key, value]): [string, string][] => {
      if (Array.isArray(value)) return value.map((item) => [key, String(item)]);
      if (typeof value === "object" && value !== null) return [[key, JSON.stringify(value)]];
      return [[key, String(value)]];
    }),
  );
}

/** Each member of a raw view resolved against a configuration: `undefined` when absent, `null` when invalid. */
export type ParsedTableView<TDocument> = {
  search: string | undefined;
  where: FilterExpression<TDocument> | null | undefined;
  columns: readonly FieldName<TDocument>[] | null | undefined;
  sort: readonly SortRule<TDocument>[] | null | undefined;
};

export function parseRawTableView<TDocument>(
  raw: RawTableView,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: StaleFieldPolicy,
): ParsedTableView<TDocument> {
  return {
    search: raw.search ?? undefined,
    where: raw.where === null ? undefined : parseRsqlExpression(raw.where, config, stale),
    columns: raw.columns === null ? undefined : parseColumns(raw.columns, config, stale),
    sort: raw.sort === null ? undefined : parseSorting(raw.sort, config),
  };
}
