import { format } from "date-fns";
import type {
  FilterOperator,
  ResolvedCollectionConfig,
  ResolvedFieldConfig,
  SearchSelector,
} from "@/modules/common/collection/config";
import type { FilterExpression, FilterValue } from "../types";

export type DraftRule<TDocument> = {
  id: number;
  field: SearchSelector<TDocument>;
  operator: FilterOperator;
  value: DraftValue;
  original?: { field: SearchSelector<TDocument>; value: FilterValue };
};

export function comparisonNodes<TDocument>(
  expression: FilterExpression<TDocument> | null,
  config: ResolvedCollectionConfig<TDocument>,
): readonly DraftRule<TDocument>[] {
  if (!expression) return [];
  const nodes = expression.kind === "and" ? expression.children : [expression];
  return nodes.flatMap((node, index) =>
    node.kind === "comparison"
      ? [
          {
            id: index + 1,
            field: node.field,
            operator: node.operator,
            value: draftValue(
              node.value,
              config.fields.find((field) => field.name === node.field),
            ),
            original: { field: node.field, value: node.value },
          },
        ]
      : [],
  );
}

const DATE_TIME_LOCAL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

function draftValue<TDocument>(
  value: FilterValue,
  field: ResolvedFieldConfig<TDocument> | undefined,
): string | readonly string[] {
  const scalar = (item: string | number | boolean | Date) => {
    if (field?.type === "dateTime" && (item instanceof Date || typeof item === "string")) {
      return format(new Date(item), DATE_TIME_LOCAL_FORMAT);
    }
    return item instanceof Date ? item.toISOString() : String(item);
  };
  return Array.isArray(value) ? value.map(scalar) : scalar(value as string | number | boolean | Date);
}

export function valueForOperator(
  value: DraftRule<never>["value"],
  previous: FilterOperator,
  next: FilterOperator,
): DraftRule<never>["value"] {
  const previousUsesMultipleValues = previous === "in" || previous === "notIn";
  const nextUsesMultipleValues = next === "in" || next === "notIn";
  if (next === "exists") return "true";
  if (previous === "exists") return nextUsesMultipleValues ? [] : "";
  if (nextUsesMultipleValues && !previousUsesMultipleValues) {
    const current = scalarValue(value);
    return current.trim() === "" ? [] : [current];
  }
  if (!nextUsesMultipleValues && previousUsesMultipleValues) return scalarValue(value);
  return value;
}

export function parsedValue<TDocument>(rule: DraftRule<TDocument>, field: ResolvedFieldConfig<TDocument>) {
  if (rule.operator === "exists") return scalarValue(rule.value) !== "false";
  const parse = (value: string, index = 0): string | number | boolean | Date => {
    if (field.type === "number") return Number(value);
    if (field.type === "boolean") return value === "true";
    if (field.type === "dateTime") {
      const original = rule.original?.field === rule.field ? rule.original.value : undefined;
      const previous = Array.isArray(original) ? original[index] : original;
      const instant = previous instanceof Date ? previous : typeof previous === "string" ? new Date(previous) : null;
      // A local clock value cannot distinguish the two instants in a DST overlap.
      return instant && draftValue(instant, field) === value ? instant : new Date(value);
    }
    return value;
  };
  if (rule.operator === "in" || rule.operator === "notIn") {
    return listValue(rule.value).map(parse);
  }
  return parse(scalarValue(rule.value).trim());
}

const CUSTOM_FIELD_SENTINEL = "\u0000customField";

export function sentinelFor(namespace: string): string {
  return `${CUSTOM_FIELD_SENTINEL}:${namespace}`;
}

export function namespaceForSentinel(value: string): string | null {
  return value.startsWith(`${CUSTOM_FIELD_SENTINEL}:`) ? value.slice(CUSTOM_FIELD_SENTINEL.length + 1) : null;
}

export function isActive<TDocument>(rule: DraftRule<TDocument>): boolean {
  if (rule.operator === "in" || rule.operator === "notIn") {
    return listValue(rule.value).length > 0;
  }
  return rule.operator === "exists" || scalarValue(rule.value).trim() !== "";
}

export function defaultOperator<TDocument>(field: ResolvedFieldConfig<TDocument>): FilterOperator {
  if (
    (field.type === "select" ||
      field.type === "relationship" ||
      field.filterPicker ||
      field.origin?.kind === "relationshipTarget") &&
    field.capabilities.filterOperators.includes("equals")
  )
    return "equals";
  return field.capabilities.filterOperators.includes("contains")
    ? "contains"
    : (field.capabilities.filterOperators[0] ?? "equals");
}

export function topLevelFilterCount<TDocument>(expression: FilterExpression<TDocument> | null): number {
  if (!expression) return 0;
  return expression.kind === "and" ? expression.children.length : 1;
}

export function fieldGroups<TDocument>(
  fields: readonly ResolvedFieldConfig<TDocument>[],
): readonly { key: string; labelKey: string | null; fields: readonly ResolvedFieldConfig<TDocument>[] }[] {
  const groups = new Map<string, { key: string; labelKey: string | null; fields: ResolvedFieldConfig<TDocument>[] }>();
  for (const field of fields) {
    const labelKey = field.origin?.viaLabel || field.origin?.groupLabelKey || null;
    const key = labelKey ?? "";
    const group = groups.get(key) ?? { key, labelKey, fields: [] };
    group.fields.push(field);
    groups.set(key, group);
  }
  return [...groups.values()];
}

export type DraftValue = string | readonly string[];

export function scalarValue(value: DraftValue): string {
  return typeof value === "string" ? value : (value[0] ?? "");
}

export function listValue(value: DraftValue): readonly string[] {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? [] : [trimmed];
}
