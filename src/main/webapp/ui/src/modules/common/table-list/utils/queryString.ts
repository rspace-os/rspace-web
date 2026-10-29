import * as v from "valibot";
import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/config";
import type { FilterExpression, FilterState, FilterValue } from "../types";

type SerializedDate = { readonly $date: string };

const columnSelectorsSchema = v.union([v.array(v.unknown()), v.object({ fields: v.array(v.unknown()) })]);
const columnsSchema = v.union([v.array(v.string()), v.object({ fields: v.array(v.string()) })]);

function serializeValue(value: FilterValue): unknown {
  if (value instanceof Date) return { $date: value.toISOString() } satisfies SerializedDate;
  if (Array.isArray(value)) return value.map(serializeValue);
  return value;
}

function serializeExpression<TDocument>(expression: FilterExpression<TDocument>): unknown {
  if (expression.kind === "comparison") {
    return { ...expression, value: serializeValue(expression.value) };
  }
  return { kind: expression.kind, children: expression.children.map(serializeExpression) };
}

export function serializeFilters<TDocument>(filters: FilterState<TDocument>): string {
  return JSON.stringify({
    search: filters.search,
    expression: filters.expression ? serializeExpression(filters.expression) : null,
  });
}

export function serializeColumns<TDocument>(columns: readonly FieldName<TDocument>[]): string {
  // TanStack Router treats a top-level JSON array as repeated query parameters.
  // The object keeps the column list as one value for the nuqs adapter.
  return JSON.stringify({ fields: columns });
}

export function parseColumns<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: { isStale: (name: string) => boolean; onDropped?: (name: string) => void },
): readonly FieldName<TDocument>[] | null {
  try {
    const serializedValue: unknown = JSON.parse(serialized);
    const result = v.safeParse(columnsSchema, serializedValue);
    if (!result.success) return null;
    const raw = Array.isArray(result.output) ? result.output : result.output.fields;
    const listableFields = new Set(config.fields.filter((field) => field.list !== false).map((field) => field.name));
    let dropped = false;
    const value = stale
      ? raw.filter((field) => {
          if (listableFields.has(field as FieldName<TDocument>) || !stale.isStale(field)) return true;
          stale.onDropped?.(field);
          dropped = true;
          return false;
        })
      : raw;
    if (dropped && value.length === 0) return null;
    if (
      new Set(value).size !== value.length ||
      !value.every((field) => listableFields.has(field as FieldName<TDocument>))
    ) {
      return null;
    }
    return value as FieldName<TDocument>[];
  } catch {
    return null;
  }
}

export function serializeSorting<TDocument>(sorting: readonly SortRule<TDocument>[]): string {
  return sorting.map((rule) => `${rule.direction === "desc" ? "-" : ""}${rule.field}`).join(",");
}

export function parseSorting<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
): readonly SortRule<TDocument>[] | null {
  if (serialized === "") return [];
  const values = serialized.split(",");
  const sortableFields = new Set(
    config.fields.filter((field) => field.capabilities.sortable).map((field) => field.name),
  );
  const rules = values.map(sortRuleFromToken<TDocument>);
  if (
    rules.some((rule) => rule.field === "" || !sortableFields.has(rule.field)) ||
    new Set(rules.map((rule) => rule.field)).size !== rules.length
  ) {
    return null;
  }
  return rules;
}

export function sortRuleFromToken<TDocument>(value: string): SortRule<TDocument> {
  return {
    field: (value.startsWith("-") ? value.slice(1) : value) as FieldName<TDocument>,
    direction: value.startsWith("-") ? "desc" : "asc",
  };
}

export function columnSelectors(serialized: string): readonly string[] {
  try {
    const value: unknown = JSON.parse(serialized);
    const result = v.safeParse(columnSelectorsSchema, value);
    if (!result.success) return [];
    const raw = Array.isArray(result.output) ? result.output : result.output.fields;
    return raw.filter((name): name is string => typeof name === "string");
  } catch {
    return [];
  }
}
