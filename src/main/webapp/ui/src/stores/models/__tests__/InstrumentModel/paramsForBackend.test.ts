import { describe, expect, test, vi } from "vitest";
import { makeMockInstrument } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentModel.paramsForBackend", () => {
  describe("JSON serialisability", () => {
    test("is JSON-serialisable for a minimal instrument", () => {
      const instrument = makeMockInstrument();
      expect(JSON.stringify(instrument.paramsForBackend)).toEqual(expect.any(String));
    });

    test("is JSON-serialisable when templateId is set", () => {
      const instrument = makeMockInstrument({ templateId: 5 });
      expect(JSON.stringify(instrument.paramsForBackend)).toEqual(expect.any(String));
    });

    test("is JSON-serialisable when fields are editable", () => {
      const instrument = makeMockInstrument({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Volume",
            type: "text",
            content: "100ml",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      instrument.currentlyEditableFields.add("fields");
      expect(JSON.stringify(instrument.paramsForBackend)).toEqual(expect.any(String));
    });
  });

  describe("templateId", () => {
    test("includes templateId when set", () => {
      const instrument = makeMockInstrument({ templateId: 7 });
      const params = instrument.paramsForBackend;
      expect(params.templateId).toBe(7);
    });

    test("omits templateId when null", () => {
      const instrument = makeMockInstrument({ templateId: null });
      const params = instrument.paramsForBackend;
      expect(params).not.toHaveProperty("templateId");
    });
  });

  describe("fields", () => {
    test("omits fields when 'fields' is not in currentlyEditableFields", () => {
      const instrument = makeMockInstrument({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Serial",
            type: "text",
            content: null,
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      expect(instrument.paramsForBackend).not.toHaveProperty("fields");
    });

    test("includes fields when 'fields' is in currentlyEditableFields", () => {
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
      instrument.currentlyEditableFields.add("fields");
      const params = instrument.paramsForBackend as { fields?: Array<Record<string, unknown>> };
      expect(params.fields).toHaveLength(1);
    });
  });
});
