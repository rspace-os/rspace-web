import { describe, expect, test } from "vitest";
import {
  iconForInventoryGlobalId,
  iconForGlobalId,
  prefixOf,
  isInventoryGlobalId,
  supportsVersionPin,
  openUrlForTarget,
} from "../iconForGlobalId";

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

  test("maps sample templates to the template icon", () => {
    expect(iconForInventoryGlobalId("IT3")).toEqual({
      iconName: "template",
      recordTypeLabel: "Sample template",
    });
  });

  test("returns null for ELN and unknown prefixes and malformed ids", () => {
    expect(iconForInventoryGlobalId("SD1")).toBeNull();
    expect(iconForInventoryGlobalId("")).toBeNull();
    expect(iconForInventoryGlobalId("not-a-global-id")).toBeNull();
  });

  test("tolerates a version pin suffix", () => {
    expect(iconForInventoryGlobalId("SA12v3")?.iconName).toBe("sample");
  });
});

describe("iconForGlobalId (inventory + ELN)", () => {
  test("maps ELN prefixes to supported RecordTypeIcon names", () => {
    expect(iconForGlobalId("SD1")).toEqual({
      iconName: "document",
      recordTypeLabel: "Document",
    });
    // RecordTypeIcon has no dedicated notebook icon, so notebooks reuse the document icon
    expect(iconForGlobalId("NB5")).toEqual({
      iconName: "document",
      recordTypeLabel: "Notebook",
    });
    expect(iconForGlobalId("GL9")).toEqual({
      iconName: "gallery",
      recordTypeLabel: "Gallery file",
    });
  });

  test("still maps inventory prefixes", () => {
    expect(iconForGlobalId("SA1")?.iconName).toBe("sample");
  });

  test("returns null for unsupported prefixes", () => {
    expect(iconForGlobalId("GF1")).toBeNull();
    expect(iconForGlobalId("not-a-global-id")).toBeNull();
  });
});

describe("prefix helpers", () => {
  test("prefixOf extracts the two-letter prefix, tolerating versions", () => {
    expect(prefixOf("SD123")).toBe("SD");
    expect(prefixOf("SD123v5")).toBe("SD");
    expect(prefixOf("garbage")).toBeNull();
  });

  test("isInventoryGlobalId is true only for inventory prefixes", () => {
    expect(isInventoryGlobalId("SA1")).toBe(true);
    expect(isInventoryGlobalId("IN9")).toBe(true);
    expect(isInventoryGlobalId("SD1")).toBe(false);
    expect(isInventoryGlobalId("NB1")).toBe(false);
  });


  test("supportsVersionPin is true for inventory and SD, false for NB and GL", () => {
    expect(supportsVersionPin("SA1")).toBe(true);
    expect(supportsVersionPin("SD1")).toBe(true);
    expect(supportsVersionPin("NB1")).toBe(false);
    expect(supportsVersionPin("GL1")).toBe(false);
  });
});

describe("openUrlForTarget", () => {
  test("opens a Gallery file at its location in the Gallery", () => {
    expect(openUrlForTarget("GL21", null)).toBe("/gallery/item/21");
  });

  test("keeps the /globalId route for non-Gallery targets", () => {
    expect(openUrlForTarget("SD5", null)).toBe("/globalId/SD5");
    expect(openUrlForTarget("SA10", null)).toBe("/globalId/SA10");
  });

  test("appends the pinned version suffix for non-Gallery targets", () => {
    expect(openUrlForTarget("SD5", 3)).toBe("/globalId/SD5v3");
  });
});
