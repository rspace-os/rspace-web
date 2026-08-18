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

const templateWithLandingPage = () => {
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
  return template;
};

/*
 * The request shape this describes is the contract the backend relies on: because the creation form
 * always sends `fields`, a blanked Landing page reaches
 * `InstrumentEntityApiManagerImpl.saveNewApiFieldsIntoInstrumentFields`, which must treat the blank
 * as "fill this for me" rather than as a missing mandatory value (RSDEV-1307).
 */
describe("InstrumentModel.paramsForBackend for an instrument created from a template", () => {
  test("sends the fields list, so the blanked Landing page reaches the backend", async () => {
    const instrument = makeMockInstrument({ id: null, globalId: null, name: "" });
    await instrument.setTemplate(templateWithLandingPage());

    // "create" state makes fields editable, so the POST body carries them; this is the premise of
    // the backend's blank-Landing-page handling
    expect(instrument.currentlyEditableFields.has("fields")).toBe(true);
    const params = instrument.paramsForBackend as {
      fields?: Array<Record<string, unknown>>;
    };
    expect(params.fields).toHaveLength(2);
  });

  test("emits the Landing page with empty content", async () => {
    const instrument = makeMockInstrument({ id: null, globalId: null, name: "" });
    await instrument.setTemplate(templateWithLandingPage());

    const params = instrument.paramsForBackend as {
      fields?: Array<Record<string, unknown>>;
    };
    const landingPage = (params.fields ?? []).find((f) => f.name === LANDING_PAGE_FIELD_NAME);
    expect(landingPage?.content).toBe("");
  });

  test("keeps the template default of every other field in the same payload", async () => {
    const instrument = makeMockInstrument({ id: null, globalId: null, name: "" });
    await instrument.setTemplate(templateWithLandingPage());

    const params = instrument.paramsForBackend as {
      fields?: Array<Record<string, unknown>>;
    };
    const manufacturer = (params.fields ?? []).find((f) => f.name === "Manufacturer");
    expect(manufacturer?.content).toBe("Acme Instruments");
  });

  test("emits a landing page the user typed after choosing the template", async () => {
    const instrument = makeMockInstrument({ id: null, globalId: null, name: "" });
    await instrument.setTemplate(templateWithLandingPage());
    const landingPageField = instrument.fields.find((f) => f.name === LANDING_PAGE_FIELD_NAME);
    landingPageField?.setAttributesDirty({ content: "https://lab.example.org/mine" });

    const params = instrument.paramsForBackend as {
      fields?: Array<Record<string, unknown>>;
    };
    const landingPage = (params.fields ?? []).find((f) => f.name === LANDING_PAGE_FIELD_NAME);
    expect(landingPage?.content).toBe("https://lab.example.org/mine");
  });
});
