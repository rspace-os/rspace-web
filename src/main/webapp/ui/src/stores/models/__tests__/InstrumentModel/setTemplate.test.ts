import { describe, expect, test, vi } from "vitest";
import { LANDING_PAGE_FIELD_NAME } from "../../InstrumentModel";
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

    test("blanks the Landing page field but keeps other template defaults", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: LANDING_PAGE_FIELD_NAME,
            type: "uri",
            content: "https://lab.example.org/original",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
          {
            id: 2,
            globalId: "IF2",
            name: "Manufacturer",
            type: "text",
            content: "Acme Instruments",
            selectedOptions: null,
            definition: null,
            columnIndex: 2,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);

      const landingPage = instrument.fields.find((f) => f.name === LANDING_PAGE_FIELD_NAME);
      expect(landingPage?.content).toBe("");
      expect(landingPage?.hasContent).toBe(false);
      expect(instrument.fields.find((f) => f.name === "Manufacturer")?.content).toBe("Acme Instruments");
    });

    test("blanks only the first Landing page field, as the backend resolves only one", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: LANDING_PAGE_FIELD_NAME,
            type: "uri",
            content: "https://lab.example.org/first",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
          {
            id: 2,
            globalId: "IF2",
            // differs only by case, so the name-distinctness check does not reject it while the
            // case-insensitive predicate still matches it
            name: "landing page",
            type: "uri",
            content: "https://lab.example.org/second",
            selectedOptions: null,
            definition: null,
            columnIndex: 2,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);

      // the backend clears and refills exactly one field; blanking both here would leave the
      // second permanently empty, since nothing would ever fill it
      expect(instrument.fields[0].content).toBe("");
      expect(instrument.fields[1].content).toBe("https://lab.example.org/second");
    });

    test("matches the Landing page field ignoring case and surrounding whitespace", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "  landing PAGE ",
            type: "uri",
            content: "https://lab.example.org/original",
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
      expect(instrument.fields[0].content).toBe("");
    });

    test("does not blank a non-URI field named Landing page", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: LANDING_PAGE_FIELD_NAME,
            type: "text",
            content: "see the lab handbook",
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
      expect(instrument.fields[0].content).toBe("see the lab handbook");
    });

    test("does not blank a URI field with a different name", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: "Manual",
            type: "uri",
            content: "https://lab.example.org/manual",
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
      expect(instrument.fields[0].content).toBe("https://lab.example.org/manual");
    });

    test("preserves everything else about the Landing page field", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: LANDING_PAGE_FIELD_NAME,
            type: "uri",
            content: "https://lab.example.org/original",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: true,
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      await instrument.setTemplate(template);
      const field = instrument.fields[0];
      expect(field.name).toBe(LANDING_PAGE_FIELD_NAME);
      expect(field.type).toBe("uri");
      expect(field.mandatory).toBe(true);
    });

    test("blanking a mandatory Landing page still leaves the instrument submittable", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: LANDING_PAGE_FIELD_NAME,
            type: "uri",
            content: "https://lab.example.org/original",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: true,
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeUnsavedInstrument();
      instrument.setAttributes({ name: "New Instrument" });
      await instrument.setTemplate(template);

      // blanking is only safe because instrument structured fields are not validated client-side;
      // if that ever changes, a mandatory Landing page would silently block creation in the form
      expect(instrument.fields[0].content).toBe("");
      expect(instrument.submittable.isOk).toBe(true);
    });

    test("a landing page typed after choosing the template is kept", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: LANDING_PAGE_FIELD_NAME,
            type: "uri",
            content: "https://lab.example.org/original",
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
      instrument.fields[0].setAttributesDirty({ content: "https://lab.example.org/mine" });
      expect(instrument.fields[0].content).toBe("https://lab.example.org/mine");
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

    test("does not blank an existing Landing page field", async () => {
      const template = makeMockInstrumentTemplate({
        fields: [
          {
            id: 1,
            globalId: "IF1",
            name: LANDING_PAGE_FIELD_NAME,
            type: "uri",
            content: "https://lab.example.org/template",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      vi.spyOn(template, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const instrument = makeMockInstrument({
        id: 99,
        globalId: "IN99",
        fields: [
          {
            id: 5,
            globalId: "IF5",
            name: LANDING_PAGE_FIELD_NAME,
            type: "uri",
            content: "https://lab.example.org/IN99",
            selectedOptions: null,
            definition: null,
            columnIndex: 1,
            attachment: null,
            mandatory: false,
          },
        ],
      });
      await instrument.setTemplate(template);
      expect(instrument.fields[0].content).toBe("https://lab.example.org/IN99");
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
