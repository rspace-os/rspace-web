import { describe, expect, test } from "vitest";
import { isSelfLink, validateTarget } from "../linkTarget";

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
