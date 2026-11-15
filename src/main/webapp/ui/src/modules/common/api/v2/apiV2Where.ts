import { and, cmp, eq, escapeValue, inList, ne, Operation, or, outList } from "rsql-builder";
import type { ApiV2FilterFields } from "./generated/filters";

/** A REST API v2 resource whose collection operations accept a typed `where`. */
export type ApiV2FilterResource = keyof ApiV2FilterFields;

declare const rsqlWhereBrand: unique symbol;
/**
 * An RSQL filter for one resource. Only `apiV2WhereToRsql` and code that has validated an expression
 * against the same filter metadata (the table-list adapter) may produce one; raw strings do not type-check.
 */
export type RsqlWhere<R extends ApiV2FilterResource> = string & { readonly [rsqlWhereBrand]: R };

/** RSQL operator token for each operator name accepted in a where object. */
export const apiV2WhereOperators = {
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
} as const;

export type ApiV2WhereOperator = keyof typeof apiV2WhereOperators;
type Token = (typeof apiV2WhereOperators)[ApiV2WhereOperator];
type Selector = { operators: Token; value: unknown };
type OperatorsFor<T extends Token> = {
  [K in ApiV2WhereOperator]: (typeof apiV2WhereOperators)[K] extends T ? K : never;
}[ApiV2WhereOperator];
type OperandFor<K extends ApiV2WhereOperator, V> = K extends "in" | "notIn"
  ? readonly [V, ...V[]]
  : K extends "exists"
    ? boolean
    : V;
type Conditions<S extends Selector> = { [K in OperatorsFor<S["operators"]>]?: OperandFor<K, S["value"]> };
type Condition<S extends Selector> = ("==" extends S["operators"] ? S["value"] : never) | Conditions<S>;

/**
 * A typed filter object for one resource. Each key is a selector with either a bare value (equals) or an
 * object of operators that selector allows; `and` and `or` combine nested objects. Keys combine with AND.
 *
 * @example
 * const where: ApiV2WhereObject<"Users"> = {
 *   or: [{ email: { contains: "smith" } }, { lastName: { matches: "Sm*" } }],
 *   createdAt: { greaterThanOrEqual: new Date("2026-01-01") },
 * };
 */
export type ApiV2WhereObject<R extends ApiV2FilterResource> = {
  [K in keyof ApiV2FilterFields[R]]?: ApiV2FilterFields[R][K] extends Selector
    ? Condition<ApiV2FilterFields[R][K]>
    : never;
} & {
  and?: readonly ApiV2WhereObject<R>[];
  or?: readonly ApiV2WhereObject<R>[];
};

/** What generated collection operations accept as `where`: a typed object or an already built filter. */
export type ApiV2Where<R extends ApiV2FilterResource> = ApiV2WhereObject<R> | RsqlWhere<R>;

type Scalar = string | number | boolean | Date;
const isPlainObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value) && !(value instanceof Date);
const scalar = (value: unknown, location: string): string | number | boolean => {
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  throw new TypeError(`${location}: Unsupported filter value ${String(value)}`);
};

function comparison(selector: string, operator: string, value: unknown): string {
  const location = `where.${selector}.${operator}`;
  if (!Object.hasOwn(apiV2WhereOperators, operator)) throw new TypeError(`${location}: Unknown operator`);
  if (operator === "in" || operator === "notIn") {
    if (!Array.isArray(value) || value.length === 0) throw new TypeError(`${location}: Expected a non-empty list`);
    const values = value.map((item: Scalar) => scalar(item, location));
    return cmp(selector, operator === "in" ? inList(...values) : outList(...values)).toString();
  }
  const operand = scalar(value, location);
  if (operator === "equals") return cmp(selector, eq(operand)).toString();
  if (operator === "notEquals") return cmp(selector, ne(operand)).toString();
  const token = apiV2WhereOperators[operator as ApiV2WhereOperator];
  return cmp(selector, new Operation(escapeValue(operand), token)).toString();
}

function terms(where: Record<string, unknown>): string[] {
  const result: string[] = [];
  for (const [key, condition] of Object.entries(where)) {
    if (condition === undefined) continue;
    if (key === "and" || key === "or") {
      if (!Array.isArray(condition) || condition.length === 0)
        throw new TypeError(`where.${key}: Expected a non-empty list`);
      const children = condition.map((child) => group(child, `where.${key}`));
      result.push(children.length === 1 ? children[0] : `(${(key === "and" ? and : or)(...children)})`);
    } else if (isPlainObject(condition)) {
      const operators = Object.entries(condition).filter(([, value]) => value !== undefined);
      if (operators.length === 0) throw new TypeError(`where.${key}: Expected at least one operator`);
      for (const [operator, value] of operators) result.push(comparison(key, operator, value));
    } else {
      result.push(comparison(key, "equals", condition));
    }
  }
  return result;
}

function group(where: unknown, location: string): string {
  if (!isPlainObject(where)) throw new TypeError(`${location}: Expected a where object`);
  const parts = terms(where);
  if (parts.length === 0) throw new TypeError(`${location}: Empty where object`);
  return parts.length === 1 ? parts[0] : `(${and(...parts)})`;
}

/**
 * Converts a typed where object to RSQL. Returns an empty string for an object with no conditions, which
 * means "no filter". Throws on a value the RSQL grammar cannot carry; the server validates selectors and
 * operators again.
 */
export function apiV2WhereToRsql<R extends ApiV2FilterResource>(where: ApiV2WhereObject<R>): RsqlWhere<R> {
  return and(...terms(where as Record<string, unknown>)).toString() as RsqlWhere<R>;
}
