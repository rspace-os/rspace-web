import { describe, expect, test, vi } from "vitest";
import type FieldModel from "../../FieldModel";
import { makeMockInstrumentTemplate } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

const twoFields = [
  {
    id: 10,
    globalId: "IF10",
    name: "Serial",
    type: "text" as const,
    content: null,
    columnIndex: 1,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 11,
    globalId: "IF11",
    name: "Frequency",
    type: "number" as const,
    content: "50",
    columnIndex: 2,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
];

describe("InstrumentTemplateModel.removeCustomField", () => {
  describe("when the template is unsaved (id is null)", () => {
    test("removes the field at the given index", () => {
      const template = makeMockInstrumentTemplate({ id: null, globalId: null, fields: twoFields });
      template.removeCustomField(10, 0);
      expect(template.fields).toHaveLength(1);
      expect(template.fields[0].name).toBe("Frequency");
    });

    test("field count decreases by one", () => {
      const template = makeMockInstrumentTemplate({ id: null, globalId: null, fields: twoFields });
      template.removeCustomField(10, 0);
      expect(template.fields).toHaveLength(1);
    });
  });

  describe("when the field has no id (newly added)", () => {
    test("removes the field by index even when the template is saved", () => {
      const template = makeMockInstrumentTemplate({ id: 1, globalId: "NT1", fields: [] });
      template.addField({
        name: "New Field",
        type: "text",
        content: null,
        selectedOptions: null,
        definition: null,
        columnIndex: 1,
        attachment: null,
        mandatory: false,
      });
      expect(template.fields).toHaveLength(1);
      template.removeCustomField(null, 0);
      expect(template.fields).toHaveLength(0);
    });
  });

  describe("when both the template and the field have ids", () => {
    test("marks the field with deleteFieldRequest=true rather than splicing", () => {
      const template = makeMockInstrumentTemplate({ id: 1, globalId: "NT1", fields: twoFields });
      template.removeCustomField(10, 0);
      expect(template.fields).toHaveLength(2);
      const removed = template.fields.find((f) => f.name === "Serial") as FieldModel;
      expect(removed.deleteFieldRequest).toBe(true);
    });

    test("does not mark the other field for deletion", () => {
      const template = makeMockInstrumentTemplate({ id: 1, globalId: "NT1", fields: twoFields });
      template.removeCustomField(10, 0);
      const kept = template.fields.find((f) => f.name === "Frequency") as FieldModel;
      expect(kept.deleteFieldRequest).toBe(false);
    });
  });
});
