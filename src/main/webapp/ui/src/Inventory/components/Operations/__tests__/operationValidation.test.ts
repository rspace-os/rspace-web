import { describe, expect, it } from "vitest";
import type { InventoryOperation } from "../operationsConfig";
import {
  amountIsStorable,
  amountTakenExceedsOrigin,
  detailsValid,
  quantityExceedsOrigin,
  reconcileRestoredQuantities,
} from "../operationValidation";
import type { OperationInputs } from "../types";
import { UNSET_UNIT } from "../types";

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

  it("requires the child count to be a whole number between the minimum and the server's cap of 100", () => {
    // Code review, finding 12: 1.5 used to pass and Array.from truncated it to one child; 101 was
    // built client-side only to be rejected by the server's @Size(max = 100).
    expect(detailsValid(cryo, { ...validValues, count: 1.5 })).toBe(false);
    expect(detailsValid(cryo, { ...validValues, count: 101 })).toBe(false);
    expect(detailsValid(cryo, { ...validValues, count: 0 })).toBe(false);
    expect(detailsValid(cryo, { ...validValues, count: 100 })).toBe(true);
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

  // revive-style floor: minCelsius 4 (as in operations_config.json)
  const reviveWithMin = {
    ...cryo,
    inputs: cryo.inputs.map((i) => (i.key === "storageTemp" ? { ...i, minCelsius: 4 } : i)),
  } as unknown as InventoryOperation;

  it("rejects a storage temperature below the configured minimum", () => {
    expect(detailsValid(reviveWithMin, { ...validValues, storageTemp: { numericValue: 2, unitId: 8 } })).toBe(false);
  });

  it("accepts a storage temperature at or above the configured minimum", () => {
    expect(detailsValid(reviveWithMin, { ...validValues, storageTemp: { numericValue: 4, unitId: 8 } })).toBe(true);
    expect(detailsValid(reviveWithMin, { ...validValues, storageTemp: { numericValue: 37, unitId: 8 } })).toBe(true);
  });

  it("rejects a temperature the backend would refuse outright", () => {
    // -300 satisfies the -18 C ceiling but is below absolute zero (@ValidTemperature), and
    // -80.0005 is finer than the stored 3 decimal places; both used to enable Perform only to fail
    // at the backend (Copilot review, PR #1090).
    for (const numericValue of [-300, -80.0005]) {
      expect(detailsValid(cryoWithMax, { ...validValues, storageTemp: { numericValue, unitId: 8 } })).toBe(false);
    }
    // absolute zero itself is storable and satisfies the ceiling
    expect(detailsValid(cryoWithMax, { ...validValues, storageTemp: { numericValue: -273.15, unitId: 8 } })).toBe(true);
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

// The per-origin over-removal check used for "per subsample" amounts (DevDocs/adr/0007): each origin's chosen
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

describe("amountIsStorable", () => {
  // Quantities persist in a DECIMAL(19,3) column and the endpoint rejects anything finer, so the
  // wizard must block it rather than let Perform fail (Copilot review, PR #1090).
  it("accepts up to three decimal places and rejects finer values", () => {
    expect(amountIsStorable(1)).toBe(true);
    expect(amountIsStorable(0.001)).toBe(true);
    expect(amountIsStorable(2.5)).toBe(true);
    // Binary floating point: 1.001 * 1000 is 1000.9999999999999, so a naive integer test on the
    // scaled value rejects a three-decimal amount the backend stores happily (Copilot review,
    // PR #1090).
    expect(amountIsStorable(1.001)).toBe(true);
    expect(amountIsStorable(2.002)).toBe(true);
    expect(amountIsStorable(0.007)).toBe(true);
    expect(amountIsStorable(0.0005)).toBe(false);
    expect(amountIsStorable(0.0004)).toBe(false);
    expect(amountIsStorable(Number.NaN)).toBe(false);
    expect(amountIsStorable(Number.POSITIVE_INFINITY)).toBe(false);
  });
});

// Unit ids from stores/definitions/Units: volume 3 = mL, 4 = L; mass 7 = g.
const ML = 3;
const L = 4;
const G = 7;

/**
 * A remembered bundle is keyed by operation plus process name only, so the same bundle is offered on
 * any origin. Nothing downstream catches a category mismatch, so a mL bundle reused on a gram origin
 * used to make allStepsValid() true and offer one-click Perform on a request the endpoint rejects.
 */
describe("reconcileRestoredQuantities", () => {
  const reconcile = (
    values: OperationInputs,
    overrides: Partial<Parameters<typeof reconcileRestoredQuantities>[0]> = {},
  ) =>
    reconcileRestoredQuantities({
      values,
      perSubsampleAmounts: {},
      amountTakenFrom: "amountTaken",
      eachAmountFrom: "eachAmount",
      originUnitId: G,
      createdCategory: "mass",
      perOriginUnitIds: {},
      ...overrides,
    });

  it("clears the unit of an amount taken whose category no longer matches the origin", () => {
    const { values } = reconcile({
      amountTaken: { numericValue: 5, unitId: ML },
      eachAmount: { numericValue: 2, unitId: G },
    });
    // Unit CLEARED, not defaulted to the origin's. A defaulted unit is a valid amount, so nothing
    // downstream blocked and the one-click fast path stayed armed on a number the user never chose:
    // a bundle remembering 50 mL on a gram origin silently removed 1 g (parallel review, C4). The
    // saved number is kept so the user can see what to re-enter.
    expect(values.amountTaken).toEqual({ numericValue: 5, unitId: UNSET_UNIT });
  });

  it("clears the created amount's unit rather than defaulting it, so the fast path is not offered", () => {
    const { values } = reconcile({
      amountTaken: { numericValue: 5, unitId: G },
      eachAmount: { numericValue: 2, unitId: ML },
    });
    // Mirrors a cross-category template pick: unset unit, number kept, so the amounts step is walked.
    expect(values.eachAmount).toEqual({ numericValue: 2, unitId: UNSET_UNIT });
  });

  it("leaves a different unit of the SAME category alone", () => {
    const { values } = reconcile(
      {
        amountTaken: { numericValue: 0.5, unitId: L },
        eachAmount: { numericValue: 2, unitId: ML },
      },
      { originUnitId: ML, createdCategory: "volume" },
    );
    // Litres on a millilitre origin is a unit choice, not a mismatch, so the bundle survives whole.
    expect(values.amountTaken).toEqual({ numericValue: 0.5, unitId: L });
    expect(values.eachAmount).toEqual({ numericValue: 2, unitId: ML });
  });

  it("keeps every non-quantity value, so template and text inputs are retained", () => {
    const { values } = reconcile({
      amountTaken: { numericValue: 5, unitId: ML },
      eachAmount: { numericValue: 2, unitId: ML },
      cryomedium: "DMSO 10%",
      count: 4,
    });
    expect(values.cryomedium).toEqual("DMSO 10%");
    expect(values.count).toEqual(4);
  });

  it("checks the created amount against the RESTORED TEMPLATE's category, not the origin's", () => {
    // With a template restored, the created amounts follow the template; a gram origin does not make
    // a millilitre created amount wrong.
    const { values } = reconcile(
      {
        amountTaken: { numericValue: 5, unitId: G },
        eachAmount: { numericValue: 2, unitId: ML },
      },
      { createdCategory: "volume" },
    );
    expect(values.eachAmount).toEqual({ numericValue: 2, unitId: ML });
  });

  it("resets only the mismatching origin's per-origin amount", () => {
    const { perSubsampleAmounts } = reconcile(
      { amountTaken: { numericValue: 1, unitId: G }, eachAmount: { numericValue: 1, unitId: G } },
      {
        perSubsampleAmounts: {
          SS1: { numericValue: 2, unitId: G },
          SS2: { numericValue: 3, unitId: ML },
        },
        perOriginUnitIds: { SS1: G, SS2: G },
      },
    );
    expect(perSubsampleAmounts.SS1).toEqual({ numericValue: 2, unitId: G });
    expect(perSubsampleAmounts.SS2).toEqual({ numericValue: 3, unitId: UNSET_UNIT });
  });

  it("leaves an amount alone when its unit's category cannot be determined", () => {
    // categoryOfUnit knows only volume, mass and dimensionless ids, while the expected category
    // comes from a server-supplied unit list that also has temperature, molarity and concentration.
    // Comparing "actual !== expected" therefore reported a mismatch for every unit this module does
    // not enumerate, wiping a perfectly good saved amount every time (parallel review, I9). Unknown
    // is not the same as wrong.
    const unknownUnit = 9999;
    const { values } = reconcile(
      {
        amountTaken: { numericValue: 5, unitId: unknownUnit },
        eachAmount: { numericValue: 2, unitId: unknownUnit },
      },
      { createdCategory: "molarity" },
    );
    expect(values.amountTaken).toEqual({ numericValue: 5, unitId: unknownUnit });
    expect(values.eachAmount).toEqual({ numericValue: 2, unitId: unknownUnit });
  });

  it("leaves an already-unset unit alone: there is nothing to repair", () => {
    const { values } = reconcile({
      amountTaken: { numericValue: 1, unitId: UNSET_UNIT },
      eachAmount: { numericValue: 1, unitId: UNSET_UNIT },
    });
    expect(values.amountTaken).toEqual({ numericValue: 1, unitId: UNSET_UNIT });
    expect(values.eachAmount).toEqual({ numericValue: 1, unitId: UNSET_UNIT });
  });

  it("leaves an amount for an origin this run does not include alone", () => {
    // That amount belongs to the stored bundle, not to this request; editing it here would silently
    // rewrite what a later run on a matching origin restores.
    const { perSubsampleAmounts } = reconcile(
      { amountTaken: { numericValue: 1, unitId: G }, eachAmount: { numericValue: 1, unitId: G } },
      {
        perSubsampleAmounts: { SS_ELSEWHERE: { numericValue: 3, unitId: ML } },
        perOriginUnitIds: { SS1: G },
      },
    );
    expect(perSubsampleAmounts.SS_ELSEWHERE).toEqual({ numericValue: 3, unitId: ML });
  });
});

describe("quantityExceedsOrigin across categories", () => {
  it("answers 'not exceeding' explicitly for cross-category input", () => {
    // Each side converts to the atomic unit of its own category, so without the guard this compared
    // picolitres against picograms and produced a meaningless verdict.
    expect(quantityExceedsOrigin({ numericValue: 1, unitId: G }, { numericValue: 1, unitId: ML })).toBe(false);
    expect(quantityExceedsOrigin({ numericValue: 1e9, unitId: G }, { numericValue: 1, unitId: ML })).toBe(false);
  });

  it("still compares normally within one category", () => {
    expect(quantityExceedsOrigin({ numericValue: 2, unitId: ML }, { numericValue: 1, unitId: ML })).toBe(true);
    expect(quantityExceedsOrigin({ numericValue: 0.5, unitId: ML }, { numericValue: 1, unitId: ML })).toBe(false);
    expect(quantityExceedsOrigin({ numericValue: 1, unitId: L }, { numericValue: 1, unitId: ML })).toBe(true);
  });
});
