import { describe, expect, test, vi } from "vitest";
import { makeMockInstrumentTemplate } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentTemplateModel.fieldNamesInUse", () => {
  test("does not include custom names when there are no fields or extra fields", () => {
    const template = makeMockInstrumentTemplate();
    // The base class always contributes 5 reserved names; own fields add on top of that
    const baseNames = ["Name", "Description", "Preview Image", "Tags", "Attachments"];
    expect(template.fieldNamesInUse).toEqual(baseNames);
  });

  test("includes the names of structured fields", () => {
    const template = makeMockInstrumentTemplate({
      fields: [
        {
          id: 1,
          globalId: "IF1",
          name: "Serial",
          type: "text",
          content: null,
          columnIndex: 1,
          definition: null,
          selectedOptions: null,
          attachment: null,
          mandatory: false,
        },
        {
          id: 2,
          globalId: "IF2",
          name: "Frequency",
          type: "number",
          content: null,
          columnIndex: 2,
          definition: null,
          selectedOptions: null,
          attachment: null,
          mandatory: false,
        },
      ],
    });
    expect(template.fieldNamesInUse).toContain("Serial");
    expect(template.fieldNamesInUse).toContain("Frequency");
  });

  test("does not include empty string in names even when a field has an empty name", () => {
    const template = makeMockInstrumentTemplate({
      fields: [
        {
          id: 1,
          globalId: "IF1",
          name: "",
          type: "text",
          content: null,
          columnIndex: 1,
          definition: null,
          selectedOptions: null,
          attachment: null,
          mandatory: false,
        },
      ],
    });
    expect(template.fieldNamesInUse).not.toContain("");
  });

  test("includes extra field names alongside structured field names", () => {
    const template = makeMockInstrumentTemplate({
      fields: [
        {
          id: 1,
          globalId: "IF1",
          name: "Serial",
          type: "text",
          content: null,
          columnIndex: 1,
          definition: null,
          selectedOptions: null,
          attachment: null,
          mandatory: false,
        },
      ],
      extraFields: [
        {
          id: 2,
          globalId: "EF2",
          name: "Lab",
          lastModified: null,
          type: "text",
          content: "Room 4B",
          parentGlobalId: "NT1",
        },
      ],
    });
    expect(template.fieldNamesInUse).toContain("Serial");
    expect(template.fieldNamesInUse).toContain("Lab");
  });

  test("dynamically reflects fields added via addField", () => {
    const template = makeMockInstrumentTemplate();
    expect(template.fieldNamesInUse).not.toContain("Calibration Date");
    template.addField({
      name: "Calibration Date",
      type: "date",
      content: null,
      selectedOptions: null,
      definition: null,
      columnIndex: 1,
      attachment: null,
      mandatory: false,
    });
    expect(template.fieldNamesInUse).toContain("Calibration Date");
  });
});
