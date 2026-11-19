import { runInAction } from "mobx";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import InvApiService from "../../../../common/InvApiService";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import type { FieldModelAttrs } from "../../FieldModel";
import Search from "../../Search";
import { fieldAttrs } from "../FieldModel/mocking";
import { makeMockInstrument } from "../InstrumentModel/mocking";
import { makeMockSample } from "../SampleModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: { post: vi.fn(), query: vi.fn(), get: vi.fn() },
}));

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    trackingStore: { trackEvent: vi.fn() },
    peopleStore: { getUser: vi.fn().mockResolvedValue(null), currentUser: null },
    uiStore: {
      addAlert: vi.fn(),
      removeAlert: vi.fn(),
      setDirty: vi.fn(),
      setPageNavigationConfirmation: vi.fn(),
    },
    unitStore: { getUnit: () => ({ label: "ml" }), assertValidUnitId: () => {} },
    searchStore: { activeResult: null, search: { activeResult: null } },
  }),
}));

vi.mock("../../../stores/SearchStore", () => ({ default: class {} })); // break import cycle

const SOURCE_KEY = "6f1c2e0a-0000-4000-8000-000000000001";
const TARGET_KEY = "6f1c2e0a-0000-4000-8000-000000000002";
const RULE_JSON = { sourceFieldKey: SOURCE_KEY, operator: "IS_ANY_OF", values: ["Yes"] };

/** A saved radio field and a saved field that has a rule reading it, as the API returns them. */
const savedFieldsWithARule = (): Array<FieldModelAttrs> => [
  fieldAttrs({
    id: 1,
    globalId: "SF1",
    type: "radio",
    fieldKey: SOURCE_KEY,
    definition: { options: ["Yes", "No"] },
    selectedOptions: [],
  }),
  fieldAttrs({
    id: 2,
    globalId: "SF2",
    type: "string",
    fieldKey: TARGET_KEY,
    displayRule: createDisplayRule(SOURCE_KEY, "IS_ANY_OF", ["Yes"]),
  }),
];

function postedFields(): Array<Record<string, unknown>> {
  const post = InvApiService.post;
  const [, body] = vi.mocked(post).mock.calls[0];
  return (body as { fields: Array<Record<string, unknown>> }).fields;
}

describe("creating a template from a saved record", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // the request is what is under test; the response fails, so nothing more runs
    vi.mocked(InvApiService.post).mockRejectedValue(new Error("not under test"));
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  test("a template made from a sample keeps the sample's rules", async () => {
    const sample = makeMockSample({ id: 1, globalId: "SA1", fields: savedFieldsWithARule() });
    runInAction(() => {
      sample.infoLoaded = true;
    });

    await expect(
      new Search({ factory: new AlwaysNewFactory() }).createTemplateFromSample("Template", sample, new Set()),
    ).rejects.toThrow();

    expect(vi.mocked(InvApiService.post)).toHaveBeenCalledWith("sampleTemplates", expect.anything());
    expect(postedFields().find((field) => field.fieldKey === TARGET_KEY)).toHaveProperty("displayRule", RULE_JSON);
  });

  test("an instrument template made from an instrument keeps the instrument's rules", async () => {
    const instrument = makeMockInstrument({ id: 1, globalId: "IN1", fields: savedFieldsWithARule() });
    runInAction(() => {
      instrument.infoLoaded = true;
    });

    await expect(
      new Search({ factory: new AlwaysNewFactory() }).createInstrumentTemplateFromInstrument(
        "Template",
        instrument,
        new Set(),
      ),
    ).rejects.toThrow();

    expect(vi.mocked(InvApiService.post)).toHaveBeenCalledWith("instrumentTemplates", expect.anything());
    expect(postedFields().find((field) => field.fieldKey === TARGET_KEY)).toHaveProperty("displayRule", RULE_JSON);
  });
});
