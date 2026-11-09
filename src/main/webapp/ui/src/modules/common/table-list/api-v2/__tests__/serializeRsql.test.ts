import { describe, expect, it } from "vitest";
import type { FilterOperator } from "@/modules/common/collection/filterOperators";
import type { FilterExpression } from "../../types";
import { serializeRsql } from "../serializeRsql";

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
    ["a\\b", 'name=="a\\\\b"'],
    ["a,b", 'name=="a,b"'],
    ["Łódź", "name==Łódź"],
  ])("escapes the value %s", (value, expected) => {
    expect(serializeRsql(comparison("name", "equals", value), selectors, limits)).toBe(expected);
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
    expect(() => serializeRsql(comparison("id", "equals", "4*"), selectors, limits)).toThrow("Wildcards");
    expect(() =>
      serializeRsql({ kind: "comparison", field: "name", operator: "in", value: [] }, selectors, limits),
    ).toThrow("at least one");
    expect(() =>
      serializeRsql(
        { kind: "and", children: [comparison("name", "equals", "a"), comparison("name", "equals", "b")] },
        selectors,
        { ...limits, maximumComparisons: 1 },
      ),
    ).toThrow("comparison limit");
    expect(() =>
      serializeRsql(comparison("name", "matches", "A*"), selectors, { ...limits, maximumLikeComparisons: 0 }),
    ).toThrow("Pattern comparison limit");
    expect(() =>
      serializeRsql(comparison("name", "contains", "Ada"), selectors, { ...limits, maximumLikeComparisons: 0 }),
    ).toThrow("Pattern comparison limit");
    expect(() =>
      serializeRsql({ kind: "comparison", field: "name", operator: "in", value: ["a", "b"] }, selectors, {
        ...limits,
        maximumArguments: 1,
      }),
    ).toThrow("argument limit");
    expect(() =>
      serializeRsql(comparison("name", "equals", "a b"), selectors, { ...limits, maximumWhereLength: 10 }),
    ).toThrow("length limit");
    expect(() =>
      serializeRsql({ kind: "comparison", field: "name", operator: "equals", value: null as never }, selectors, limits),
    ).toThrow("Null filter values");
  });

  it("counts a wildcard equality on a wildcard selector as a pattern comparison", () => {
    const patterns = Array.from({ length: 10 }, (_, index) => comparison("name", "equals", `a${index}*`));
    expect(() => serializeRsql({ kind: "and", children: patterns }, selectors, limits)).not.toThrow();
    expect(() =>
      serializeRsql({ kind: "and", children: [...patterns, comparison("name", "notEquals", "b*")] }, selectors, limits),
    ).toThrow("Pattern comparison limit");
    const exact = Array.from({ length: 11 }, (_, index) => comparison("name", "equals", `a${index}`));
    expect(() => serializeRsql({ kind: "and", children: exact }, selectors, limits)).not.toThrow();
  });

  it("counts logical groups, not comparisons, against the nesting limit", () => {
    const nested = (groups: number) => {
      let node = comparison("name", "equals", "a");
      for (let group = 0; group < groups; group += 1) {
        node = { kind: group % 2 === 0 ? "or" : "and", children: [node, comparison("id", "equals", group)] };
      }
      return node;
    };
    expect(() => serializeRsql(nested(10), selectors, limits)).not.toThrow();
    expect(() => serializeRsql(nested(11), selectors, limits)).toThrow("nesting limit");
  });
});
