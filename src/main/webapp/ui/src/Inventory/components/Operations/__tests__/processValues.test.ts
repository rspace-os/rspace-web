import { describe, expect, it } from "vitest";
import type { ProcessValues } from "../processValues";
import { normalizeProcessValues, processValuesAfterPerform } from "../processValues";

const bundle: ProcessValues = {
  values: { count: 2, eachAmount: { numericValue: 5, unitId: 3 }, amountTaken: { numericValue: 5, unitId: 3 } },
  template: { mode: "pick", templateId: 42, templateName: "Cells" },
  documentation: { globalId: "SD1", name: "SOP" },
};

describe("normalizeProcessValues", () => {
  it("returns a well-formed bundle unchanged", () => {
    expect(normalizeProcessValues(bundle)).toEqual(bundle);
  });

  it("returns null for a missing or malformed bundle", () => {
    expect(normalizeProcessValues(null)).toBeNull();
    expect(normalizeProcessValues(undefined)).toBeNull();
    expect(normalizeProcessValues({})).toBeNull();
    expect(normalizeProcessValues({ values: 3 })).toBeNull();
  });

  it("defaults an absent template to unselected and an absent/invalid document to null", () => {
    const normalized = normalizeProcessValues({ values: { count: 1 } });
    expect(normalized?.template).toEqual({ mode: "unselected", templateId: null });
    expect(normalized?.documentation).toBeNull();
  });
});

describe("processValuesAfterPerform", () => {
  it("stores the bundle under the key when remember is on", () => {
    expect(processValuesAfterPerform({}, "derive dna", bundle, true)).toEqual({ "derive dna": bundle });
  });

  it("leaves the store untouched when remember is off (never deletes a prior bundle)", () => {
    const current = { "derive dna": bundle };
    expect(processValuesAfterPerform(current, "derive dna", bundle, false)).toBe(current);
  });

  it("does not mutate the input map", () => {
    const current = { existing: bundle };
    processValuesAfterPerform(current, "derive dna", bundle, true);
    expect(current).toEqual({ existing: bundle });
  });
});
