import { describe, expect, test, vi } from "vitest";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import type { FieldModelAttrs } from "../../FieldModel";
import { LANDING_PAGE_FIELD_NAME } from "../../InstrumentModel";
import { makeMockInstrumentTemplate } from "../InstrumentTemplateModel/mocking";
import { instrumentAttrs, makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: { query: vi.fn(), get: vi.fn() } }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn(), setPageNavigationConfirmation: vi.fn(), setDirty: vi.fn() },
  }),
}));

const WEIGHT_KEY = "6f1c2e0a-0000-4000-8000-000000000031";

/** A number source and a mandatory permit shown while it is greater than 2. */
const weightFields = (weight: string, permit = ""): Array<FieldModelAttrs> => [
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
    content: permit,
    selectedOptions: null,
    columnIndex: 2,
    attachment: null,
    mandatory: true,
    fieldKey: "6f1c2e0a-0000-4000-8000-000000000032",
    displayRule: createDisplayRule(WEIGHT_KEY, "GREATER_THAN", ["2"]),
  },
];

/** A mandatory Landing page holding `landingPage`, and a mandatory serial number. */
const landingPageFields = (landingPage: string, serial = "SN-1"): Array<FieldModelAttrs> => [
  {
    id: 1,
    globalId: "IF1",
    name: LANDING_PAGE_FIELD_NAME,
    type: "uri",
    content: landingPage,
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
    content: serial,
    selectedOptions: null,
    columnIndex: 2,
    attachment: null,
    mandatory: true,
  },
];

describe("InstrumentModel.submittable", () => {
  test("refuses an empty mandatory field once a typed number shows it", () => {
    const instrument = makeMockInstrument({ fields: weightFields("1") });
    expect(instrument.submittable.isOk).toBe(true);

    instrument.fields[0].setAttributesDirty({ content: "4" });
    expect(instrument.submittable.isOk).toBe(false);
  });

  test("allows an empty mandatory field on a new instrument once a typed number hides it", () => {
    const instrument = makeMockInstrument({ id: null, globalId: null, fields: weightFields("4") });
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

  test.each(["", "   "])("refuses clearing a Landing page that was filled in when loaded to %j", (cleared) => {
    const instrument = makeMockInstrument({ fields: landingPageFields("https://lab.example.org/lsm980") });
    expect(instrument.requiresValue(instrument.fields[0])).toBe(true);

    instrument.fields[0].setAttributesDirty({ content: cleared });
    expect(instrument.submittable.isOk).toBe(false);
  });

  test("allows an unrelated edit once a reload finds the Landing page blank", () => {
    const instrument = makeMockInstrument({ fields: landingPageFields("https://lab.example.org/lsm980") });
    // a save that clears the Landing page with the identifier that wrote it refetches the instrument
    instrument.populateFromJson(new AlwaysNewFactory(), instrumentAttrs({ fields: landingPageFields("") }));

    instrument.setAttributesDirty({ name: "Renamed" });
    expect(instrument.submittable.isOk).toBe(true);
  });

  test("allows editing other fields while the Landing page stays blank as it was loaded", () => {
    const instrument = makeMockInstrument({ fields: landingPageFields("") });

    instrument.fields[1].setAttributesDirty({ content: "SN-2" });
    expect(instrument.submittable.isOk).toBe(true);
  });

  test("exempts only the first field named Landing page on a new instrument", () => {
    const [landingPage, serial] = landingPageFields("");
    const instrument = makeMockInstrument({
      id: null,
      globalId: null,
      fields: [landingPage, serial, { ...landingPage, id: 3, globalId: "IF3", name: " landing PAGE ", columnIndex: 3 }],
    });
    expect(instrument.submittable.isOk).toBe(false);

    instrument.fields[2].setAttributesDirty({ content: "https://lab.example.org/lsm980" });
    expect(instrument.submittable.isOk).toBe(true);
  });

  test("allows an unrelated edit while a shown mandatory field with a rule stays empty as loaded", () => {
    const instrument = makeMockInstrument({ fields: weightFields("4") });

    instrument.setAttributesDirty({ name: "Renamed" });
    expect(instrument.submittable.isOk).toBe(true);
  });

  test("refuses emptying a shown mandatory field with a rule", () => {
    const instrument = makeMockInstrument({ fields: weightFields("4", "P-1") });

    instrument.fields[1].setAttributesDirty({ content: "" });
    expect(instrument.submittable.isOk).toBe(false);
  });

  test("allows an unrelated edit while a mandatory field without a rule stays empty as loaded", () => {
    const instrument = makeMockInstrument({ fields: landingPageFields("https://lab.example.org/lsm980", "") });

    instrument.setAttributesDirty({ name: "Renamed" });
    expect(instrument.submittable.isOk).toBe(true);
  });

  /*
   * Refusing to remove a mandatory link from a field without a display rule is
   * deliberate. The server does not check it, and the client stays stricter
   * than the server rather than looser.
   */
  test("keeps a mandatory Filestore link as a value: Save stays enabled until the link is removed", () => {
    const instrument = makeMockInstrument({
      fields: [
        {
          id: 1,
          globalId: "IF1",
          name: "Raw data",
          type: "link",
          content: null,
          selectedOptions: null,
          columnIndex: 1,
          attachment: null,
          mandatory: true,
          link: {
            relationType: "IsDocumentedBy",
            targetGlobalId: "",
            versionPin: null,
            filestoreTarget: { filestoreId: 3, remotePath: "/scans/lsm980.tif" },
          },
        },
      ],
    });

    instrument.setAttributesDirty({ name: "Renamed" });
    expect(instrument.submittable.isOk).toBe(true);

    instrument.fields[0].setAttributesDirty({ link: null });
    expect(instrument.submittable.isOk).toBe(false);
  });
});
