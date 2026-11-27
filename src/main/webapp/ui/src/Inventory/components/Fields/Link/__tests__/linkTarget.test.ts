import { describe, expect, it, test } from "vitest";
import type { GalleryFile } from "@/eln/gallery/useGalleryListing";
import { filestoreTargetOf, isSelfLink, validateTarget } from "../linkTarget";

describe("validateTarget", () => {
  test("accepts all inventory prefixes, including sample templates", () => {
    for (const globalId of ["SA9", "SS9", "IC9", "IN9", "IT9"]) {
      expect(validateTarget(globalId, "SA1").ok, `${globalId} accepted`).toBe(true);
    }
  });

  test("accepts ELN prefixes", () => {
    for (const globalId of ["SD9", "NB9", "GL9"]) {
      expect(validateTarget(globalId, "SA1").ok, `${globalId} accepted`).toBe(true);
    }
  });

  test("accepts an instrument template target", () => {
    expect(validateTarget("NT7", "SA42").ok).toBe(true);
  });

  test("rejects an instrument template self link", () => {
    expect(validateTarget("NT7", "NT7").ok).toBe(false);
  });

  test("rejects unsupported prefixes", () => {
    const { ok, reason } = validateTarget("GF9", "SA1");
    expect(ok).toBe(false);
    expect(reason).toBe("inventory:fields.link.targetValidation.supportedType");
  });

  test("rejects malformed and empty ids as 'required'", () => {
    expect(validateTarget("", "SA1").reason).toBe("inventory:fields.link.targetValidation.required");
    expect(validateTarget("not-an-id", "SA1").reason).toBe("inventory:fields.link.targetValidation.required");
  });

  test("rejects self-links, ignoring any version suffix", () => {
    expect(validateTarget("SA1", "SA1").ok).toBe(false);
    expect(validateTarget("SA1v3", "SA1").ok).toBe(false);
  });

  test("rejects manually-typed version suffixes: versions are pinned via the clock", () => {
    const inventory = validateTarget("SA6v1", "SA1");
    expect(inventory.ok).toBe(false);
    expect(inventory.reason).toBe("inventory:fields.link.targetValidation.noVersionSuffix");
    const eln = validateTarget("SD7v2", "SA1");
    expect(eln.ok).toBe(false);
    expect(eln.reason).toBe("inventory:fields.link.targetValidation.noVersionSuffix");
    // the base id without a suffix remains valid
    expect(validateTarget("SA6", "SA1").ok).toBe(true);
  });
});

describe("isSelfLink", () => {
  test("matches only the same prefix and id", () => {
    expect(isSelfLink("SA1", "SA1")).toBe(true);
    expect(isSelfLink("SA1", "SS1")).toBe(false);
    expect(isSelfLink("SA1", "SA2")).toBe(false);
  });
});

const filestoreNode = { id: 12, name: "My S3", path: [] } as unknown as GalleryFile;

describe("filestoreTargetOf", () => {
  it("names the filestore and the path of a file inside a filestore", () => {
    const file = {
      id: null,
      name: "run.dat",
      path: [filestoreNode],
      remotePath: "/XRD/run.dat",
    } as unknown as GalleryFile;

    expect(filestoreTargetOf(file)).toEqual({
      target: { filestoreId: 12, remotePath: "/XRD/run.dat" },
      filestoreName: "My S3",
    });
  });

  it("gives the path in the server's form: one leading slash, no trailing one, inner slashes kept", () => {
    // a picked folder can end with a slash (SMB lists folders that way); the server stores the
    // link without it, so a re-picked folder must compare equal to the stored link
    const folder = {
      id: null,
      name: "runs",
      path: [filestoreNode],
      remotePath: "//XRD//runs/",
    } as unknown as GalleryFile;

    expect(filestoreTargetOf(folder)?.target.remotePath).toBe("/XRD//runs");
  });

  it("refuses the filestore itself", () => {
    expect(filestoreTargetOf(filestoreNode)).toBeNull();
  });

  it("refuses a file of another Gallery section", () => {
    const gallerySubfolder = { id: 7, name: "Gels", path: [] };
    const local = { id: 5, name: "gel.png", globalId: "GL5", path: [gallerySubfolder] } as unknown as GalleryFile;

    expect(filestoreTargetOf(local)).toBeNull();
  });
});
