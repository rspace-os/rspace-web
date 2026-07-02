import { describe, expect, test, vi } from "vitest";
import { makeMockInstrumentTemplate } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

const textFieldAttrs = (name: string, columnIndex: number = 1) => ({
  name,
  type: "text" as const,
  content: null,
  selectedOptions: null,
  definition: null,
  columnIndex,
  attachment: null,
  mandatory: false,
});

describe("InstrumentTemplateModel.addField", () => {
  test("increases field count by one", () => {
    const template = makeMockInstrumentTemplate();
    expect(template.fields).toHaveLength(0);
    template.addField(textFieldAttrs("Serial"));
    expect(template.fields).toHaveLength(1);
  });

  test("new field has the correct name", () => {
    const template = makeMockInstrumentTemplate();
    template.addField(textFieldAttrs("Frequency"));
    expect(template.fields[0].name).toBe("Frequency");
  });

  test("new field has the correct type", () => {
    const template = makeMockInstrumentTemplate();
    template.addField({
      name: "Count",
      type: "number",
      content: null,
      selectedOptions: null,
      definition: null,
      columnIndex: 1,
      attachment: null,
      mandatory: false,
    });
    expect(template.fields[0].type).toBe("number");
  });

  test("successive addField calls accumulate fields", () => {
    const template = makeMockInstrumentTemplate();
    template.addField(textFieldAttrs("A", 1));
    template.addField(textFieldAttrs("B", 2));
    template.addField(textFieldAttrs("C", 3));
    expect(template.fields).toHaveLength(3);
  });

  test("new field is appended after existing fields", () => {
    const template = makeMockInstrumentTemplate({
      fields: [
        {
          id: 1,
          globalId: "IF1",
          name: "First",
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
    template.addField(textFieldAttrs("Second", 2));
    expect(template.fields).toHaveLength(2);
    expect(template.fields[1].name).toBe("Second");
  });

  test("adding a radio field preserves its options", () => {
    const template = makeMockInstrumentTemplate();
    template.addField({
      name: "Status",
      type: "radio",
      content: null,
      selectedOptions: [],
      definition: { options: ["Active", "Retired"] },
      columnIndex: 1,
      attachment: null,
      mandatory: false,
    });
    expect(template.fields[0].name).toBe("Status");
  });
});
