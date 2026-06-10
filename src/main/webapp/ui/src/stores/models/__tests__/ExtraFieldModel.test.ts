import { describe, expect, it, vi } from "vitest";
import { isAction } from "mobx";
// SampleModel must load before the ExtraFieldModel -> RootStore import chain,
// otherwise the TemplateModel/SampleModel module cycle hits an uninitialised
// SAMPLE_FIELDS during module evaluation
import { makeMockSample } from "./SampleModel/mocking";
import ExtraFieldModel from "../ExtraFieldModel";

vi.mock("../../../common/InvApiService", () => ({ default: {} }));
// cut the RootStore -> SearchStore -> TemplateModel module cycle, which is not
// exercised by these model-level tests
vi.mock("../../stores/RootStore", () => ({
  default: () => ({
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));

function makeLinkField(
  link: {
    relationType: string;
    targetGlobalId: string;
    versionPin: number | null;
  } | null,
): ExtraFieldModel {
  return new ExtraFieldModel(
    {
      id: 7,
      globalId: "EF7",
      name: "Related item",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: "SA1",
      link,
    },
    makeMockSample(),
  );
}

describe("ExtraFieldModel link validation", () => {
  it("is valid for a complete link", () => {
    const field = makeLinkField({
      relationType: "References",
      targetGlobalId: "SA2",
      versionPin: null,
    });
    expect(field.isValid.isOk).toBe(true);
  });

  it("is invalid while an in-progress edit has cleared the target", () => {
    // the editor flags the model when its staged link state is incomplete, so
    // a record-level Save cannot silently revert the removed target
    const field = makeLinkField({
      relationType: "References",
      targetGlobalId: "SA2",
      versionPin: null,
    });
    field.setInvalidInput(true);

    expect(field.isValid.isOk).toBe(false);
    const message = field.isValid.orElseGet((errors) =>
      errors.map((e) => e.message).join(" "),
    );
    expect(message).toMatch(/target global id/i);
  });

  it("is invalid when the link has no target at all", () => {
    const field = makeLinkField(null);
    expect(field.isValid.isOk).toBe(false);
  });
});

describe("ExtraFieldModel actions", () => {
  it("mutates observable state only through MobX actions", () => {
    // setInvalidInput and setEditing modify observed observables; if they are
    // not actions, MobX strict mode logs an error on every editor interaction
    const field = makeLinkField(null);
    // eslint-disable-next-line @typescript-eslint/unbound-method -- isAction inspects the function itself
    const { setInvalidInput, setEditing } = field;
    expect(isAction(setInvalidInput)).toBe(true);
    expect(isAction(setEditing)).toBe(true);
  });
});
