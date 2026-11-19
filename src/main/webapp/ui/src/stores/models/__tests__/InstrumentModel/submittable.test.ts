import { describe, expect, test, vi } from "vitest";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import type { FieldModelAttrs } from "../../FieldModel";
import { LANDING_PAGE_FIELD_NAME } from "../../InstrumentModel";
import { makeMockInstrumentTemplate } from "../InstrumentTemplateModel/mocking";
import { makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: { query: vi.fn(), get: vi.fn() } }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn(), setPageNavigationConfirmation: vi.fn(), setDirty: vi.fn() },
  }),
}));

const WEIGHT_KEY = "6f1c2e0a-0000-4000-8000-000000000031";

/** A number source and a mandatory, empty permit shown while it is greater than 2. */
const weightFields = (weight: string): Array<FieldModelAttrs> => [
  {
    id: 1,
    globalId: "IF1",
    name: "Weight",
    type: "number",
    content: weight,
    selectedOptions: null,
    columnIndex: 1,
    attachment: null,
    mandatory: false,
    fieldKey: WEIGHT_KEY,
  },
  {
    id: 2,
    globalId: "IF2",
    name: "Heavy goods permit",
    type: "string",
    content: "",
    selectedOptions: null,
    columnIndex: 2,
    attachment: null,
    mandatory: true,
    fieldKey: "6f1c2e0a-0000-4000-8000-000000000032",
    displayRule: createDisplayRule(WEIGHT_KEY, "GREATER_THAN", ["2"]),
  },
];

describe("InstrumentModel.submittable", () => {
  test("refuses an empty mandatory field once a typed number shows it", () => {
    const instrument = makeMockInstrument({ fields: weightFields("1") });
    expect(instrument.submittable.isOk).toBe(true);

    instrument.fields[0].setAttributesDirty({ content: "4" });
    expect(instrument.submittable.isOk).toBe(false);
  });

  test("allows an empty mandatory field once a typed number hides it", () => {
    const instrument = makeMockInstrument({ fields: weightFields("4") });
    expect(instrument.submittable.isOk).toBe(false);

    instrument.fields[0].setAttributesDirty({ content: "1" });
    expect(instrument.submittable.isOk).toBe(true);
  });

  test("allows the blank Landing page of an instrument made from a template", async () => {
    const template = makeMockInstrumentTemplate({
      fields: [
        {
          id: 1,
          globalId: "IF1",
          name: LANDING_PAGE_FIELD_NAME,
          type: "uri",
          content: "https://lab.example.org/original",
          selectedOptions: null,
          columnIndex: 1,
          attachment: null,
          mandatory: true,
        },
        {
          id: 2,
          globalId: "IF2",
          name: "Serial number",
          type: "string",
          content: "SN-1",
          selectedOptions: null,
          columnIndex: 2,
          attachment: null,
          mandatory: true,
        },
      ],
    });
    vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
    const instrument = makeMockInstrument({ id: null, globalId: null });
    await instrument.setTemplate(template);
    expect(instrument.submittable.isOk).toBe(true);

    instrument.fields[1].setAttributesDirty({ content: "" });
    expect(instrument.submittable.isOk).toBe(false);
  });
});
