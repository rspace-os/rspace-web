import { describe, expect, it } from "vitest";
import type { InventoryOperation } from "../operationsConfig";
import { amountTakenExceedsOrigin, detailsValid, quantityExceedsOrigin } from "../operationValidation";
import type { OperationInputs } from "../types";

// A cryopreserve-shaped operation: it has a sub-zero temperature field and an optional cryomedium.
const cryo = {
  key: "cryopreserve",
  inputs: [
    { key: "sampleName", type: "text", labelKey: "x", required: true },
    { key: "count", type: "integer", labelKey: "x", min: 1 },
    { key: "eachAmount", type: "quantity", labelKey: "x" },
    { key: "amountTaken", type: "quantity", labelKey: "x" },
    { key: "cryomedium", type: "text", labelKey: "x" },
    { key: "storageTemp", type: "temperature", labelKey: "x" },
  ],
  effect: { eachAmountFrom: "eachAmount", amountTakenFrom: "amountTaken" },
} as unknown as InventoryOperation;

const validValues: OperationInputs = {
  sampleName: "Frozen A",
  count: 1,
  eachAmount: { numericValue: 5, unitId: 3 },
  amountTaken: { numericValue: 5, unitId: 3 },
  cryomedium: "",
  storageTemp: { numericValue: -80, unitId: 8 },
};

describe("detailsValid", () => {
  it("passes cryopreserve with a sub-zero storage temperature and an empty (optional) cryomedium", () => {
    expect(detailsValid(cryo, validValues)).toBe(true);
  });

  it("rejects a required text field left blank", () => {
    expect(detailsValid(cryo, { ...validValues, sampleName: "  " })).toBe(false);
  });

  it("rejects a zero created amount and a negative amount", () => {
    expect(detailsValid(cryo, { ...validValues, eachAmount: { numericValue: 0, unitId: 3 } })).toBe(false);
    expect(detailsValid(cryo, { ...validValues, amountTaken: { numericValue: -1, unitId: 3 } })).toBe(false);
  });

  it("requires the amount taken from the origin to be strictly positive", () => {
    expect(detailsValid(cryo, { ...validValues, amountTaken: { numericValue: 0, unitId: 3 } })).toBe(false);
  });

  it("rejects an amount whose unit has been cleared (no unit chosen)", () => {
    // A cleared unit (unitId 0, set when switching to a new process name) is an incomplete amount and
    // must block the step even if a numeric value is present.
    expect(detailsValid(cryo, { ...validValues, eachAmount: { numericValue: 5, unitId: 0 } })).toBe(false);
    expect(detailsValid(cryo, { ...validValues, amountTaken: { numericValue: 5, unitId: 0 } })).toBe(false);
  });

  it("validates only the given keys when allowedKeys is passed (per-step validation)", () => {
    // The wizard validates the name/template step and the amounts step separately: a blank amount unit
    // fails the amounts step but is ignored while validating only the name inputs, and vice versa.
    const blankUnit = { ...validValues, eachAmount: { numericValue: 5, unitId: 0 } };
    expect(detailsValid(cryo, blankUnit, new Set(["sampleName"]))).toBe(true); // amounts not checked
    expect(detailsValid(cryo, blankUnit, new Set(["eachAmount"]))).toBe(false); // amounts checked
    const blankName = { ...validValues, sampleName: "  " };
    expect(detailsValid(cryo, blankName, new Set(["eachAmount", "amountTaken"]))).toBe(true); // name skipped
    expect(detailsValid(cryo, blankName, new Set(["sampleName"]))).toBe(false); // name checked
  });
});

