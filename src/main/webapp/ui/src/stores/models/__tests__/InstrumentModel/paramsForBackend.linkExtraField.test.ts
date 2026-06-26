import { describe, expect, test, vi } from "vitest";
import { makeMockInstrument } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentModel.paramsForBackend with Link extra-fields", () => {
  test("emits link.versionPin when the link is pinned to a revision", () => {
    const instrument = makeMockInstrument();
    instrument.currentlyEditableFields.add("extraFields");
    instrument.addExtraField({
      id: 5,
      globalId: null,
      name: "Reference doc",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: instrument.globalId,
      editing: false,
      initial: false,
      link: {
        relationType: "References",
        targetGlobalId: "SD42",
        versionPin: 7,
      },
    });
    const params = instrument.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    expect(params.extraFields?.[0].link).toEqual({
      relationType: "References",
      targetGlobalId: "SD42",
      versionPin: 7,
    });
  });

  test("does NOT include a link key on Text or Number extra-fields", () => {
    const instrument = makeMockInstrument();
    instrument.currentlyEditableFields.add("extraFields");
    instrument.addExtraField({
      id: 1,
      globalId: null,
      name: "Note",
      lastModified: null,
      type: "text",
      content: "maintenance done",
      parentGlobalId: instrument.globalId,
      editing: false,
      initial: false,
    });
    instrument.addExtraField({
      id: 2,
      globalId: null,
      name: "Calibration Offset",
      lastModified: null,
      type: "number",
      content: "0.5",
      parentGlobalId: instrument.globalId,
      editing: false,
      initial: false,
    });
    const params = instrument.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    const fields = params.extraFields ?? [];
    expect(fields).toHaveLength(2);
    expect("link" in fields[0]).toBe(false);
    expect("link" in fields[1]).toBe(false);
  });

  test("serialises a mix of Text, Number and Link fields side by side", () => {
    const instrument = makeMockInstrument();
    instrument.currentlyEditableFields.add("extraFields");
    instrument.addExtraField({
      id: 1,
      globalId: null,
      name: "Note",
      lastModified: null,
      type: "text",
      content: "ok",
      parentGlobalId: instrument.globalId,
      editing: false,
      initial: false,
    });
    instrument.addExtraField({
      id: 2,
      globalId: null,
      name: "Linked doc",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: instrument.globalId,
      editing: false,
      initial: false,
      link: {
        relationType: "References",
        targetGlobalId: "SD42",
        versionPin: null,
      },
    });
    const params = instrument.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    expect(params.extraFields).toHaveLength(2);
    expect(params.extraFields?.[0].type).toBe("text");
    expect(params.extraFields?.[1].type).toBe("link");
    expect(params.extraFields?.[1].link).toEqual({
      relationType: "References",
      targetGlobalId: "SD42",
      versionPin: null,
    });
  });

  test("emits type=link and the link payload, not content", () => {
    const instrument = makeMockInstrument();
    instrument.currentlyEditableFields.add("extraFields");
    instrument.addExtraField({
      id: null,
      globalId: null,
      name: "Linked doc",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: instrument.globalId,
      editing: false,
      initial: false,
      link: {
        relationType: "References",
        targetGlobalId: "SD42",
        versionPin: null,
      },
    });
    const params = instrument.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    expect(params.extraFields).toHaveLength(1);
    const linkField = (params.extraFields ?? [])[0];
    expect(linkField.type).toBe("link");
    expect(linkField.link).toEqual({
      relationType: "References",
      targetGlobalId: "SD42",
      versionPin: null,
    });
    expect(linkField.content).toBe("");
  });
});
