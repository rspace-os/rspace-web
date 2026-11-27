import { Temporal } from "@js-temporal/polyfill";
import { expect, it } from "vitest";
import type { FilterOperator } from "@/modules/common/collection/filterOperators";
import { matchesFilters } from "../utils/clientFilters";
import { config, records, type TestRecord } from "./fixtures/tableList";

const matches = (modifiedAt: unknown, operator: FilterOperator, value: string) =>
  matchesFilters<TestRecord>(
    { ...records[0], modifiedAt } as TestRecord,
    config,
    {
      search: "",
      expression: { kind: "comparison", field: "modifiedAt", operator, value: Temporal.Instant.from(value) },
    },
    "en-US",
  );

it("compares date-time rows to the millisecond", () => {
  expect(matches("2026-08-01T10:00:00.123456Z", "equals", "2026-08-01T10:00:00.123Z")).toBe(true);
  expect(matches("2026-08-01T12:00:00+02:00", "lessThanOrEqual", "2026-08-01T10:00:00Z")).toBe(true);
  expect(matches("2026-08-01T10:00:00Z", "greaterThan", "2026-08-01T10:00:00Z")).toBe(false);
});

it.each([null, undefined, "not a date", "2026-08-01"])("never matches the date-time row %j", (modifiedAt) => {
  for (const operator of ["equals", "greaterThan", "lessThan"] as const)
    expect(matches(modifiedAt, operator, "2026-08-01T10:00:00Z")).toBe(false);
});