describe("detailsValid temperature limit", () => {
  // cryo with a configured storage-temperature ceiling of -18 C (as in operations_config.json)
  const cryoWithMax = {
    ...cryo,
    inputs: cryo.inputs.map((i) => (i.key === "storageTemp" ? { ...i, maxCelsius: -18 } : i)),
  } as unknown as InventoryOperation;

  it("rejects a storage temperature above the configured maximum", () => {
    expect(detailsValid(cryoWithMax, { ...validValues, storageTemp: { numericValue: -10, unitId: 8 } })).toBe(false);
  });

  it("accepts a storage temperature at or below the configured maximum", () => {
    expect(detailsValid(cryoWithMax, { ...validValues, storageTemp: { numericValue: -18, unitId: 8 } })).toBe(true);
    expect(detailsValid(cryoWithMax, { ...validValues, storageTemp: { numericValue: -80, unitId: 8 } })).toBe(true);
  });

  it("does not constrain the temperature when no maximum is configured", () => {
    expect(detailsValid(cryo, { ...validValues, storageTemp: { numericValue: 20, unitId: 8 } })).toBe(true);
  });
});

describe("amountTakenExceedsOrigin", () => {
  // unit ids: 3 = millilitres, 4 = litres (same, volume, category); origin holds 400 ml.
  const origin = { numericValue: 400, unitId: 3 };

  it("is false when the amount taken is within (or equal to) the origin's quantity", () => {
    expect(
      amountTakenExceedsOrigin(cryo, { ...validValues, amountTaken: { numericValue: 400, unitId: 3 } }, origin),
    ).toBe(false);
    expect(
      amountTakenExceedsOrigin(cryo, { ...validValues, amountTaken: { numericValue: 100, unitId: 3 } }, origin),
    ).toBe(false);
  });

  it("is true when the amount taken exceeds the origin's quantity (same unit)", () => {
    expect(
      amountTakenExceedsOrigin(cryo, { ...validValues, amountTaken: { numericValue: 401, unitId: 3 } }, origin),
    ).toBe(true);
  });

  it("compares unit-aware across units in the same category (0.5 L > 400 ml)", () => {
    expect(
      amountTakenExceedsOrigin(cryo, { ...validValues, amountTaken: { numericValue: 0.5, unitId: 4 } }, origin),
    ).toBe(true);
    expect(
      amountTakenExceedsOrigin(cryo, { ...validValues, amountTaken: { numericValue: 0.3, unitId: 4 } }, origin),
    ).toBe(false);
  });

  it("does not flag an incomplete (unit-unset) amount", () => {
    expect(
      amountTakenExceedsOrigin(cryo, { ...validValues, amountTaken: { numericValue: 999, unitId: 0 } }, origin),
    ).toBe(false);
  });

  it("flags a positive amount taken from an origin that has no quantity (treated as zero available)", () => {
    // A subsample whose volume was never set reads as 0, so taking any positive amount is over-removal.
    expect(amountTakenExceedsOrigin(cryo, validValues, null)).toBe(true);
  });
});

// The per-origin over-removal check used for "per subsample" amounts (adr/0009): each origin's chosen
// amount is checked against its own quantity, unit-aware within a category.
describe("quantityExceedsOrigin", () => {
  it("is false at or within the origin's quantity, true above it (same unit)", () => {
    expect(quantityExceedsOrigin({ numericValue: 5, unitId: 3 }, { numericValue: 5, unitId: 3 })).toBe(false);
    expect(quantityExceedsOrigin({ numericValue: 4, unitId: 3 }, { numericValue: 5, unitId: 3 })).toBe(false);
    expect(quantityExceedsOrigin({ numericValue: 6, unitId: 3 }, { numericValue: 5, unitId: 3 })).toBe(true);
  });

  it("compares unit-aware across units in the same category (0.5 L > 400 ml)", () => {
    expect(quantityExceedsOrigin({ numericValue: 0.5, unitId: 4 }, { numericValue: 400, unitId: 3 })).toBe(true);
    expect(quantityExceedsOrigin({ numericValue: 0.3, unitId: 4 }, { numericValue: 400, unitId: 3 })).toBe(false);
  });

  it("does not flag an incomplete (unit-unset) or absent amount", () => {
    expect(quantityExceedsOrigin({ numericValue: 999, unitId: 0 }, { numericValue: 5, unitId: 3 })).toBe(false);
    expect(quantityExceedsOrigin(undefined, { numericValue: 5, unitId: 3 })).toBe(false);
  });

  it("flags any positive amount against an origin that holds nothing", () => {
    expect(quantityExceedsOrigin({ numericValue: 1, unitId: 3 }, null)).toBe(true);
  });
});
