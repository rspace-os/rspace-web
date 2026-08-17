import { describe, expect, it } from "vitest";
import type { FilterOperator } from "@/modules/common/collection/collectionConfig";
import { serializeRsql } from "../../../adapters/apiV2/rsql/serializeRsql";
import type { FilterExpression } from "../../../tableListState";

type Row = { name: string; id: number; enabled: boolean };

const selectors = {
  name: { operators: ["==", "!=", "=contains=", "=like=", "=in=", "=out=", "=exists="] as const, wildcards: true },
  id: { operators: ["==", "=gt=", "=ge=", "=lt=", "=le="] as const, wildcards: false },
  enabled: { operators: ["==", "=exists="] as const, wildcards: false },
};
const limits = {
  maximumComparisons: 50,
  maximumLikeComparisons: 10,
  maximumNesting: 10,
  maximumArguments: 100,
  maximumWhereLength: 4096,
};

const comparison = (
  field: keyof Row,
  operator: FilterOperator,
  value: string | number | boolean,
): FilterExpression<Row> => ({
  kind: "comparison",
  field,
  operator,
  value,
});

describe("serializeRsql", () => {
  it.each([
    ["equals", "name==Ada"],
    ["notEquals", "name!=Ada"],
    ["contains", "name=contains=Ada"],
    ["matches", "name=like=A*"],
    ["greaterThan", "id=gt=4"],
    ["greaterThanOrEqual", "id=ge=4"],
    ["lessThan", "id=lt=4"],
    ["lessThanOrEqual", "id=le=4"],
    ["exists", "name=exists=true"],
  ] as const)("maps %s to the API token", (operator, expected) => {
    expect(
      serializeRsql(
        comparison(
          operator.includes("Than") ? "id" : "name",
          operator,
          operator === "exists" ? true : operator.includes("Than") ? 4 : operator === "matches" ? "A*" : "Ada",
        ),
        selectors,
        limits,
      ),
    ).toBe(expected);
  });

  it("serializes list operators and escapes reserved values", () => {
    expect(
      serializeRsql(
        { kind: "comparison", field: "name", operator: "in", value: ["Ada Lovelace", "a(b", "x,y"] },
        selectors,
        limits,
      ),
    ).toBe('name=in=("Ada Lovelace","a(b","x,y")');
    expect(
      serializeRsql(
        { kind: "comparison", field: "name", operator: "notIn", value: ["Ada", "Grace"] },
        selectors,
        limits,
      ),
    ).toBe("name=out=(Ada,Grace)");
  });

  it.each([
    ["", 'name==""'],
    ['Ada "L"', 'name=="Ada \\"L\\""'],
    ["a\\b", 'name=="a\\\\b"'],
    ["Ada Lovelace", 'name=="Ada Lovelace"'],
    ["a,b", 'name=="a,b"'],
    ["a(", 'name=="a("'],
    ["Łódź", "name==Łódź"],
  ])("escapes the value %s", (value, expected) => {
    expect(serializeRsql(comparison("name", "equals", value), selectors, limits)).toBe(expected);
  });

  it("escapes values used with custom RSpace operators", () => {
    expect(serializeRsql(comparison("name", "contains", "Ada Lovelace"), selectors, limits)).toBe(
      'name=contains="Ada Lovelace"',
    );
  });

  it("preserves nested precedence for a value containing an opening parenthesis", () => {
    const expression: FilterExpression<Row> = {
      kind: "and",
      children: [
        { kind: "or", children: [comparison("name", "equals", "a("), comparison("id", "equals", 1)] },
        comparison("enabled", "equals", true),
      ],
    };
    expect(serializeRsql(expression, selectors, limits)).toBe('(name=="a(",id==1);enabled==true');
  });

  it("enforces backend limits and selector capabilities", () => {
    expect(() => serializeRsql(comparison("id", "equals", "4*"), selectors, limits)).toThrow(/Wildcards/);
    expect(() =>
      serializeRsql({ kind: "comparison", field: "name", operator: "in", value: [] }, selectors, limits),
    ).toThrow(/at least one/);
    expect(() =>
      serializeRsql(
        { kind: "and", children: [comparison("name", "equals", "a"), comparison("name", "equals", "b")] },
        selectors,
        { ...limits, maximumComparisons: 1 },
      ),
    ).toThrow(/comparison limit/);
    expect(() =>
      serializeRsql(comparison("name", "matches", "A*"), selectors, { ...limits, maximumLikeComparisons: 0 }),
    ).toThrow(/Pattern comparison limit/);
    expect(() =>
      serializeRsql(comparison("name", "contains", "Ada"), selectors, { ...limits, maximumLikeComparisons: 0 }),
    ).toThrow(/Pattern comparison limit/);
    expect(() =>
      serializeRsql({ kind: "comparison", field: "name", operator: "in", value: ["a", "b"] }, selectors, {
        ...limits,
        maximumArguments: 1,
      }),
    ).toThrow(/argument limit/);
    expect(() =>
      serializeRsql(comparison("name", "equals", "a b"), selectors, { ...limits, maximumWhereLength: 10 }),
    ).toThrow(/length limit/);
    expect(() =>
      serializeRsql({ kind: "comparison", field: "name", operator: "equals", value: null as never }, selectors, limits),
    ).toThrow(/Null filter values/);
  });
});
