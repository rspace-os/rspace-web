import { describe, expect, it } from "vitest";
import {
  resolveTemplateId,
  templateSelectionBlock,
  templateSelectionFor,
  templateSelectionToDefault,
  templateStepValid,
} from "../templateResolution";

describe("resolveTemplateId", () => {
  it("returns null when no template is chosen", () => {
    expect(resolveTemplateId({ mode: "none", pickedTemplateId: 5, originSampleTemplateId: 7 })).toBeNull();
  });

  it("returns the picked template id for an explicit choice", () => {
    expect(resolveTemplateId({ mode: "pick", pickedTemplateId: 5, originSampleTemplateId: 7 })).toBe(5);
  });

  it("uses the remembered template's id directly", () => {
    expect(resolveTemplateId({ mode: "remembered", pickedTemplateId: 88, originSampleTemplateId: 42 })).toBe(88);
  });

  it("reuses the origin sample's own template for 'fromSample'", () => {
    expect(resolveTemplateId({ mode: "fromSample", pickedTemplateId: null, originSampleTemplateId: 42 })).toBe(42);
  });

  it("resolves 'fromSample' to no template when the parent has none (the wizard never creates one)", () => {
    expect(resolveTemplateId({ mode: "fromSample", pickedTemplateId: null, originSampleTemplateId: null })).toBeNull();
  });
});

describe("templateSelectionToDefault", () => {
  it("stores a picked template with its id, name and quantity category", () => {
    expect(
      templateSelectionToDefault({ mode: "pick", templateId: 5, templateName: "T5", quantityCategory: "volume" }),
    ).toEqual({ mode: "pick", templateId: 5, templateName: "T5", quantityCategory: "volume" });
  });

  it("re-stores a still-in-effect remembered template as a plain pick", () => {
    expect(templateSelectionToDefault({ mode: "remembered", templateId: 5, templateName: "T5" })).toEqual({
      mode: "pick",
      templateId: 5,
      templateName: "T5",
    });
  });

  it("stores a non-pick choice without a template id", () => {
    expect(templateSelectionToDefault({ mode: "none", templateId: null })).toEqual({ mode: "none", templateId: null });
    expect(templateSelectionToDefault({ mode: "fromSample", templateId: null })).toEqual({
      mode: "fromSample",
      templateId: null,
    });
  });
});

describe("templateSelectionBlock", () => {
  it("does not block a template with no mandatory fields", () => {
    expect(templateSelectionBlock([{ name: "Notes", mandatory: false, hasDefault: false }])).toEqual({
      blocked: false,
      missingFields: [],
    });
  });

  it("does not block when mandatory fields all have defaults", () => {
    expect(templateSelectionBlock([{ name: "Batch", mandatory: true, hasDefault: true }])).toEqual({
      blocked: false,
      missingFields: [],
    });
  });

  it("blocks and names mandatory fields that lack a default", () => {
    const result = templateSelectionBlock([
      { name: "Batch", mandatory: true, hasDefault: true },
      { name: "Concentration", mandatory: true, hasDefault: false },
      { name: "Passage", mandatory: true, hasDefault: false },
    ]);
    expect(result.blocked).toBe(true);
    expect(result.missingFields).toEqual(["Concentration", "Passage"]);
  });
});

describe("templateSelectionFor", () => {
  it("is 'unselected' when nothing is remembered, so the user must choose", () => {
    expect(templateSelectionFor(undefined)).toEqual({ mode: "unselected", templateId: null, remember: false });
  });

  it("restores a remembered specific template as a 'remembered' banner, keeping its category", () => {
    expect(templateSelectionFor({ mode: "pick", templateId: 5, templateName: "T5", quantityCategory: "mass" })).toEqual(
      {
        mode: "remembered",
        templateId: 5,
        templateName: "T5",
        quantityCategory: "mass",
        remember: true,
      },
    );
  });

  it("restores a remembered 'none' / 'fromSample' choice as that radio directly", () => {
    expect(templateSelectionFor({ mode: "none", templateId: null }).mode).toBe("none");
    expect(templateSelectionFor({ mode: "fromSample", templateId: null }).mode).toBe("fromSample");
  });
});

describe("templateStepValid", () => {
  it("is invalid while nothing is chosen ('unselected'), so Next stays disabled", () => {
    expect(templateStepValid({ mode: "unselected", templateId: null })).toBe(false);
  });

  it("requires a picked template to have finished validating (id set)", () => {
    expect(templateStepValid({ mode: "pick", templateId: null })).toBe(false);
    expect(templateStepValid({ mode: "pick", templateId: 5 })).toBe(true);
  });

  it("is valid for none / fromSample / remembered", () => {
    expect(templateStepValid({ mode: "none", templateId: null })).toBe(true);
    expect(templateStepValid({ mode: "fromSample", templateId: null })).toBe(true);
    expect(templateStepValid({ mode: "remembered", templateId: 5 })).toBe(true);
  });
});
