import fc from "fast-check";
import { describe, expect, test, vi } from "vitest";
import { makeMockInstrumentTemplate } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentTemplateModel.validate", () => {
  describe("All field names should be unique", () => {
    test("Returns false when two structured fields share the same name.", () => {
      fc.assert(
        fc.property(fc.string({ minLength: 1 }), (name) => {
          const template = makeMockInstrumentTemplate({
            fields: [
              {
                type: "text",
                name,
                selectedOptions: null,
                columnIndex: 1,
                attachment: null,
                mandatory: false,
              },
              {
                type: "text",
                name,
                selectedOptions: null,
                columnIndex: 2,
                attachment: null,
                mandatory: false,
              },
            ],
          });
          expect(template.validate().isOk).toBe(false);
        }),
      );
    });

    test("Returns false when a structured field and an extra field share the same name.", () => {
      fc.assert(
        fc.property(fc.string({ minLength: 1 }), (name) => {
          const template = makeMockInstrumentTemplate({
            fields: [
              {
                type: "text",
                name,
                selectedOptions: null,
                columnIndex: 1,
                attachment: null,
                mandatory: false,
              },
            ],
            extraFields: [
              {
                id: 1,
                globalId: "EF1",
                name,
                lastModified: null,
                type: "text",
                content: "",
                parentGlobalId: "NT1",
              },
            ],
          });
          expect(template.validate().isOk).toBe(false);
        }),
      );
    });
  });

  describe("Valid instrument templates", () => {
    test("Returns true for a template with no fields.", () => {
      const template = makeMockInstrumentTemplate();
      expect(template.validate().isOk).toBe(true);
    });

    test("Returns true for a template with uniquely named fields.", () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            type: "text",
            name: "Serial",
            selectedOptions: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
          {
            type: "number",
            name: "Frequency",
            selectedOptions: null,
            columnIndex: 2,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      expect(template.validate().isOk).toBe(true);
    });
  });

  describe("Name validation (when name is editable)", () => {
    test("Returns false when name is empty.", () => {
      const template = makeMockInstrumentTemplate({ name: "" });
      // Name validation is gated on isFieldEditable; make it active
      template.currentlyEditableFields.add("name");
      expect(template.validate().isOk).toBe(false);
    });

    test("Returns false when name is a single character.", () => {
      const template = makeMockInstrumentTemplate({ name: "A" });
      template.currentlyEditableFields.add("name");
      expect(template.validate().isOk).toBe(false);
    });
  });
});
