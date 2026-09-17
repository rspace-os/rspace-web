import { describe, expect, it } from "vitest";
import type { ProcessValues } from "../processValues";
import { normalizeProcessValues } from "../processValues";

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

describe("normalizeProcessValues (documentation)", () => {
  it("passes through a well-formed document", () => {
    expect(
      normalizeProcessValues({ values: {}, documentation: { globalId: "SD1", name: "My SOP" } })?.documentation,
    ).toEqual({ globalId: "SD1", name: "My SOP" });
  });

  it("rejects a document that is not an object carrying both a global id and a name", () => {
    for (const stored of [undefined, null, "SD1", { globalId: "SD1" }, { name: "My SOP" }]) {
      expect(normalizeProcessValues({ values: {}, documentation: stored })?.documentation).toBeNull();
    }
  });
});

describe("normalizeProcessValues (amount modes, DevDocs/adr/0007)", () => {
  it("carries a valid amount mode and per-origin amounts unchanged", () => {
    const withMode: ProcessValues = {
      ...bundle,
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS1: { numericValue: 2, unitId: 3 } },
    };
    expect(normalizeProcessValues(withMode)).toEqual(withMode);
  });

  it("coerces an unknown amount mode to 'same'", () => {
    expect(normalizeProcessValues({ ...bundle, amountMode: "bogus" })?.amountMode).toBe("same");
  });

  it("drops malformed per-origin amount entries", () => {
    const normalized = normalizeProcessValues({
      ...bundle,
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS1: { numericValue: 2, unitId: 3 }, SS2: { numericValue: "x", unitId: 3 }, SS3: null },
    });
    expect(normalized?.perSubsampleAmounts).toEqual({ SS1: { numericValue: 2, unitId: 3 } });
  });

  it("omits the amount fields for an older bundle that lacks them (consumers default to 'same')", () => {
    const normalized = normalizeProcessValues({ values: { count: 1 } });
    expect(normalized).not.toHaveProperty("amountMode");
    expect(normalized).not.toHaveProperty("perSubsampleAmounts");
  });
});

describe("normalizeProcessValues: the stored template choice", () => {
  // Preferences are persisted JSON that outlives the code that wrote them. An unrecognised mode was
  // cast straight through, and resolveTemplateId then treated it as "fromSample", silently swapping
  // the user's template choice.
  const bundleWith = (template: unknown) => normalizeProcessValues({ values: { count: 1 }, template });

  it("falls back to unselected for a mode this version does not know", () => {
    expect(bundleWith({ mode: "someFutureMode", templateId: 7 })?.template).toEqual({
      mode: "unselected",
      templateId: null,
    });
  });

  it("falls back to unselected for a pick with no usable id", () => {
    expect(bundleWith({ mode: "pick", templateId: null })?.template).toEqual({
      mode: "unselected",
      templateId: null,
    });
    expect(bundleWith({ mode: "pick", templateId: "7" })?.template).toEqual({
      mode: "unselected",
      templateId: null,
    });
  });

  it("keeps a well-formed choice, with its name and category", () => {
    expect(bundleWith({ mode: "pick", templateId: 7, templateName: "T7", quantityCategory: "mass" })?.template).toEqual(
      { mode: "pick", templateId: 7, templateName: "T7", quantityCategory: "mass" },
    );
    expect(bundleWith({ mode: "none", templateId: null })?.template).toEqual({ mode: "none", templateId: null });
  });
});
