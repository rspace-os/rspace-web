import { parse } from "@rsql/parser";
import { and, cmp, eq, escapeValue, inList, ne, Operation, or, outList } from "rsql-builder";
import type {
  FilterOperator,
  ResolvedCollectionConfig,
  ResolvedFieldConfig,
} from "@/modules/common/collection/collectionConfig";
import type { FilterExpression } from "../tableListState";
import { normalizeRsqlList, normalizeRsqlScalar } from "./filterValue";

const tokens = {
  equals: "==",
  notEquals: "!=",
  greaterThan: "=gt=",
  greaterThanOrEqual: "=ge=",
  lessThan: "=lt=",
  lessThanOrEqual: "=le=",
  in: "=in=",
  notIn: "=out=",
  contains: "=contains=",
  matches: "=like=",
  exists: "=exists=",
} as const satisfies Record<FilterOperator, string>;

const operators = new Map<string, FilterOperator>([
  ["==", "equals"],
  ["!=", "notEquals"],
  [">", "greaterThan"],
  ["=gt=", "greaterThan"],
  [">=", "greaterThanOrEqual"],
  ["=ge=", "greaterThanOrEqual"],
  ["<", "lessThan"],
  ["=lt=", "lessThan"],
  ["<=", "lessThanOrEqual"],
  ["=le=", "lessThanOrEqual"],
  ["=in=", "in"],
  ["=out=", "notIn"],
  ["=contains=", "contains"],
  ["=like=", "matches"],
  ["=exists=", "exists"],
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

function typedScalar<TDocument>(
  value: string,
  field: ResolvedFieldConfig<TDocument>,
  operator: FilterOperator,
): string | number | boolean | Date | null {
  if (operator === "exists") {
    if (value === "true") return true;
    if (value === "false") return false;
    return null;
  }
  if (field.type === "number") {
    if (value.trim() === "") return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  if (field.type === "boolean") {
    if (value === "true") return true;
    if (value === "false") return false;
    return null;
  }
  if (field.type === "dateTime") {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  return value;
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
    if (values.some((value) => value === null)) return null;
    return {
      kind: "comparison",
      field: field.name,
      operator,
      value: values.filter((value): value is string | number | boolean | Date => value !== null),
    };
  }
  if (Array.isArray(rawValue)) return null;
  const value = typedScalar(rawValue, field, operator);
  return value === null ? null : { kind: "comparison", field: field.name, operator, value };
}

function parseNode<TDocument>(
  node: ParsedNode,
  config: ResolvedCollectionConfig<TDocument>,
  depth = 0,
): FilterExpression<TDocument> | null {
  if (depth > 20) return null;
  if (node.type === "COMPARISON") return comparison(node, config);
  const kind = node.operator === ";" || node.operator === "and" ? "and" : "or";
  const left = parseNode(node.left, config, depth + 1);
  const right = parseNode(node.right, config, depth + 1);
  if (!left || !right) return null;
  const children = [left, right].flatMap((child) => (child.kind === kind ? child.children : [child]));
  return { kind, children };
}

export function parseRsqlExpression<TDocument>(
  serialized: string,
  config: ResolvedCollectionConfig<TDocument>,
): FilterExpression<TDocument> | null {
  try {
    return parseNode(parse(serialized), config);
  } catch {
    return null;
  }
}
