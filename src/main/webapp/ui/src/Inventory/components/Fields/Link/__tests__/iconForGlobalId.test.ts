import { describe, expect, test } from "vitest";
import { iconForInventoryGlobalId } from "../iconForGlobalId";

describe("iconForInventoryGlobalId", () => {
  test("maps known Inventory prefixes to RecordTypeIcon data", () => {
    expect(iconForInventoryGlobalId("SA12")).toEqual({
      iconName: "sample",
      recordTypeLabel: "Sample",
    });
    expect(iconForInventoryGlobalId("SS9")).toEqual({
      iconName: "subsample",
      recordTypeLabel: "Subsample",
    });
    expect(iconForInventoryGlobalId("IC1")).toEqual({
      iconName: "container",
      recordTypeLabel: "Container",
    });
    expect(iconForInventoryGlobalId("IN7")).toEqual({
      iconName: "container",
      recordTypeLabel: "Instrument",
    });
  });

  test("returns null for unknown prefixes and malformed ids", () => {
    expect(iconForInventoryGlobalId("SD1")).toBeNull();
    expect(iconForInventoryGlobalId("")).toBeNull();
    expect(iconForInventoryGlobalId("not-a-global-id")).toBeNull();
  });

  test("tolerates a version pin suffix", () => {
    expect(iconForInventoryGlobalId("SA12v3")?.iconName).toBe("sample");
  });
});
