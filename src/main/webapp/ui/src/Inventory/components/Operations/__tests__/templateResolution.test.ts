import { describe, expect, it, vi } from "vitest";
import {
  resolveTemplateId,
  selectionToRemember,
  templateDefaultsAfterPerform,
  templateSelectionBlock,
  templateSelectionFor,
  templateStepValid,
} from "../templateResolution";

describe("resolveTemplateId", () => {
  const createTemplate = () => Promise.resolve(999);

  it("returns null when no template is chosen", async () => {
    expect(
      await resolveTemplateId({
        mode: "none",
        pickedTemplateId: 5,
        originSampleTemplateId: 7,
        createTemplate,
      }),
    ).toBeNull();
  });

  it("returns the picked template id for an explicit choice", async () => {
    expect(
      await resolveTemplateId({
        mode: "pick",
        pickedTemplateId: 5,
        originSampleTemplateId: 7,
        createTemplate,
      }),
    ).toBe(5);
  });

  it("reuses the origin sample's existing template without creating a new one (option c)", async () => {
    const create = vi.fn(() => Promise.resolve(999));
    const id = await resolveTemplateId({
      mode: "fromSample",
      pickedTemplateId: null,
      originSampleTemplateId: 42,
      createTemplate: create,
    });
    expect(id).toBe(42);
    expect(create).not.toHaveBeenCalled();
  });

  it("uses the remembered template's id directly, without creating one", async () => {
    const create = vi.fn(() => Promise.resolve(999));
    const id = await resolveTemplateId({
      mode: "remembered",
      pickedTemplateId: 88,
      originSampleTemplateId: 42,
      createTemplate: create,
    });
    expect(id).toBe(88);
    expect(create).not.toHaveBeenCalled();
  });

  it("creates a template exactly once when the origin sample has none (option c)", async () => {
    const create = vi.fn(() => Promise.resolve(123));
    const id = await resolveTemplateId({
      mode: "fromSample",
      pickedTemplateId: null,
      originSampleTemplateId: null,
      createTemplate: create,
    });
    expect(id).toBe(123);
    expect(create).toHaveBeenCalledTimes(1);
  });
});

describe("templateDefaultsAfterPerform", () => {
  it("removes only this operation's remembered choice when 'remember' is off, leaving others intact", () => {
    const current = {
      derive: { mode: "pick" as const, templateId: 5 },
      cryopreserve: { mode: "fromSample" as const, templateId: null },
    };
    const next = templateDefaultsAfterPerform(current, "derive", { mode: "pick", templateId: 5, remember: false });
    expect(next).toEqual({ cryopreserve: { mode: "fromSample", templateId: null } });
  });

  it("stores a picked template (with its id) when 'remember' is on", () => {
    const next = templateDefaultsAfterPerform({}, "derive", { mode: "pick", templateId: 5, remember: true });
    expect(next).toEqual({ derive: { mode: "pick", templateId: 5 } });
  });

  it("stores the template name alongside a picked template so it can be shown next time", () => {
    const next = templateDefaultsAfterPerform({}, "derive", {
      mode: "pick",
      templateId: 5,
      templateName: "My Template",
      remember: true,
    });
    expect(next).toEqual({ derive: { mode: "pick", templateId: 5, templateName: "My Template" } });
  });

  it("re-stores a remembered template as a plain pick so it is restored next time", () => {
    const next = templateDefaultsAfterPerform({}, "derive", {
      mode: "remembered",
      templateId: 5,
      templateName: "My Template",
      remember: true,
    });
    expect(next).toEqual({ derive: { mode: "pick", templateId: 5, templateName: "My Template" } });
  });

  it("stores a non-pick choice without a templateId when 'remember' is on", () => {
    const next = templateDefaultsAfterPerform({ cryopreserve: { mode: "none", templateId: null } }, "derive", {
      mode: "fromSample",
      templateId: 5,
      remember: true,
    });
    expect(next).toEqual({
      cryopreserve: { mode: "none", templateId: null },
      derive: { mode: "fromSample", templateId: null },
    });
  });
});

describe("selectionToRemember", () => {
  it("remembers a fromSample-created template as a specific pick so it is reused, not recreated", () => {
    const result = selectionToRemember({
      selection: { mode: "fromSample", templateId: null, remember: true },
      resolvedTemplateId: 77,
      originHadTemplate: false,
      createdTemplateName: "S1 (template)",
    });
    expect(result).toEqual({ mode: "pick", templateId: 77, templateName: "S1 (template)", remember: true });
  });

  it("leaves the selection unchanged when the parent already had a template (no duplication anyway)", () => {
    const selection = { mode: "fromSample" as const, templateId: null, remember: true };
    expect(
      selectionToRemember({ selection, resolvedTemplateId: 42, originHadTemplate: true, createdTemplateName: "x" }),
    ).toBe(selection);
  });

  it("leaves a plain pick unchanged", () => {
    const selection = { mode: "pick" as const, templateId: 5, templateName: "T", remember: true };
    expect(
      selectionToRemember({ selection, resolvedTemplateId: 5, originHadTemplate: false, createdTemplateName: "x" }),
    ).toBe(selection);
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

  it("blocks and names mandatory fields that lack a default (option a)", () => {
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

  it("restores a remembered specific template as a 'remembered' banner", () => {
    expect(templateSelectionFor({ mode: "pick", templateId: 5, templateName: "T5" })).toEqual({
      mode: "remembered",
      templateId: 5,
      templateName: "T5",
      remember: true,
    });
  });

  it("restores a remembered 'none' / 'fromSample' choice as that radio directly", () => {
    expect(templateSelectionFor({ mode: "none", templateId: null })).toEqual({
      mode: "none",
      templateId: null,
      templateName: undefined,
      remember: true,
    });
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
