import { Temporal } from "@js-temporal/polyfill";
import type { ResolvedCollectionConfig, ResolvedFieldConfig, SearchSelector } from "@/modules/common/collection/config";
import type { FilterOperator } from "@/modules/common/collection/filterOperators";
import { instantToWire } from "@/modules/common/time/instant";
import { valueBehaviourOf } from "../fieldValueBehaviour";
import type { FilterExpression, FilterScalar, FilterValue } from "../types";
import { allOf } from "./expressions";

export type DraftRule<TDocument> = {
  id: number;
  field: SearchSelector<TDocument>;
  operator: FilterOperator;
  value: DraftValue;
  original?: { field: SearchSelector<TDocument>; value: FilterValue };
  /** The runtime-field group the user is choosing a definition from; the rule has no field until they pick one. */
  pickingGroup?: string;
};

/** The nodes the panel lists: an AND's children, or the expression itself. */
export function topLevelNodes<TDocument>(
  expression: FilterExpression<TDocument> | null,
): readonly FilterExpression<TDocument>[] {
  if (expression?.kind === "and") return expression.children;
  if (expression) return [expression];
  return [];
}

export function comparisonNodes<TDocument>(
  expression: FilterExpression<TDocument> | null,
  config: ResolvedCollectionConfig<TDocument>,
): readonly DraftRule<TDocument>[] {
  return topLevelNodes(expression).flatMap((node, index) =>
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

export function isActive<TDocument>(rule: DraftRule<TDocument>): boolean {
  if (rule.pickingGroup !== undefined) return false;
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
  return topLevelNodes(expression).length;
}

/** A blank rule for `field`, numbered after the existing rules. */
export function newDraftRule<TDocument>(
  rules: readonly DraftRule<TDocument>[],
  field: ResolvedFieldConfig<TDocument>,
): DraftRule<TDocument> {
  return {
    id: Math.max(0, ...rules.map((rule) => rule.id)) + 1,
    field: field.name,
    operator: defaultOperator(field),
    value: "",
  };
}

/** The filter the panel applies: its active rules as comparisons, then the nested groups it kept, all ANDed. */
export function draftExpression<TDocument>(
  rules: readonly DraftRule<TDocument>[],
  fields: readonly ResolvedFieldConfig<TDocument>[],
  savedGroups: readonly FilterExpression<TDocument>[],
): FilterExpression<TDocument> | null {
  const comparisons: FilterExpression<TDocument>[] = rules.filter(isActive).map((rule) => {
    const field = fields.find((candidate) => candidate.name === rule.field);
    if (!field) throw new Error(`Unknown filter field ${rule.field}`);
    return { kind: "comparison", field: rule.field, operator: rule.operator, value: parsedValue(rule, field) };
  });
  return allOf([...comparisons, ...savedGroups]);
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
