import { parse } from "@rsql/parser";
import { and, cmp, eq, escapeValue, inList, ne, Operation, or, outList } from "rsql-builder";
import * as v from "valibot";
import type { FilterOperator, ResolvedCollectionConfig, ResolvedFieldConfig } from "@/modules/common/collection/config";
import { filterOperatorsByToken, filterOperatorTokens as tokens } from "@/modules/common/collection/schema";
import { booleanScalar, dateTimeScalar, numberScalar, stringScalar } from "./schema";
import type { FilterExpression, FilterValue } from "./types";

const operators = new Map<string, FilterOperator>([
  ...Object.entries(filterOperatorsByToken),
  // Accept symbolic aliases from URLs and saved state; serialization uses the canonical tokens above.
  [">", "greaterThan"],
  [">=", "greaterThanOrEqual"],
  ["<", "lessThan"],
  ["<=", "lessThanOrEqual"],
]);

type ParsedNode = ReturnType<typeof parse>;

export function rsqlOperatorToken(operator: FilterOperator): (typeof tokens)[FilterOperator] {
  return tokens[operator];
}

function serializeComparison<TDocument>(
  expression: Extract<FilterExpression<TDocument>, { kind: "comparison" }>,
): string {
  if (expression.operator === "in" || expression.operator === "notIn") {
    const values = normalizeRsqlList(expression.value);
    return cmp(expression.field, expression.operator === "in" ? inList(...values) : outList(...values)).toString();
  }
  const value = normalizeRsqlScalar(expression.value);
  if (expression.operator === "equals") return cmp(expression.field, eq(value)).toString();
  if (expression.operator === "notEquals") return cmp(expression.field, ne(value)).toString();
  return cmp(expression.field, new Operation(escapeValue(value), rsqlOperatorToken(expression.operator))).toString();
}

function serializeNode<TDocument>(expression: FilterExpression<TDocument>, isRoot: boolean): string {
  if (expression.kind === "comparison") return serializeComparison(expression);
  if (expression.children.length === 0) throw new Error("Logical filter groups must not be empty");
  const children = expression.children.map((child) => serializeNode(child, false));
  const serialized = expression.kind === "and" ? and(...children) : or(...children);
  return isRoot ? serialized.toString() : `(${serialized})`;
}

export function serializeRsqlExpression<TDocument>(expression: FilterExpression<TDocument>): string {
  return serializeNode(expression, true);
}

function parseScalar<TOutput>(schema: v.GenericSchema<string, TOutput>, value: string): TOutput | null {
  const result = v.safeParse(schema, value);
  return result.success ? result.output : null;
}

function typedScalar<TDocument>(
  value: string,
  field: ResolvedFieldConfig<TDocument>,
  operator: FilterOperator,
): string | number | boolean | Date | null {
  if (operator === "exists" || field.type === "boolean") return parseScalar(booleanScalar, value);
  if (field.type === "number") return parseScalar(numberScalar, value);
  if (field.type === "dateTime") return parseScalar(dateTimeScalar, value);
  return parseScalar(stringScalar, value);
}

function comparison<TDocument>(
  node: Extract<ParsedNode, { type: "COMPARISON" }>,
  config: ResolvedCollectionConfig<TDocument>,
): FilterExpression<TDocument> | null {
  const field = config.fields.find((candidate) => candidate.name === node.left.selector);
  const operator = operators.get(node.operator);
  if (!field || !operator || !field.capabilities.filterOperators.includes(operator)) return null;
  const rawValue = node.right.value;
  if (operator === "in" || operator === "notIn") {
    if (!Array.isArray(rawValue) || rawValue.length === 0) return null;
    const values = rawValue.map((value) => typedScalar(value, field, operator));
    if (!values.every((value): value is string | number | boolean | Date => value !== null)) return null;
    return {
      kind: "comparison",
      field: field.name,
      operator,
      value: values,
    };
  }
  if (Array.isArray(rawValue)) return null;
  const value = typedScalar(rawValue, field, operator);
  return value === null ? null : { kind: "comparison", field: field.name, operator, value };
}

