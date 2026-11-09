import { describe, expect, it } from "vitest";
import { type ApiV2WhereObject, apiV2WhereToRsql } from "@/modules/common/api/v2/apiV2Where";
import { serializeRsqlExpression } from "../../rsql";
import { filterExpressionFromWhere, isFilterExpression } from "../filterExpressionFromWhere";

describe("filterExpressionFromWhere", () => {
  it("stands for the same RSQL as the typed where object", () => {
    const where: ApiV2WhereObject<"Instruments"> = {
      deleted: false,
      id: { greaterThanOrEqual: 8, lessThan: 100 },
      name: undefined,
      "parentContainer.id": { in: [3, 4] },
      or: [
        { name: { contains: "centri fuge" } },
        { name: { matches: "Cen*" }, "parentContainer.containerType": "GRID" },
      ],
    };
    const expression = filterExpressionFromWhere(where);
    if (!expression) throw new Error("A where object with conditions is a filter");

    expect(serializeRsqlExpression(expression)).toBe(apiV2WhereToRsql(where));
  });

  it("means no filter for an object without conditions", () => {
    expect(filterExpressionFromWhere<Record<string, unknown>, "Instruments">({ name: undefined })).toBeUndefined();
  });

  it.each([
    [{ and: [] }, "where.and: Expected a non-empty list"],
    [{ or: [{}] }, "where.or: Empty where object"],
    [{ name: {} }, "where.name: Expected at least one operator"],
  ])("rejects %j as apiV2WhereToRsql does", (where, message) => {
    // These shapes do not type-check; the runtime rejection is what this test covers.
    const loose = (value: object) => value as ApiV2WhereObject<"Instruments">;
    expect(() => filterExpressionFromWhere(loose(where))).toThrow(message);
    expect(() => apiV2WhereToRsql(loose(where))).toThrow(message);
  });
});

describe("isFilterExpression", () => {
  it("tells expressions from where objects, including one with a kind selector", () => {
    expect(isFilterExpression({ kind: "comparison", field: "name", operator: "equals", value: "x" })).toBe(true);
    expect(isFilterExpression({ kind: "and", children: [] })).toBe(true);
    expect(isFilterExpression<Record<string, unknown>, "Instruments">({ name: "x" })).toBe(false);
    expect(isFilterExpression({ kind: "BOOKING" } as ApiV2WhereObject<"Instruments">)).toBe(false);
  });
});
