import { describe, expect, it } from "vitest";
import { parseRsqlExpression, serializeRsqlExpression } from "../rsql/rsqlCodec";
import type { FilterExpression } from "../tableListState";
import { config, type TestRecord } from "./fixtures/tableListFixtures";

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
              value: new Date("2026-08-01T10:00:00Z"),
            },
          ],
        },
      ],
    };

    const serialized = serializeRsqlExpression(expression);

    expect(serialized).toBe('score=in=(4,8);(owner=contains="Ada Lovelace",modifiedAt=gt=2026-08-01T10:00:00.000Z)');
    expect(parseRsqlExpression(serialized, config)).toEqual(expression);
  });

  it.each([
    ["title==Alpha", "equals"],
    ["title!=Alpha", "notEquals"],
    ["score=gt=4", "greaterThan"],
    ["score=ge=4", "greaterThanOrEqual"],
    ["score=lt=4", "lessThan"],
    ["score=le=4", "lessThanOrEqual"],
    ["title=in=(Alpha,Beta)", "in"],
    ["title=out=(Alpha,Beta)", "notIn"],
    ["title=contains=Alpha", "contains"],
    ["title=like=A*", "matches"],
    ["title=exists=true", "exists"],
  ] as const)("maps %s to %s", (serialized, operator) => {
    expect(parseRsqlExpression(serialized, config)).toMatchObject({ kind: "comparison", operator });
  });

  it("rejects invalid syntax, fields, operators, and typed values", () => {
    expect(parseRsqlExpression("not rsql", config)).toBeNull();
    expect(parseRsqlExpression("unknown==value", config)).toBeNull();
    expect(parseRsqlExpression("enabled=contains=true", config)).toBeNull();
    expect(parseRsqlExpression("score==many", config)).toBeNull();
    expect(parseRsqlExpression("modifiedAt==yesterday", config)).toBeNull();
  });
});
