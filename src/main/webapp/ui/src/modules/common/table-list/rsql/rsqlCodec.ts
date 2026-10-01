import { parse } from "@rsql/parser";
import { and, cmp, eq, escapeValue, inList, ne, Operation, or, outList } from "rsql-builder";
import * as v from "valibot";
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
  ...Object.entries(tokens).map(([operator, token]) => [token, operator as FilterOperator] as const),
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

const stringScalar = v.string();
const booleanScalar = v.pipe(
  v.picklist(["true", "false"]),
  v.transform((value) => value === "true"),
);
const numberScalar = v.pipe(
  v.string(),
  v.transform((value) => (value.trim() === "" ? Number.NaN : Number(value))),
  v.finite(),
);
const dateTimeScalar = v.pipe(
  v.string(),
  v.transform((value) => new Date(value)),
  v.check((value) => !Number.isNaN(value.getTime())),
);

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

function parseNode<TDocument>(
  node: ParsedNode,
  config: ResolvedCollectionConfig<TDocument>,
  stale: StaleFieldPolicy | undefined,
  depth = 0,
): FilterExpression<TDocument> | null | "dropped" {
  if (depth > 20) return null;
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
  const kind = node.operator === ";" || node.operator === "and" ? "and" : "or";
  const left = parseNode(node.left, config, stale, depth + 1);
  const right = parseNode(node.right, config, stale, depth + 1);
  if (left === null || right === null) return null;
  if (left === "dropped" && right === "dropped") return "dropped";
  if (left === "dropped") return right;
  if (right === "dropped") return left;
  const children = [left, right].flatMap((child) => (child.kind === kind ? child.children : [child]));
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
  const selectors: string[] = [];
  const visit = (node: ParsedNode, depth: number): void => {
    if (depth > 20) return;
    if (node.type === "COMPARISON") {
      selectors.push(node.left.selector);
      return;
    }
    visit(node.left, depth + 1);
    visit(node.right, depth + 1);
  };
  const parsed = safeParse(serialized);
  if (parsed === null) return [];
  visit(parsed, 0);
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
