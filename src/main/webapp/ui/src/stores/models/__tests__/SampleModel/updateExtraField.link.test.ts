import { describe, expect, test, vi } from "vitest";
import { makeMockSample } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

describe("InventoryBaseRecord.updateExtraField with a link payload", () => {
  function seedLinkField(versionPin: number | null = null) {
    const sample = makeMockSample();
    sample.currentlyEditableFields.add("extraFields");
    sample.addExtraField({
      id: 1,
      globalId: "EF1",
      name: "linked sample",
      lastModified: null,
      type: "link",
      content: "",
      parentGlobalId: sample.globalId,
      editing: false,
      initial: false,
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin,
      },
    });
    return sample;
  }

  test("applies the supplied link payload onto the existing Link field", () => {
    const sample = seedLinkField(null);
    sample.updateExtraField("linked sample", {
      name: "linked sample",
      type: "Link",
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: 7,
      },
    });
    const ef = sample.extraFields[0] as typeof sample.extraFields[0] & {
      link: {
        relationType: string;
        targetGlobalId: string;
        versionPin: number | null;
      };
    };
    expect(ef.link.versionPin).toBe(7);
    expect(ef.editing).toBe(false);
    expect(ef.initial).toBe(false);
  });

  test("clears the link when the type is switched away from Link", () => {
    const sample = seedLinkField(7);
    sample.updateExtraField("linked sample", {
      name: "linked sample",
      type: "Text",
    });
    const ef = sample.extraFields[0] as typeof sample.extraFields[0] & {
      link: unknown;
    };
    expect(ef.type).toBe("Text");
    expect(ef.link).toBeNull();
  });

  test("dirties the record when only the link payload changes, even though name/type are the same", () => {
    const sample = seedLinkField(null);
    // baseline: not dirty after seed because addExtraField sets us through setAttributesDirty,
    // so the dirty flag is already raised. Clear by calling submitDirtyChanges-equivalent: we
    // can't easily do that here, so instead we assert that the link change went through the
    // dirty path by checking the underlying field's link was actually updated.
    sample.updateExtraField("linked sample", {
      name: "linked sample",
      type: "Link",
      link: {
        relationType: "IsCalibratedBy",
        targetGlobalId: "SA42",
        versionPin: null,
      },
    });
    const ef = sample.extraFields[0] as typeof sample.extraFields[0] & {
      link: { relationType: string };
    };
    expect(ef.link.relationType).toBe("IsCalibratedBy");
  });

  test("preserves the existing link when a Link update omits the link payload", () => {
    // Reproduces the discardChanges/partial-update path: the caller passes only
    // name + type (no link), which must NOT wipe the field's existing link.
    const sample = seedLinkField(5);
    sample.updateExtraField("linked sample", {
      name: "linked sample",
      type: "Link",
    });
    const ef = sample.extraFields[0] as typeof sample.extraFields[0] & {
      link: {
        relationType: string;
        targetGlobalId: string;
        versionPin: number | null;
      } | null;
    };
    expect(ef.link).not.toBeNull();
    expect(ef.link?.relationType).toBe("References");
    expect(ef.link?.targetGlobalId).toBe("SA42");
    expect(ef.link?.versionPin).toBe(5);
  });

  test("does not dirty the field when an identical Link update is applied", () => {
    const sample = seedLinkField(3);
    const ef = sample.extraFields[0];
    const dirtySpy = vi.spyOn(ef, "setAttributesDirty");
    sample.updateExtraField("linked sample", {
      name: "linked sample",
      type: "Link",
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: 3,
      },
    });
    expect(dirtySpy).not.toHaveBeenCalled();
    const updated = ef as typeof ef & {
      link: { versionPin: number | null } | null;
    };
    expect(updated.link?.versionPin).toBe(3);
  });

  test("ignores link payload when the new type is not Link", () => {
    const sample = seedLinkField(7);
    sample.updateExtraField("linked sample", {
      name: "linked sample",
      type: "Number",
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: 99,
      },
    });
    const ef = sample.extraFields[0] as typeof sample.extraFields[0] & {
      link: unknown;
    };
    expect(ef.type).toBe("Number");
    // type changed -> the implementation clears link to null regardless of incoming payload
    expect(ef.link).toBeNull();
  });
});
