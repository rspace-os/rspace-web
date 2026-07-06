import { describe, expect, test, vi } from "vitest";
import { makeMockInstrumentTemplate } from "../InstrumentTemplateModel/mocking";
import { makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: { query: vi.fn(), get: vi.fn() } }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: {
      addAlert: vi.fn(),
      setPageNavigationConfirmation: vi.fn(),
      setDirty: vi.fn(),
    },
  }),
}));

function makeUnsavedInstrument() {
  return makeMockInstrument({ id: null, globalId: null, name: "" });
}

describe("InstrumentModel.setTemplate", () => {
  describe("when template is null", () => {
    test("clears fields", async () => {
      const instrument = makeMockInstrument({
        id: null,
        globalId: null,
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
      await instrument.setTemplate(null);
      expect(instrument.fields).toHaveLength(0);
    });

    test("sets templateId to null", async () => {
      const instrument = makeMockInstrument({ id: null, globalId: null, templateId: 5 });
      await instrument.setTemplate(null);
      expect(instrument.templateId).toBeNull();
    });

    test("sets template to null", async () => {
      const template = makeMockInstrumentTemplate();
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      await instrument.setTemplate(null);
      expect(instrument.template).toBeNull();
    });
  });

  describe("when template is set on an unsaved instrument", () => {
    test("sets templateId from the template", async () => {
      const template = makeMockInstrumentTemplate({ id: 7 });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      expect(instrument.templateId).toBe(7);
    });

    test("sets templateVersion from the template", async () => {
      const template = makeMockInstrumentTemplate({ version: 4 });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      expect(instrument.templateVersion).toBe(4);
    });

    test("copies structured fields from the template", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Frequency",
            type: "number",
            content: "60",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      expect(instrument.fields).toHaveLength(1);
      expect(instrument.fields[0].name).toBe("Frequency");
    });

    test("copies extra fields from the template", async () => {
      const template = makeMockInstrumentTemplate({
        extraFields: [
          {
            id: 2,
            globalId: "EF2",
            name: "Calibration Date",
            lastModified: null,
            type: "text",
            content: "2024-01-01",
            parentGlobalId: "NT1",
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      expect(instrument.extraFields).toHaveLength(1);
      expect(instrument.extraFields[0].name).toBe("Calibration Date");
    });

    test("copied extra fields have newFieldRequest=true and id=null", async () => {
      const template = makeMockInstrumentTemplate({
        extraFields: [
          {
            id: 3,
            globalId: "EF3",
            name: "Lab",
            lastModified: null,
            type: "text",
            content: "Room 4B",
            parentGlobalId: "NT1",
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      expect(instrument.extraFields[0].id).toBeNull();
      expect(instrument.extraFields[0].newFieldRequest).toBe(true);
    });

    test("copies name from template when instrument name is empty", async () => {
      const template = makeMockInstrumentTemplate({ name: "Centrifuge 5000" });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      expect(instrument.name).toBe("Centrifuge 5000");
    });

    test("does not overwrite instrument name when already set", async () => {
      const template = makeMockInstrumentTemplate({ name: "Template Name" });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeMockInstrument({ id: null, globalId: null, name: "My Instrument" });
      await instrument.setTemplate(template);
      expect(instrument.name).toBe("My Instrument");
    });

    test("copies description from template when instrument description is empty", async () => {
      const template = makeMockInstrumentTemplate({ description: "High-speed centrifuge" });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeMockInstrument({ id: null, globalId: null, name: "", description: "" });
      await instrument.setTemplate(template);
      expect(instrument.description).toBe("High-speed centrifuge");
    });

    test("does not overwrite instrument description when already set", async () => {
      const template = makeMockInstrumentTemplate({ description: "Template description" });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeMockInstrument({
        id: null,
        globalId: null,
        name: "",
        description: "My description",
      });
      await instrument.setTemplate(template);
      expect(instrument.description).toBe("My description");
    });

    test("calls fetchAdditionalInfo on the template", async () => {
      const template = makeMockInstrumentTemplate();
      const fetchSpy = vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      expect(fetchSpy).toHaveBeenCalledOnce();
    });

    test("preserves user-added extra fields when changing templates", async () => {
      const templateA = makeMockInstrumentTemplate({
        extraFields: [
          {
            id: 1,
            globalId: "EF1",
            name: "Template A Field",
            lastModified: null,
            type: "text",
            content: "",
            parentGlobalId: "NT1",
          },
        ],
      });
      const templateB = makeMockInstrumentTemplate({
        extraFields: [
          {
            id: 2,
            globalId: "EF2",
            name: "Template B Field",
            lastModified: null,
            type: "text",
            content: "",
            parentGlobalId: "NT1",
          },
        ],
      });
      vi.spyOn(templateA, "fetchAdditionalInfo").mockResolvedValue(undefined);
      vi.spyOn(templateB, "fetchAdditionalInfo").mockResolvedValue(undefined);

      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(templateA);
      instrument.addExtraField({
        id: null,
        globalId: null,
        parentGlobalId: null,
        name: "User Added Field",
        type: "text",
        content: "my value",
        lastModified: null,
      });
      await instrument.setTemplate(templateB);

      const names = instrument.extraFields.map((ef) => ef.name);
      expect(names).toContain("User Added Field");
      expect(names).toContain("Template B Field");
      expect(names).not.toContain("Template A Field");
    });

    test("replaces previous template extra fields when changing templates", async () => {
      const templateA = makeMockInstrumentTemplate({
        extraFields: [
          {
            id: 1,
            globalId: "EF1",
            name: "Calibration Date",
            lastModified: null,
            type: "text",
            content: "",
            parentGlobalId: "NT1",
          },
        ],
      });
      const templateB = makeMockInstrumentTemplate({
        extraFields: [
          {
            id: 2,
            globalId: "EF2",
            name: "Warranty Expiry",
            lastModified: null,
            type: "text",
            content: "",
            parentGlobalId: "NT1",
          },
        ],
      });
      vi.spyOn(templateA, "fetchAdditionalInfo").mockResolvedValue(undefined);
      vi.spyOn(templateB, "fetchAdditionalInfo").mockResolvedValue(undefined);

      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(templateA);
      await instrument.setTemplate(templateB);

      const names = instrument.extraFields.map((ef) => ef.name);
      expect(names).not.toContain("Calibration Date");
      expect(names).toContain("Warranty Expiry");
    });
  });

  describe("when template is set on a saved instrument", () => {
    test("does not copy fields from the template", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Frequency",
            type: "number",
            content: "60",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeMockInstrument({ id: 99, globalId: "IN99" });
      await instrument.setTemplate(template);
      expect(instrument.fields).toHaveLength(0);
    });

    test("still sets templateId and templateVersion", async () => {
      const template = makeMockInstrumentTemplate({ id: 3, version: 2 });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeMockInstrument({ id: 99, globalId: "IN99" });
      await instrument.setTemplate(template);
      expect(instrument.templateId).toBe(3);
      expect(instrument.templateVersion).toBe(2);
    });
  });
});
