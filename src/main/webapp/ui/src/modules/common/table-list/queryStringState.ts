import type {
  FieldName,
  FilterOperator,
  ResolvedCollectionConfig,
  SortRule,
} from "@/modules/common/collection/collectionConfig";
import type { FilterExpression, FilterState, FilterValue } from "./tableListState";

type SerializedDate = { readonly $date: string };

const filterOperators = new Set<FilterOperator>([
  "equals",
  "notEquals",
  "greaterThan",
  "greaterThanOrEqual",
  "lessThan",
  "lessThanOrEqual",
  "in",
  "notIn",
  "contains",
  "matches",
  "exists",
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function serializeValue(value: FilterValue): unknown {
  if (value instanceof Date) return { $date: value.toISOString() } satisfies SerializedDate;
  if (Array.isArray(value)) return value.map(serializeValue);
  return value;
}

function parseValue(value: unknown): FilterValue | null {
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  if (Array.isArray(value)) {
    const parsed = value.map(parseValue);
    return parsed.every((item): item is FilterValue => item !== null) ? parsed.flat() : null;
  }
  if (isRecord(value) && typeof value.$date === "string") {
    const date = new Date(value.$date);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  return null;
}

function serializeExpression<TDocument>(expression: FilterExpression<TDocument>): unknown {
  if (expression.kind === "comparison") {
    return { ...expression, value: serializeValue(expression.value) };
  }
  return { kind: expression.kind, children: expression.children.map(serializeExpression) };
}

function parseExpression<TDocument>(
  value: unknown,
  config: ResolvedCollectionConfig<TDocument>,
  depth = 0,
): FilterExpression<TDocument> | null {
  if (!isRecord(value) || depth > 20) return null;
  if (value.kind === "and" || value.kind === "or") {
    if (!Array.isArray(value.children) || value.children.length === 0) return null;
    const children = value.children.map((child) => parseExpression(child, config, depth + 1));
    if (children.some((child) => child === null)) return null;
    return {
      kind: value.kind,
      children: children.filter((child): child is FilterExpression<TDocument> => child !== null),
    };
  }
  if (value.kind !== "comparison" || typeof value.field !== "string" || typeof value.operator !== "string") {
    return null;
  }
  const field = config.fields.find((candidate) => candidate.name === value.field);
  if (!field || !filterOperators.has(value.operator as FilterOperator)) return null;
  const operator = value.operator as FilterOperator;
  if (!field.capabilities.filterOperators.includes(operator)) return null;
  const parsedValue = parseValue(value.value);
  if (parsedValue === null) return null;
  return { kind: "comparison", field: field.name, operator, value: parsedValue };
}

export function serializeFilters<TDocument>(filters: FilterState<TDocument>): string {
  return JSON.stringify({
    search: filters.search,
    expression: filters.expression ? serializeExpression(filters.expression) : null,
  });
}

export function parseFilters<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
): FilterState<TDocument> | null {
  try {
    const value: unknown = JSON.parse(serialized);
    if (!isRecord(value) || typeof value.search !== "string") return null;
    if (value.expression === null) return { search: value.search, expression: null };
    const expression = parseExpression(value.expression, config);
    return expression ? { search: value.search, expression } : null;
  } catch {
    return null;
  }
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
    const raw = isRecord(serializedValue) ? serializedValue.fields : serializedValue;
    if (!Array.isArray(raw) || !raw.every((field) => typeof field === "string")) return null;
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
  const rules = values.map((value) => ({
    field: (value.startsWith("-") ? value.slice(1) : value) as FieldName<TDocument>,
    direction: value.startsWith("-") ? ("desc" as const) : ("asc" as const),
  }));
  if (
    rules.some((rule) => rule.field === "" || !sortableFields.has(rule.field)) ||
    new Set(rules.map((rule) => rule.field)).size !== rules.length
  ) {
    return null;
  }
  return rules;
}
