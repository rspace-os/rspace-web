import { Temporal } from "@js-temporal/polyfill";
import { escapeRegExp, isNil, isNotNil } from "es-toolkit";
import type { SearchSelector } from "@/modules/common/collection/config";
import { parseInstant } from "@/modules/common/time/instant";
import { valueBehaviourOf } from "../fieldValueBehaviour";
import type { FilterExpression, FilterState, TableListProps } from "../types";

function comparable(value: unknown, dateTime = false): string | number | boolean {
  if (value instanceof Temporal.Instant) return value.epochMilliseconds;
  // A missing or unreadable date-time never compares, rather than reading as the empty string.
  if (dateTime) return typeof value === "string" ? (parseInstant(value)?.epochMilliseconds ?? Number.NaN) : Number.NaN;
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  return String(value ?? "");
}

function equal(left: unknown, right: unknown, language: string | undefined, dateTime = false): boolean {
  return !dateTime && typeof left === "string" && typeof right === "string"
    ? left.localeCompare(right, language, { sensitivity: "accent" }) === 0
    : comparable(left, dateTime) === comparable(right, dateTime);
}

function wildcardPattern(value: string): RegExp {
  const escaped = escapeRegExp(value).replaceAll("\\*", ".*");
  return new RegExp(`^${escaped}$`, "i");
}

function matchesExpression<TDocument extends Record<string, unknown>>(
  row: TDocument,
  expression: FilterExpression<TDocument>,
  config: TableListProps<TDocument>["config"],
  language: string | undefined,
): boolean {
  if (expression.kind !== "comparison") {
    return expression.kind === "and"
      ? expression.children.every((child) => matchesExpression(row, child, config, language))
      : expression.children.some((child) => matchesExpression(row, child, config, language));
  }

  const rowValue = row[String(expression.field)];
  const field = config.fields.find((candidate) => String(candidate.name) === String(expression.field));
  const dateTime = valueBehaviourOf(field).behaviour.comparesAsInstant;
  const filterValues = Array.isArray(expression.value) ? expression.value : [expression.value];
  switch (expression.operator) {
    case "equals":
      return equal(rowValue, expression.value, language, dateTime);
    case "notEquals":
      return !equal(rowValue, expression.value, language, dateTime);
    case "greaterThan":
      return comparable(rowValue, dateTime) > comparable(expression.value, dateTime);
    case "greaterThanOrEqual":
      return comparable(rowValue, dateTime) >= comparable(expression.value, dateTime);
    case "lessThan":
      return comparable(rowValue, dateTime) < comparable(expression.value, dateTime);
    case "lessThanOrEqual":
      return comparable(rowValue, dateTime) <= comparable(expression.value, dateTime);
    case "in":
      return filterValues.some((value) => equal(rowValue, value, language, dateTime));
    case "notIn":
      return filterValues.every((value) => !equal(rowValue, value, language, dateTime));
    case "contains":
      return String(rowValue ?? "")
        .toLocaleLowerCase(language)
        .includes(String(expression.value).toLocaleLowerCase(language));
    case "matches":
      return wildcardPattern(String(expression.value)).test(String(rowValue ?? ""));
    case "exists":
      return expression.value ? isNotNil(rowValue) : isNil(rowValue);
  }
}

function searchValues<TDocument extends Record<string, unknown>>(
  row: TDocument,
  selector: SearchSelector<TDocument>,
): readonly unknown[] {
  const name = String(selector);
  const dot = name.indexOf(".");
  if (dot < 0) return [row[name]];
  const relationship = row[name.slice(0, dot)];
  if (typeof relationship !== "object" || relationship === null || Array.isArray(relationship)) return [];
  const relationshipField = (relationship as Record<string, unknown>)[name.slice(dot + 1)];
  if (relationshipField !== undefined) return [relationshipField];
  const value = (relationship as Record<string, unknown>).value;
  const targets = Array.isArray(value) ? value : [value];
  return targets.flatMap((target) =>
    typeof target === "object" && target !== null && !Array.isArray(target)
      ? [(target as Record<string, unknown>)[name.slice(dot + 1)]]
      : [],
  );
}

export function matchesFilters<TDocument extends Record<string, unknown>>(
  row: TDocument,
  config: TableListProps<TDocument>["config"],
  filters: FilterState<TDocument>,
  language: string | undefined,
): boolean {
  const search = filters.search.trim().toLocaleLowerCase(language);
  const matchesSearch =
    search === "" ||
    (config.listSearchableFields ?? []).some((field) =>
      searchValues(row, field).some((value) =>
        String(value ?? "")
          .toLocaleLowerCase(language)
          .includes(search),
      ),
    );
  return matchesSearch && (!filters.expression || matchesExpression(row, filters.expression, config, language));
}
