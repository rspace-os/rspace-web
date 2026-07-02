import { describe, expect, test, vi } from "vitest";
import { makeMockInstrumentTemplate } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentTemplateModel.paramsForBackend", () => {
  describe("JSON serialisability", () => {
    test("is JSON-serialisable for a minimal instrument template", () => {
      const template = makeMockInstrumentTemplate();
      expect(JSON.stringify(template.paramsForBackend)).toEqual(expect.any(String));
    });

    test("is JSON-serialisable when fields are editable", () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Serial",
            type: "text",
            content: "SN-001",
            columnIndex: 1,
            definition: null,
            selectedOptions: null,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      template.currentlyEditableFields.add("fields");
      expect(JSON.stringify(template.paramsForBackend)).toEqual(expect.any(String));
    });

    test("is JSON-serialisable when extra fields are editable", () => {
      const template = makeMockInstrumentTemplate({
        extraFields: [
          {
            id: 1,
            globalId: "EF1",
            name: "Lab",
            lastModified: null,
            type: "text",
            content: "Room 4B",
            parentGlobalId: "NT1",
          },
        ],
      });
      template.currentlyEditableFields.add("extraFields");
      expect(JSON.stringify(template.paramsForBackend)).toEqual(expect.any(String));
    });
  });

  describe("fields", () => {
    test("omits fields when 'fields' is not in currentlyEditableFields", () => {
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
      });
      expect(template.paramsForBackend).not.toHaveProperty("fields");
    });

    test("includes fields when 'fields' is in currentlyEditableFields", () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Serial",
            type: "text",
            content: "SN-001",
            columnIndex: 1,
            definition: null,
            selectedOptions: null,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      template.currentlyEditableFields.add("fields");
      const params = template.paramsForBackend as { fields?: Array<Record<string, unknown>> };
      expect(params.fields).toHaveLength(1);
    });

    test("includes all fields when multiple are present and editable", () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Field A",
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
            name: "Field B",
            type: "number",
            content: "10",
            columnIndex: 2,
            definition: null,
            selectedOptions: null,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      template.currentlyEditableFields.add("fields");
      const params = template.paramsForBackend as { fields?: Array<Record<string, unknown>> };
      expect(params.fields).toHaveLength(2);
    });
  });
});
