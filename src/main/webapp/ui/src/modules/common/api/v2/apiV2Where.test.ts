import { describe, expect, it } from "vitest";
import {
  type ApiV2Where,
  type ApiV2WhereObject,
  type ApiV2WhereOperator,
  apiV2WhereToRsql,
  type RsqlOperand,
  rsqlComparison,
} from "./apiV2Where";

describe("apiV2WhereToRsql", () => {
  it.each<[ApiV2WhereObject<"Users">, string]>([
    [{}, ""],
    [{ id: 7 }, "id==7"],
    [{ email: { notEquals: "a@example.com" } }, "email!=a@example.com"],
    [{ id: { greaterThan: 1, lessThanOrEqual: 9 } }, "id=gt=1;id=le=9"],
    [{ id: { in: [1, 2] }, username: { notIn: ["root"] } }, "id=in=(1,2);username=out=(root)"],
    [{ email: { exists: false } }, "email=exists=false"],
    [{ firstName: { contains: "Ann" }, lastName: { matches: "Sm*" } }, "firstName=contains=Ann;lastName=like=Sm*"],
    [{ createdAt: { greaterThanOrEqual: new Date("2026-01-01T00:00:00Z") } }, "createdAt=ge=2026-01-01T00:00:00.000Z"],
    [{ lastName: 'O\'Brien (QA); "x"' }, 'lastName=="O\'Brien (QA); \\"x\\""'],
    [{ firstName: "" }, 'firstName==""'],
    [{ id: undefined, username: "ann" }, "username==ann"],
  ])("converts %j", (where, rsql) => {
    expect(apiV2WhereToRsql(where)).toBe(rsql);
  });

  it("groups nested or/and", () => {
    expect(
      apiV2WhereToRsql<"Users">({
        or: [{ email: { contains: "smith" } }, { lastName: { matches: "Sm*" }, firstName: "Jo" }],
        id: { greaterThan: 3 },
      }),
    ).toBe("(email=contains=smith,(lastName=like=Sm*;firstName==Jo));id=gt=3");
    expect(apiV2WhereToRsql<"Users">({ and: [{ id: 1 }, { or: [{ id: 2 }, { id: 3 }] }] })).toBe(
      "(id==1;(id==2,id==3))",
    );
    expect(apiV2WhereToRsql<"Users">({ or: [{ id: 1 }] })).toBe("id==1");
  });

  it("rejects values RSQL cannot express", () => {
    const loose = (where: object) => () => apiV2WhereToRsql(where as ApiV2WhereObject<"Users">);
    expect(loose({ id: { in: [] } })).toThrow("non-empty list");
    expect(loose({ id: {} })).toThrow("at least one operator");
    expect(loose({ or: [] })).toThrow("non-empty list");
    expect(loose({ or: [{}] })).toThrow("Empty where object");
    expect(loose({ id: { between: 1 } })).toThrow("Unknown operator");
    expect(loose({ id: null })).toThrow("Unsupported filter value");
  });
});

describe("rsqlComparison", () => {
  it.each<[ApiV2WhereOperator, readonly RsqlOperand[], string]>([
    ["equals", [1, 2], "id.equals: Expected a single value"],
    ["in", [], "id.in: Expected a non-empty list"],
  ])("rejects %s with %j", (operator, operand, message) => {
    expect(() => rsqlComparison("id", operator, operand)).toThrow(new TypeError(message));
  });
});

describe("ApiV2Where types", () => {
  it("accepts allowed selectors, operators and values only", () => {
    const valid: ApiV2Where<"Users">[] = [
      {},
      { id: 7, email: { contains: "x" }, createdAt: { lessThan: new Date() } },
      { username: { in: ["a", "b"] }, or: [{ id: { notIn: [1] } }] },
      { and: [{ id: 1 }, { or: [{ id: 2 }, { email: { contains: "x" } }] }] },
      { firstName: { exists: true } },
    ];
    const containerType: ApiV2Where<"Containers"> = { containerType: "GRID" };
    const invalid: ApiV2Where<"Users">[] = [
      // @ts-expect-error Users has no "state" selector.
      { state: "CONFIRMED" },
      // @ts-expect-error id takes a number.
      { id: { greaterThan: "7" } },
      // @ts-expect-error =in= needs at least one value.
      { id: { in: [] } },
      // @ts-expect-error =exists= takes a boolean.
      { email: { exists: "yes" } },
      // @ts-expect-error a group needs at least one where object.
      { or: [] },
      // @ts-expect-error a selector's operator object needs at least one operator.
      { id: {} },
      // @ts-expect-error a nested where object needs at least one condition.
      { or: [{}] },
      // @ts-expect-error raw strings are not filters; build them from an object.
      "id==7",
    ];
    // @ts-expect-error containerType only allows ==, != and =exists=.
    const notAllowed: ApiV2Where<"Containers"> = { containerType: { in: ["GRID"] } };
    // @ts-expect-error a Users filter is not a Containers filter.
    const wrongResource: ApiV2Where<"Containers"> = apiV2WhereToRsql<"Users">({ id: 1 });
    expect([valid, containerType, invalid, notAllowed, wrongResource]).toHaveLength(5);
  });
});
