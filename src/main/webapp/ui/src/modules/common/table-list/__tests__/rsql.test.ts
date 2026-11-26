import { Temporal } from "@js-temporal/polyfill";
import { describe, expect, it } from "vitest";
import { hasPreset } from "../presets";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "../rsql";
import type { FilterExpression } from "../types";
import { serializeFilters } from "../utils/queryString";
import { config, type TestRecord } from "./fixtures/tableList";

describe("table-list RSQL query state", () => {
  it("round-trips nested expressions, typed lists, and dates", () => {
    const expression: FilterExpression<TestRecord> = {
      kind: "and",
      children: [
        { kind: "comparison", field: "score", operator: "in", value: [4, 8] },
        {
          kind: "or",
          children: [
            { kind: "comparison", field: "owner", operator: "contains", value: "Ada Lovelace" },
            {
              kind: "comparison",
              field: "modifiedAt",
              operator: "greaterThan",
              value: Temporal.Instant.from("2026-08-01T10:00:00Z"),
            },
          ],
        },
      ],
    };

    const serialized = serializeRsqlExpression(expression);

    expect(serialized).toBe('score=in=(4,8);(owner=contains="Ada Lovelace",modifiedAt=gt=2026-08-01T10:00:00.000Z)');
    expect(parseRsqlExpression(serialized, config)).toEqual(expression);
  });

  it("rejects invalid syntax, fields, operators, and typed values", () => {
    expect(parseRsqlExpression("not rsql", config)).toBeNull();
    expect(parseRsqlExpression("unknown==value", config)).toBeNull();
    expect(parseRsqlExpression("enabled=contains=true", config)).toBeNull();
    expect(parseRsqlExpression("score==many", config)).toBeNull();
    expect(parseRsqlExpression("modifiedAt==yesterday", config)).toBeNull();
    expect(parseRsqlExpression("modifiedAt==2026-02-30T10:00:00Z", config)).toBeNull();
  });

  it.each(["2026-08-01", "2026-08-01T10:00"])("rejects the date-time %s without an offset", (value) => {
    expect(parseRsqlExpression(`modifiedAt==${value}`, config)).toBeNull();
  });

  it("round-trips a saved view's date-time filter unchanged", () => {
    const saved = "modifiedAt=ge=2026-08-01T10:00:00.000Z;modifiedAt=lt=2026-08-03T12:30:00.500Z";
    const parsed = parseRsqlExpression(saved, config);
    expect(parsed).not.toBeNull();
    expect(serializeRsqlExpression(parsed as FilterExpression<TestRecord>)).toBe(saved);
  });

  it("cuts a six-digit fraction to milliseconds on the way in", () => {
    const parsed = parseRsqlExpression("modifiedAt==2026-08-01T10:00:00.123456Z", config);
    const rule = { kind: "comparison", field: "modifiedAt", operator: "equals" } as const;
    const preset = { ...rule, value: Temporal.Instant.from("2026-08-01T10:00:00.123Z") };
    expect(hasPreset(parsed, preset)).toBe(true);
    expect(serializeFilters<TestRecord>({ search: "", expression: parsed })).toBe(
      serializeFilters<TestRecord>({ search: "", expression: preset }),
    );
    expect(serializeRsqlExpression(parsed as FilterExpression<TestRecord>)).toBe(
      "modifiedAt==2026-08-01T10:00:00.123Z",
    );
  });

  it.each(["and", "or"] as const)("round-trips 50 flat %s comparisons and collects every runtime selector", (kind) => {
    const comparisons: FilterExpression<TestRecord>[] = Array.from({ length: 50 }, (_, index) => ({
      kind: "comparison",
      field: "title",
      operator: "equals",
      value: `Filter ${index}`,
    }));
    const expression: FilterExpression<TestRecord> =
      kind === "and" ? { kind: "and", children: comparisons } : { kind: "or", children: comparisons };

    const serialized = serializeRsqlExpression(expression);

    expect(parseRsqlExpression(serialized, config)).toEqual(expression);

    const runtimeSelectors = Array.from({ length: 50 }, (_, index) => `runtimeField${index}`);
    const runtimeFilter = runtimeSelectors.map((selector) => `${selector}==value`).join(kind === "and" ? ";" : ",");
    expect(rsqlSelectors(runtimeFilter)).toEqual(runtimeSelectors);
  });

  it("rejects mixed logical nesting beyond 20 groups without partial selectors", () => {
    let serialized = "title==value0";
    for (let index = 1; index <= 21; index += 1) {
      const operator = index % 2 === 0 ? ";" : ",";
      serialized = `(${serialized})${operator}title==value${index}`;
    }

    expect(parseRsqlExpression(serialized, config)).toBeNull();
    expect(rsqlSelectors(serialized)).toEqual([]);
  });

  it("drops stale selectors while preserving valid siblings in a flat chain", () => {
    const dropped: string[] = [];

    expect(
      parseRsqlExpression("title==Alpha;retired==old;score=gt=4", config, {
        isStale: (selector) => selector === "retired",
        onDropped: (selector) => dropped.push(selector),
      }),
    ).toEqual({
      kind: "and",
      children: [
        { kind: "comparison", field: "title", operator: "equals", value: "Alpha" },
        { kind: "comparison", field: "score", operator: "greaterThan", value: 4 },
      ],
    });
    expect(dropped).toEqual(["retired"]);
  });
});
