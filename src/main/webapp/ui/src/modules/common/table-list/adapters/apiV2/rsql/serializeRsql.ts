import { normalizeRsqlList, normalizeRsqlScalar } from "../../../rsql/filterValue";
import { rsqlOperatorToken, serializeRsqlExpression } from "../../../rsql/rsqlCodec";
import type { FilterExpression } from "../../../tableListState";
import type { ApiV2FilterLimits, ApiV2FilterOperator } from "../apiV2CollectionMetadata";

type SelectorContract = Readonly<
  Record<string, { operators: readonly ApiV2FilterOperator[]; wildcards: boolean } | undefined>
>;

type Counts = { comparisons: number; likeComparisons: number; arguments: number };

function validateWildcard(value: string | number | boolean, allowed: boolean, field: string): void {
  if (!allowed && typeof value === "string" && value.includes("*")) {
    throw new Error(`Wildcards are not supported for field ${field}`);
  }
}

function validateComparison<TDocument>(
  node: Extract<FilterExpression<TDocument>, { kind: "comparison" }>,
  selectors: SelectorContract,
  counts: Counts,
): void {
  const selector = selectors[node.field];
  if (!selector) throw new Error(`Field is not filterable: ${node.field}`);
  const token: ApiV2FilterOperator = rsqlOperatorToken(node.operator);
  if (!selector.operators.includes(token))
    throw new Error(`Operator ${token} is not supported for field ${node.field}`);

  counts.comparisons += 1;
  if (node.operator === "contains" || node.operator === "matches") counts.likeComparisons += 1;

  if (node.operator === "in" || node.operator === "notIn") {
    const args = normalizeRsqlList(node.value);
    for (const value of args) validateWildcard(value, selector.wildcards, node.field);
    counts.arguments += args.length;
    return;
  }

  const value = normalizeRsqlScalar(node.value);
  validateWildcard(value, selector.wildcards, node.field);
  counts.arguments += 1;
}

function validateNode<TDocument>(
  node: FilterExpression<TDocument>,
  selectors: SelectorContract,
  limits: ApiV2FilterLimits,
  counts: Counts,
  depth: number,
): void {
  if (depth > limits.maximumNesting) throw new Error("Filter nesting limit exceeded");
  if (node.kind === "comparison") {
    validateComparison(node, selectors, counts);
    return;
  }
  if (node.children.length === 0) throw new Error("Logical filter groups must not be empty");
  for (const child of node.children) validateNode(child, selectors, limits, counts, depth + 1);
}

export function serializeRsql<TDocument>(
  expression: FilterExpression<TDocument>,
  selectors: SelectorContract,
  limits: ApiV2FilterLimits,
): string {
  const counts: Counts = { comparisons: 0, likeComparisons: 0, arguments: 0 };
  validateNode(expression, selectors, limits, counts, 1);
  if (counts.comparisons > limits.maximumComparisons) throw new Error("Filter comparison limit exceeded");
  if (counts.likeComparisons > limits.maximumLikeComparisons) throw new Error("Pattern comparison limit exceeded");
  if (counts.arguments > limits.maximumArguments) throw new Error("Filter argument limit exceeded");
  const result = serializeRsqlExpression(expression);
  if (result.length > limits.maximumWhereLength || encodeURIComponent(result).length > limits.maximumWhereLength) {
    throw new Error("Filter length limit exceeded");
  }
  return result;
}
