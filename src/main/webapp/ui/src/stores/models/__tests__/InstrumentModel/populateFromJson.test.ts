import { describe, expect, test, vi } from "vitest";
import { instrumentAttrs, makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentModel.populateFromJson", () => {
  describe("fields", () => {
    test("replaces existing fields when called a second time", () => {
      const instrument = makeMockInstrument({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Serial",
            type: "text",
            content: "SN-001",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      expect(instrument.fields).toHaveLength(1);

      instrument.populateFromJson(instrument.factory, instrumentAttrs(), {});
      expect(instrument.fields).toHaveLength(0);
    });

    test("preserves field names and types after repopulation", () => {
      const instrument = makeMockInstrument();
      instrument.populateFromJson(
        instrument.factory,
        instrumentAttrs({
          fields: [
            {
              id: 5,
              globalId: "IF5",
              name: "Frequency",
              type: "number",
              content: "50",
              selectedOptions: null,
              definition: null,
              columnIndex: 1,
              attachment: null,
              mandatory: false,
            },
          ],
        }),
        {},
      );
      expect(instrument.fields).toHaveLength(1);
      expect(instrument.fields[0].name).toBe("Frequency");
    });
  });

  describe("templateId and templateVersion", () => {
    test("updates templateId when present in new params", () => {
      const instrument = makeMockInstrument();
      expect(instrument.templateId).toBeNull();

      instrument.populateFromJson(instrument.factory, instrumentAttrs({ templateId: 7 }), {});
      expect(instrument.templateId).toBe(7);
    });

    test("updates templateVersion when present in new params", () => {
      const instrument = makeMockInstrument();
      instrument.populateFromJson(instrument.factory, instrumentAttrs({ templateVersion: 2 }), {});
      expect(instrument.templateVersion).toBe(2);
    });

    test("does not change templateId when absent from new params", () => {
      const instrument = makeMockInstrument({ templateId: 3 });
      const attrsWithoutTemplateId = instrumentAttrs();
      delete (attrsWithoutTemplateId as Partial<typeof attrsWithoutTemplateId>).templateId;
      instrument.populateFromJson(instrument.factory, attrsWithoutTemplateId, {});
      expect(instrument.templateId).toBe(3);
    });
  });

  describe("createOptionsParametersState", () => {
    test("is initialised with an empty name", () => {
      const instrument = makeMockInstrument();
      expect(instrument.createOptionsParametersState.name.value).toBe("");
    });

    test("copyFieldContent includes structured fields", () => {
      const instrument = makeMockInstrument({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Batch",
            type: "text",
            content: "B1",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      const { copyFieldContent } = instrument.createOptionsParametersState.fields;
      const batchEntry = copyFieldContent.find((f) => f.name === "Batch");
      expect(batchEntry).toBeDefined();
    });

    test("copyFieldContent includes extra fields", () => {
      const instrument = makeMockInstrument({
        extraFields: [
          {
            id: 2,
            globalId: "EF2",
            name: "Lab Note",
            lastModified: null,
            type: "text",
            content: "calibrated",
            parentGlobalId: "IN1",
          },
        ],
      });
      const { copyFieldContent } = instrument.createOptionsParametersState.fields;
      const noteEntry = copyFieldContent.find((f) => f.name === "Lab Note");
      expect(noteEntry).toBeDefined();
    });

    test("re-initialises copyFieldContent on each populateFromJson call", () => {
      const instrument = makeMockInstrument();
      expect(instrument.createOptionsParametersState.fields.copyFieldContent).toHaveLength(0);

      instrument.populateFromJson(
        instrument.factory,
        instrumentAttrs({
          fields: [
            {
              id: 1,
              globalId: "IF1",
              name: "New Field",
              type: "text",
              content: null,
              selectedOptions: null,
              definition: null,
              columnIndex: 1,
              attachment: null,
              mandatory: false,
            },
          ],
        }),
        {},
      );
      expect(instrument.createOptionsParametersState.fields.copyFieldContent).toHaveLength(1);
    });
  });
});
