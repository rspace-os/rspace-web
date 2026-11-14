import { describe, expect, it } from "vitest";
import type { FilterExpression } from "@/modules/common/table-list/types";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { allItemsBaseFilter } from "./allItemsBaseFilter";

describe("All items default scope", () => {
  it("defaults and resets to active enabled items", () => {
    expect(allItemsBaseFilter(null)).toEqual({
      kind: "and",
      children: [
        { kind: "comparison", field: "state", operator: "equals", value: "ACTIVE" },
        { kind: "comparison", field: "enabled", operator: "equals", value: true },
      ],
    });
  });
  it.each([
    ["state", "ARCHIVED"],
    ["state", "ACTIVE"],
    ["enabled", false],
    ["enabled", true],
  ] as const)("lifts the whole scope for %s=%s", (field, value) => {
    const rule: FilterExpression<BookingConfiguration> = { kind: "comparison", field, operator: "equals", value };
    expect(allItemsBaseFilter(rule)).toBeUndefined();
    expect(allItemsBaseFilter({ kind: "and", children: [rule] })).toBeUndefined();
    expect(allItemsBaseFilter({ kind: "or", children: [rule] })).toEqual(allItemsBaseFilter(null));
  });
});
