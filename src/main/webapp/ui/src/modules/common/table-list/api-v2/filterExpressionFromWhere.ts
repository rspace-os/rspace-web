import { Temporal } from "@js-temporal/polyfill";
import { isPlainObject } from "es-toolkit";
import {
  type ApiV2FilterResource,
  type ApiV2WhereObject,
  type ApiV2WhereOperator,
  apiV2WhereOperators,
} from "@/modules/common/api/v2/apiV2Where";
import type { SearchSelector } from "@/modules/common/collection/config";
import type { FilterExpression, FilterScalar } from "../types";
import { allOf } from "../utils/expressions";

type Expression<TDocument> = FilterExpression<TDocument>;

const isOperator = (operator: string): operator is ApiV2WhereOperator => Object.hasOwn(apiV2WhereOperators, operator);

function filterValue(value: unknown, location: string): FilterScalar {
  if (
    value instanceof Temporal.Instant ||
    typeof value === "string" ||
    typeof value === "number" ||
    typeof value === "boolean"
  )
    return value;
  throw new TypeError(`${location}: Unsupported filter value ${String(value)}`);
}

function comparison<TDocument>(selector: string, operator: string, value: unknown): Expression<TDocument> {
  const location = `where.${selector}.${operator}`;
  if (!isOperator(operator)) throw new TypeError(`${location}: Unknown operator`);
  // A where object's keys are the resource's published selectors, which the document type does not list.
  const field = selector as SearchSelector<TDocument>;
  if (operator === "in" || operator === "notIn") {
    if (!Array.isArray(value) || value.length === 0) throw new TypeError(`${location}: Expected a non-empty list`);
    return { kind: "comparison", field, operator, value: value.map((item) => filterValue(item, location)) };
  }
  return { kind: "comparison", field, operator, value: filterValue(value, location) };
}

function terms<TDocument>(where: object): Expression<TDocument>[] {
  const result: Expression<TDocument>[] = [];
  const entries: [string, unknown][] = Object.entries(where);
  for (const [key, condition] of entries) {
    if (condition === undefined) continue;
    if (key === "and" || key === "or") {
      if (!Array.isArray(condition) || condition.length === 0)
        throw new TypeError(`where.${key}: Expected a non-empty list`);
      const children = condition.map((child) => group<TDocument>(child, `where.${key}`));
      result.push(children.length === 1 ? children[0] : { kind: key, children });
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

function group<TDocument>(where: unknown, location: string): Expression<TDocument> {
  if (!isPlainObject(where)) throw new TypeError(`${location}: Expected a where object`);
  const expression = allOf(terms<TDocument>(where));
  if (expression === null) throw new TypeError(`${location}: Empty where object`);
  return expression;
}

/**
 * The filter expression a typed where object stands for, following `apiV2WhereToRsql`: a bare value means equals,
 * several operators on one selector are ANDed, `undefined` entries are skipped and `and`/`or` must not be empty.
 * An object without conditions means no filter.
 */
export function filterExpressionFromWhere<TDocument, R extends ApiV2FilterResource>(
  where: ApiV2WhereObject<R>,
): Expression<TDocument> | undefined {
  return allOf(terms<TDocument>(where)) ?? undefined;
}

/**
 * Tells a filter expression from a where object. A where object could only match by naming selectors `kind` and
 * `children`, or `kind`, `field`, `operator` and `value`, with an expression's values.
 */
export function isFilterExpression<TDocument, R extends ApiV2FilterResource>(
  filter: Expression<TDocument> | ApiV2WhereObject<R>,
): filter is Expression<TDocument> {
  if (!("kind" in filter)) return false;
  const { kind } = filter;
  if (kind === "and" || kind === "or") return "children" in filter && Array.isArray(filter.children);
  return kind === "comparison" && "field" in filter && "operator" in filter && "value" in filter;
}
