import type { FilterExpression } from "./types";

type Rule<T> = Extract<FilterExpression<T>, { kind: "comparison" }>;

function conjuncts<T>(expression: FilterExpression<T> | null): readonly FilterExpression<T>[] {
  if (expression === null) return [];
  return expression.kind === "and" ? expression.children.flatMap((child) => conjuncts(child)) : [expression];
}

function matches<T>(expression: FilterExpression<T>, rule: Rule<T>) {
  return (
    expression.kind === "comparison" &&
    expression.field === rule.field &&
    expression.operator === rule.operator &&
    JSON.stringify(expression.value) === JSON.stringify(rule.value)
  );
}

function combine<T>(children: readonly FilterExpression<T>[]): FilterExpression<T> | null {
  return children.length === 0 ? null : children.length === 1 ? children[0] : { kind: "and", children };
}

/** Presets inspect only top-level AND conjuncts and leave OR subtrees intact. */
export function hasPreset<T>(expression: FilterExpression<T> | null, rule: Rule<T>): boolean {
  return conjuncts(expression).some((child) => matches(child, rule));
}

export function withoutPresets<T>(
  expression: FilterExpression<T> | null,
  rules: readonly Rule<T>[],
): FilterExpression<T> | null {
  return combine(conjuncts(expression).filter((child) => !rules.some((rule) => matches(child, rule))));
}

export function togglePreset<T>(expression: FilterExpression<T> | null, rule: Rule<T>): FilterExpression<T> | null {
  return hasPreset(expression, rule) ? withoutPresets(expression, [rule]) : combine([...conjuncts(expression), rule]);
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
    return children.length < 2 ? combine(children) : { ...node, children };
  }
  return { expression: visit(expression), dropped };
}
