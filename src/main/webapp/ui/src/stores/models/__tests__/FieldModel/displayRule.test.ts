import { describe, expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { createDisplayRule, type FieldDisplayRule } from "@/modules/common/field-rules/displayRule";
import type FieldModel from "../../FieldModel";
import { makeMockTemplate } from "../TemplateModel/mocking";
import { makeMockField } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    uiStore: { setPageNavigationConfirmation: () => {}, setDirty: () => {} },
    unitStore: { getUnit: () => ({ label: "ml" }), assertValidUnitId: () => {} },
  }),
}));

const KEY = "6f1c2e0a-0000-4000-8000-000000000001";
const RULE_JSON = { sourceFieldKey: KEY, operator: "IS_ANY_OF", values: ["Yes"] };
/** Attrs built from API JSON carry the rule unparsed, whatever their type says. */
const fromApi = (json: object) => json as FieldDisplayRule;

describe("FieldModel display rules", () => {
  it("sends neither a key nor a rule for a legacy field that has none", () => {
    const params = makeMockField({ type: "string" }).paramsForBackend;
    expect(params).not.toHaveProperty("fieldKey");
    expect(params).not.toHaveProperty("displayRule");
  });

  it("sends the rule as the API's JSON object once this edit changes it", () => {
    const field = makeMockField({ type: "string", fieldKey: KEY, displayRule: fromApi(RULE_JSON) });
    expect(field.displayRule).toEqual(createDisplayRule(KEY, "IS_ANY_OF", ["Yes"]));
    field.setDisplayRule(createDisplayRule(KEY, "IS_NONE_OF", ["Yes"]));
    expect(field.paramsForBackend).toMatchObject({
      fieldKey: KEY,
      displayRule: { ...RULE_JSON, operator: "IS_NONE_OF" },
    });
  });

  it("leaves out a loaded rule this edit did not change, so a stale tab cannot undo another author's change", () => {
    const field = makeMockField({ type: "string", fieldKey: KEY, displayRule: fromApi(RULE_JSON) });
    expect(field.paramsForBackend).not.toHaveProperty("displayRule");
    // setting the same condition again is no change either
    field.setDisplayRule(createDisplayRule(KEY, "IS_ANY_OF", ["Yes"]));
    expect(field.paramsForBackend).not.toHaveProperty("displayRule");
  });

  it("sends every rule of a record being created", () => {
    const template = makeMockTemplate({ id: null, globalId: null });
    template.addField({ type: "string", selectedOptions: [], columnIndex: null, attachment: null, mandatory: false });
    const field = template.fields[0] as FieldModel;
    field.setDisplayRule(createDisplayRule(KEY, "HAS_VALUE", []));
    expect(field.paramsForBackend).toHaveProperty("displayRule", { sourceFieldKey: KEY, operator: "HAS_VALUE" });
  });

  it("sends an explicit null when a saved rule is cleared, since an absent rule means unchanged", () => {
    const field = makeMockField({ type: "string", displayRule: fromApi(RULE_JSON) });
    field.setDisplayRule(null);
    expect(field.paramsForBackend).toHaveProperty("displayRule", null);
  });

  it("shows a field whose stored rule cannot be read", () => {
    const restore = silenceConsole(["warn"], ["Ignoring an unreadable display rule"]);
    const field = makeMockField({ type: "string", displayRule: fromApi({ operator: "SOMETIMES" }) });
    restore();
    expect(field.displayRule).toBeNull();
  });

  it("gives each field added to a template its own key, but not a field built from a record", () => {
    const template = makeMockTemplate();
    template.addField({ type: "string", selectedOptions: [], columnIndex: null, attachment: null, mandatory: false });
    template.addField({ type: "string", selectedOptions: [], columnIndex: null, attachment: null, mandatory: false });
    const [first, second] = template.fields.map((field) => field.fieldKey);
    expect(first).toEqual(expect.any(String));
    expect(second).not.toBe(first);
    expect(makeMockField({ type: "string" }).fieldKey).toBeNull();
  });
});
