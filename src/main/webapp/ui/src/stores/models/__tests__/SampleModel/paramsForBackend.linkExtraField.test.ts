import { describe, expect, test, vi } from "vitest";
import { makeMockSample } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

describe("paramsForBackend with a Link extra-field", () => {
  test("emits link.versionPin when the link is pinned to a revision", () => {
    const sample = makeMockSample();
    sample.currentlyEditableFields.add("extraFields");
    sample.addExtraField({
      id: 5,
      globalId: null,
      name: "Linked sample",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: sample.globalId,
      editing: false,
      initial: false,
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: 7,
      },
    });
    const params = sample.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    expect(params.extraFields?.[0].link).toEqual({
      relationType: "References",
      targetGlobalId: "SA42",
      versionPin: 7,
    });
  });

  test("does NOT include a link key on Text or Number extra-fields", () => {
    const sample = makeMockSample();
    sample.currentlyEditableFields.add("extraFields");
    sample.addExtraField({
      id: 1,
      globalId: null,
      name: "Note",
      lastModified: null,
      type: "text",
      content: "hi",
      parentGlobalId: sample.globalId,
      editing: false,
      initial: false,
    });
    sample.addExtraField({
      id: 2,
      globalId: null,
      name: "Yield",
      lastModified: null,
      type: "number",
      content: "12.5",
      parentGlobalId: sample.globalId,
      editing: false,
      initial: false,
    });
    const params = sample.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    const fields = params.extraFields ?? [];
    expect(fields).toHaveLength(2);
    expect("link" in fields[0]).toBe(false);
    expect("link" in fields[1]).toBe(false);
  });

  test("serialises a mix of Text, Number and Link fields side by side", () => {
    const sample = makeMockSample();
    sample.currentlyEditableFields.add("extraFields");
    sample.addExtraField({
      id: 1,
      globalId: null,
      name: "Note",
      lastModified: null,
      type: "text",
      content: "hi",
      parentGlobalId: sample.globalId,
      editing: false,
      initial: false,
    });
    sample.addExtraField({
      id: 2,
      globalId: null,
      name: "Linked sample",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: sample.globalId,
      editing: false,
      initial: false,
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: null,
      },
    });
    const params = sample.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    expect(params.extraFields).toHaveLength(2);
    expect(params.extraFields?.[0].type).toBe("text");
    expect(params.extraFields?.[1].type).toBe("link");
    expect(params.extraFields?.[1].link).toEqual({
      relationType: "References",
      targetGlobalId: "SA42",
      versionPin: null,
    });
  });

  test("emits type=link and the link payload, not content", () => {
    const sample = makeMockSample();
    sample.currentlyEditableFields.add("extraFields");
    sample.addExtraField({
      id: null,
      globalId: null,
      name: "Linked sample",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: sample.globalId,
      editing: false,
      initial: false,
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: null,
      },
    });

    const params = sample.paramsForBackend as {
      extraFields?: Array<Record<string, unknown>>;
    };
    expect(params.extraFields).toHaveLength(1);
    const linkField = (params.extraFields ?? [])[0];
    expect(linkField.type).toBe("link");
    expect(linkField.link).toEqual({
      relationType: "References",
      targetGlobalId: "SA42",
      versionPin: null,
    });
    expect(linkField.content).toBe("");
  });
});
