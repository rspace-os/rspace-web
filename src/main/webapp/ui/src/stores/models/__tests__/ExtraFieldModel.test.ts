import { isAction } from "mobx";
import { describe, expect, it, vi } from "vitest";
import ExtraFieldModel from "../ExtraFieldModel";
// SampleModel must load before the ExtraFieldModel -> RootStore import chain,
// otherwise the TemplateModel/SampleModel module cycle hits an uninitialised
// SAMPLE_FIELDS during module evaluation
import { makeMockSample } from "./SampleModel/mocking";

vi.mock("../../../common/InvApiService", () => ({ default: {} }));
// cut the RootStore -> SearchStore -> TemplateModel module cycle, which is not
// exercised by these model-level tests
vi.mock("../../stores/getRootStore", () => ({
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
    const message = field.isValid.orElseGet((errors) => errors.map((e) => e.message).join(" "));
    expect(message).toMatch(/target global id/i);
  });

  it("treats an unset link as a valid empty field rather than blocking Save", () => {
    // the backend allows Link extra-fields with no payload (API-created or
    // migrated records) and the card renders them as "No link set"; that
    // empty view state must not block record-level Save, since no editor is
    // open and there is nothing for the user to correct
    const field = makeLinkField(null);
    expect(field.isValid.isOk).toBe(true);
  });

  it("is invalid when the link is half-set", () => {
    // a payload missing its relation or target is corrupt, unlike an absent
    // payload, which is the legitimate "No link set" empty state
    const field = makeLinkField({
      relationType: "",
      targetGlobalId: "SA2",
      versionPin: null,
    });
    expect(field.isValid.isOk).toBe(false);
  });
});

describe("ExtraFieldModel editing blocks record save", () => {
  it("is invalid while the field editor is open, so Save is greyed out", () => {
    // mid-edit values live in the editor, not the model: saving now would
    // silently drop them. Save stays greyed until Update or Cancel is clicked.
    const field = makeLinkField({
      relationType: "References",
      targetGlobalId: "SA2",
      versionPin: null,
    });
    expect(field.isValid.isOk).toBe(true);

    field.setEditing(true);

    expect(field.isValid.isOk).toBe(false);
    const message = field.isValid.orElseGet((errors) => errors.map((e) => e.message).join(" "));
    expect(message).toMatch(/update or cancel/i);

    field.setEditing(false);
    expect(field.isValid.isOk).toBe(true);
  });

  it("is invalid while a brand-new field is unapplied, so Save is greyed out", () => {
    const field = new ExtraFieldModel(
      {
        id: null,
        globalId: null,
        name: "",
        lastModified: null,
        type: "text",
        content: "",
        parentGlobalId: "SA1",
        editing: true,
        initial: true,
      } as unknown as ConstructorParameters<typeof ExtraFieldModel>[0],
      makeMockSample(),
    );

    expect(field.isValid.isOk).toBe(false);
    const message = field.isValid.orElseGet((errors) => errors.map((e) => e.message).join(" "));
    expect(message).toMatch(/apply or discard/i);
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
