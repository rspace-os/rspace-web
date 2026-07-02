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
    expect(message).toMatch(/inventory:fields\.extraFields\.validation\.linkTargetRequired/);
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

function makeTextField(attrs: { name?: string; content?: string; editing?: boolean } = {}): ExtraFieldModel {
  return new ExtraFieldModel(
    {
      id: 8,
      globalId: "EF8",
      name: attrs.name ?? "Notes",
      lastModified: null,
      type: "text",
      content: attrs.content ?? "some text",
      parentGlobalId: "SA1",
      editing: attrs.editing ?? false,
    } as unknown as ConstructorParameters<typeof ExtraFieldModel>[0],
    makeMockSample(),
  );
}

describe("ExtraFieldModel editing blocks record save for any open editor (RSDEV-1201)", () => {
  it("blocks Save while a Text field's name/type editor is open", () => {
    // the name/type edit lives only in the editor (local React state) until Update is
    // clicked, so saving mid-edit would silently discard a rename. Save stays greyed
    // until the edit is committed or cancelled.
    const field = makeTextField({
      name: "Notes",
      content: "hello",
      editing: true,
    });
    expect(field.isValid.isOk).toBe(false);
    const message = field.isValid.orElseGet((errors) => errors.map((e) => e.message).join(" "));
    expect(message).toMatch(/inventory:fields\.extraFields\.validation\.fieldBeingEdited/);
  });

  it("blocks Save while a Number field's editor is open", () => {
    const field = new ExtraFieldModel(
      {
        id: 9,
        globalId: "EF9",
        name: "inventory:contextMenu.createDialog.fields.count",
        lastModified: null,
        type: "number",
        content: "42",
        parentGlobalId: "SA1",
        editing: true,
      } as unknown as ConstructorParameters<typeof ExtraFieldModel>[0],
      makeMockSample(),
    );
    expect(field.isValid.isOk).toBe(false);
    const message = field.isValid.orElseGet((errors) => errors.map((e) => e.message).join(" "));
    expect(message).toMatch(/inventory:fields\.extraFields\.validation\.fieldBeingEdited/);
  });

  it("does NOT block Save when only the field value is edited inline (editor closed)", () => {
    // editing a field's value happens inline on the card (editing stays false) and the
    // content live-syncs into the model, so the record-level Save keeps working normally.
    const field = makeTextField({
      name: "Notes",
      content: "hello",
      editing: false,
    });
    expect(field.isValid.isOk).toBe(true);

    // mirrors the inline TextField onChange (setAttributesDirty({ content }))
    field.setAttributes({ content: "hello world" });
    expect(field.isValid.isOk).toBe(true);
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
    expect(message).toMatch(/inventory:fields\.extraFields\.validation\.fieldBeingEdited/);

    field.setEditing(false);
    expect(field.isValid.isOk).toBe(true);
  });

  it.each(["text", "number", "link"] as const)("blocks Save when a new %s field is being added", (type) => {
    const field = new ExtraFieldModel(
      {
        id: null,
        globalId: null,
        name: "My field",
        lastModified: null,
        type,
        content: "",
        parentGlobalId: "SA1",
        editing: true,
        initial: true,
      } as unknown as ConstructorParameters<typeof ExtraFieldModel>[0],
      makeMockSample(),
    );

    expect(field.isValid.isOk).toBe(false);
    const message = field.isValid.orElseGet((errors) => errors.map((e) => e.message).join(" "));
    expect(message).toMatch(/inventory:fields\.extraFields\.validation\.newFieldBeingAdded/);
  });

  it("blocks Save for a brand-new unapplied field of any type via the edit-mode rule", () => {
    // A new field's name/type/content all live in the editor until Apply; saving mid-add
    // would silently discard them. The edit-mode guard fires for all field types when
    // initial=true so Save is greyed regardless of field type (RSDEV-1201 review).
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
    expect(message).toMatch(/inventory:fields\.extraFields\.validation\.newFieldBeingAdded/);
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
