import { Temporal } from "@js-temporal/polyfill";
import { isEqual } from "es-toolkit";
import type { FilterExpression, FilterScalar, FilterValue } from "./types";
import { allOf } from "./utils/expressions";

type Rule<T> = Extract<FilterExpression<T>, { kind: "comparison" }>;

function scalarEquals(left: FilterScalar, right: FilterScalar): boolean {
  if (left instanceof Temporal.Instant && right instanceof Temporal.Instant)
    return Temporal.Instant.compare(left, right) === 0;
  return isEqual(left, right);
}

/** Whether two filter operands are the same: instants by time, lists element by element, other values by value. */
export function filterValueEquals(left: FilterValue, right: FilterValue): boolean {
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) return false;
    return left.every((item, index) => scalarEquals(item, right[index]));
  }
  return scalarEquals(left as FilterScalar, right as FilterScalar);
}

function conjuncts<T>(expression: FilterExpression<T> | null): readonly FilterExpression<T>[] {
  if (expression === null) return [];
  return expression.kind === "and" ? expression.children.flatMap((child) => conjuncts(child)) : [expression];
}

function matches<T>(expression: FilterExpression<T>, rule: Rule<T>) {
  return (
    expression.kind === "comparison" &&
    expression.field === rule.field &&
    expression.operator === rule.operator &&
    filterValueEquals(expression.value, rule.value)
  );
}

/** Presets inspect only top-level AND conjuncts and leave OR subtrees intact. */
export function hasPreset<T>(expression: FilterExpression<T> | null, rule: Rule<T>): boolean {
  return conjuncts(expression).some((child) => matches(child, rule));
}

export function withoutPresets<T>(
  expression: FilterExpression<T> | null,
  rules: readonly Rule<T>[],
): FilterExpression<T> | null {
  return allOf(conjuncts(expression).filter((child) => !rules.some((rule) => matches(child, rule))));
}

export function togglePreset<T>(expression: FilterExpression<T> | null, rule: Rule<T>): FilterExpression<T> | null {
  return hasPreset(expression, rule) ? withoutPresets(expression, [rule]) : allOf([...conjuncts(expression), rule]);
}

/** Recovery on load may remove invalid values anywhere, including inside OR groups. */
export function dropInvalidMeRules<T>(expression: FilterExpression<T> | null, fields: readonly string[]) {
  const dropped: string[] = [];
  function visit(node: FilterExpression<T> | null): FilterExpression<T> | null {
    if (node === null) return null;
    if (node.kind === "comparison") {
      if (fields.includes(String(node.field)) && node.value !== "me") {
        dropped.push(String(node.field));
        return null;
      }
      return node;
    }
    const children = node.children.flatMap((child) => {
      const retained = visit(child);
      return retained ? [retained] : [];
    });
    return children.length < 2 ? allOf(children) : { ...node, children };
  }
  return { expression: visit(expression), dropped };
}
