import { describe, expect, it } from "vitest";
import { getUnit, unitsOfCategory, volumeIds } from "../../Units";

describe("unit labels", () => {
  it("translates the items label and leaves symbols alone", () => {
    expect(getUnit(1)?.label).toBe("common:units.items");
    expect(unitsOfCategory(["dimensionless"])).toEqual([
      { id: 1, label: "common:units.items", category: "dimensionless" },
    ]);
    expect(getUnit(volumeIds.milliliters)?.label).toBe("mL");
  });
});
