import { describe, expect, it } from "vitest";
import type { InventoryOperation } from "../operationsConfig";
import { detailsValid } from "../operationValidation";
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