export type StaleFieldPolicy = {
  isStale: (selector: string) => boolean;
  onDropped?: (selector: string) => void;
};

type LogicalKind = "and" | "or";

const maximumRsqlLogicalDepth = 20;

function rsqlLogicalKind(operator: string): LogicalKind {
  return operator === ";" || operator === "and" ? "and" : "or";
}

// @rsql/parser stores same-kind chains as nested binary nodes and removes group nodes.
function flattenSameKindChildren(node: ParsedNode, kind: LogicalKind): ParsedNode[] {
  const pending: ParsedNode[] = [node];
  const children: ParsedNode[] = [];
  while (pending.length > 0) {
    const child = pending.pop();
    if (child === undefined) continue;
    if (child.type !== "COMPARISON" && rsqlLogicalKind(child.operator) === kind) {
      pending.push(child.right, child.left);
    } else {
      children.push(child);
    }
  }
  return children;
}

function parseNode<TDocument>(
  node: ParsedNode,
  config: ResolvedCollectionConfig<TDocument>,
  stale: StaleFieldPolicy | undefined,
  depth = 1,
): FilterExpression<TDocument> | null | "dropped" {
  if (node.type === "COMPARISON") {
    const parsed = comparison(node, config);
    if (parsed !== null) return parsed;
    const selector = node.left.selector;
    if (stale?.isStale(selector)) {
      stale.onDropped?.(selector);
      return "dropped";
    }
    return null;
  }

  if (depth > maximumRsqlLogicalDepth) return null;
  const kind = rsqlLogicalKind(node.operator);
  const parsedChildren = flattenSameKindChildren(node, kind).map((child) => parseNode(child, config, stale, depth + 1));
  if (parsedChildren.some((child) => child === null)) return null;
  const retained = parsedChildren.filter(
    (child): child is FilterExpression<TDocument> => child !== null && child !== "dropped",
  );
  if (retained.length === 0) return "dropped";
  if (retained.length === 1) {
    const onlyChild = retained[0];
    if (onlyChild !== undefined) return onlyChild;
  }

  const children = retained.flatMap((child) => (child.kind === kind ? child.children : [child]));
  return { kind, children };
}

function safeParse(serialized: string): ParsedNode | null {
  try {
    return parse(serialized);
  } catch {
    return null;
  }
}

export function rsqlSelectors(serialized: string): readonly string[] {
  const parsed = safeParse(serialized);
  if (parsed === null) return [];

  const selectors: string[] = [];
  const pending: { node: ParsedNode; depth: number }[] = [{ node: parsed, depth: 1 }];

  while (pending.length > 0) {
    const visit = pending.pop();
    if (visit === undefined) continue;
    const { node, depth } = visit;
    if (node.type === "COMPARISON") {
      selectors.push(node.left.selector);
      continue;
    }

    if (depth > maximumRsqlLogicalDepth) return [];
    const kind = rsqlLogicalKind(node.operator);
    const children = flattenSameKindChildren(node, kind);
    for (let index = children.length - 1; index >= 0; index -= 1) {
      const child = children[index];
      if (child !== undefined) {
        pending.push({ node: child, depth: depth + 1 });
      }
    }
  }

  return selectors;
}

export function parseRsqlExpression<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
  stale?: StaleFieldPolicy,
): FilterExpression<TDocument> | null {
  const parsed = safeParse(serialized);
  if (parsed === null) return null;
  const expression = parseNode(parsed, config, stale);
  return expression === null || expression === "dropped" ? null : expression;
}

export type RsqlValue = string | number | boolean;

export function normalizeRsqlScalar(value: FilterValue): RsqlValue {
  if (Array.isArray(value)) throw new Error("Expected a scalar filter value");
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  throw new Error("Null filter values are not supported");
}

export function normalizeRsqlList(value: FilterValue): readonly RsqlValue[] {
  if (!Array.isArray(value) || value.length === 0) throw new Error("List filters require at least one value");
  return value.map((item) => (item instanceof Date ? item.toISOString() : item));
}
