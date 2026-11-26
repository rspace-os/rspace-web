import { Temporal } from "@js-temporal/polyfill";
import type { ResolvedCollectionConfig, ResolvedFieldConfig, SearchSelector } from "@/modules/common/collection/config";
import type { FilterOperator } from "@/modules/common/collection/filterOperators";
import { instantToWire } from "@/modules/common/time/instant";
import { valueBehaviourOf } from "../fieldValueBehaviour";
import type { FilterExpression, FilterScalar, FilterValue } from "../types";

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

function draftValue<TDocument>(
  value: FilterValue,
  field: ResolvedFieldConfig<TDocument> | undefined,
): string | readonly string[] {
  const { toDraft } = valueBehaviourOf(field).behaviour;
  const scalar = (item: FilterScalar) => {
    if (toDraft && (item instanceof Temporal.Instant || typeof item === "string")) return toDraft(item);
    return item instanceof Temporal.Instant ? instantToWire(item) : String(item);
  };
  return Array.isArray(value) ? value.map(scalar) : scalar(value as FilterScalar);
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
  const { fromDraft } = valueBehaviourOf(field).behaviour;
  const original = rule.original?.field === rule.field ? rule.original.value : undefined;
  const parse = (value: string, index = 0): FilterScalar => {
    const previous = Array.isArray(original) ? original[index] : (original as FilterScalar | undefined);
    return fromDraft?.(value, previous) ?? value;
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
    (valueBehaviourOf(field).behaviour.prefersEquals ||
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

export type DraftValue = string | readonly string[];

export function scalarValue(value: DraftValue): string {
  return typeof value === "string" ? value : (value[0] ?? "");
}

export function listValue(value: DraftValue): readonly string[] {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? [] : [trimmed];
}
