import { describe, expect, it } from "vitest";
import {
  assertEffectReferencesValid,
  type InventoryOperation,
  operationAvailability,
  operations,
  resolveProcessName,
} from "../operationsConfig";

function op(key: string): InventoryOperation {
  const found = operations.find((o) => o.key === key);
  if (!found) throw new Error(`no operation "${key}"`);
  return found;
}
const derive = op("derive");
const cryopreserve = op("cryopreserve");

describe("resolveProcessName", () => {
  it("returns the user-entered (trimmed) process name for an operation that declares one", () => {
    expect(resolveProcessName(derive, { processName: "  dna extraction  " })).toBe("dna extraction");
  });

  it("returns an empty string for a declared-but-unfilled process name", () => {
    expect(resolveProcessName(derive, {})).toBe("");
  });

  it("returns the operation key as the fixed process name when none is declared", () => {
    expect(resolveProcessName(cryopreserve, {})).toBe("cryopreserve");
  });
});

describe("input order", () => {
  it("puts the process name first and the sample name second for Derive", () => {
    expect(derive.inputs.slice(0, 2).map((i) => i.key)).toEqual(["processName", "sampleName"]);
  });
});

describe("operations_config.json", () => {
  it("parses against the schema and includes the shipped operations", () => {
    const keys = operations.map((o) => o.key);
    expect(keys).toContain("derive");
    expect(keys).toContain("cryopreserve");
    expect(keys).toContain("pool");
    expect(keys).toContain("destroy");
  });

  it("declares Destroy as a terminal operation that empties the origin and adds an origin field", () => {
    const destroy = operations.find((o) => o.key === "destroy");
    expect(destroy?.noOutput).toBe(true);
    // Destroy skips straight to confirmation (no details/template/amounts steps).
    expect(destroy?.steps).toEqual(["confirm"]);
    expect(destroy?.effect.emptiesOrigin).toBe(true);
    // It creates no sample, so it declares no new-sample name/count/each-amount and no links.
    expect(destroy?.effect.nameFrom).toBeUndefined();
    expect(destroy?.effect.links).toEqual([]);
    // It adds a disposed field to the origin, its content a computed value (today).
    expect(destroy?.effect.originFields?.[0]).toMatchObject({ contentFrom: "disposedDate", type: "text" });
    expect(destroy?.effect.computed?.[0]).toMatchObject({ fn: "today", into: "disposedDate" });
  });

  it("declares Derive as single-origin with an IsDerivedFrom link and an origin-amount input", () => {
    const derive = operations.find((o) => o.key === "derive");
    expect(derive?.requiresMultiple).toBeFalsy();
    expect(derive?.effect.amountTakenFrom).toBe("amountTaken");
    expect(derive?.effect.links[0].relationType).toBe("IsDerivedFrom");
  });

  it("declares Pool as multi-origin with a HasPart link and a shared origin-amount input", () => {
    const pool = op("pool");
    expect(pool.requiresMultiple).toBe(true);
    expect(pool.effect.amountTakenFrom).toBe("amountTaken");
    expect(pool.effect.links[0].relationType).toBe("HasPart");
  });

  it("declares Revive's link back to the origin as IsDerivedFrom", () => {
    const revive = op("revive");
    expect(revive.effect.links[0].relationType).toBe("IsDerivedFrom");
  });

  it("enables single-origin operations for one subsample and disables Pool", () => {
    expect(operationAvailability(derive, 1, true).enabled).toBe(true);
    expect(operationAvailability(op("pool"), 1, true).enabled).toBe(false);
    expect(operationAvailability(op("pool"), 1, true).reasonKey).toBe("operations.picker.needsMultiple");
  });

  it("enables only Pool for a multi-subsample selection of one measurement category", () => {
    expect(operationAvailability(op("pool"), 2, true).enabled).toBe(true);
    expect(operationAvailability(derive, 2, true).enabled).toBe(false);
    expect(operationAvailability(derive, 2, true).reasonKey).toBe("operations.picker.singleOnly");
  });

  it("disables Pool when the selected subsamples span measurement categories", () => {
    const availability = operationAvailability(op("pool"), 2, false);
    expect(availability.enabled).toBe(false);
    expect(availability.reasonKey).toBe("operations.picker.sameCategory");
  });

  it("configures Cryopreserve's storage temperature with a -18 C ceiling", () => {
    const storageTemp = cryopreserve.inputs.find((i) => i.key === "storageTemp");
    expect(storageTemp?.type).toBe("temperature");
    expect(storageTemp?.maxCelsius).toBe(-18);
  });

  it("includes the storage temperature in Cryopreserve's confirmation summary", () => {
    expect(cryopreserve.confirmSummary).toContain("storageTemp");
    // Derive has no storage temperature, so its summary does not list one
    expect(derive.confirmSummary).not.toContain("storageTemp");
  });
});

describe("assertEffectReferencesValid", () => {
  it("accepts the shipped config (every effect source resolves to an input or computed value)", () => {
    expect(() => assertEffectReferencesValid(operations)).not.toThrow();
  });

  it("throws when an effect source key names no declared input or computed value", () => {
    const bad = {
      key: "bad",
      labelKey: "l",
      documentationStep: false,
      inputs: [{ key: "sampleName", type: "text", labelKey: "l" }],
      effect: { nameFrom: "typo", links: [] },
    } as unknown as InventoryOperation;
    expect(() => assertEffectReferencesValid([bad])).toThrow(/unknown input "typo"/);
  });

  it("resolves a contentFrom that names a computed value's `into` (e.g. Destroy's disposedDate)", () => {
    const withComputed = {
      key: "c",
      labelKey: "l",
      documentationStep: false,
      inputs: [],
      effect: {
        computed: [{ fn: "today", into: "disposedDate", args: {} }],
        originFields: [{ nameKey: "n", contentFrom: "disposedDate", type: "text" }],
        links: [],
      },
    } as unknown as InventoryOperation;
    expect(() => assertEffectReferencesValid([withComputed])).not.toThrow();
  });

  it("throws when a computed { input } arg names no declared input or computed value", () => {
    const bad = {
      key: "c",
      labelKey: "l",
      documentationStep: false,
      inputs: [{ key: "count", type: "integer", labelKey: "l" }],
      effect: {
        computed: [{ fn: "increment", into: "n", args: { current: { input: "typo" }, start: { constant: 1 } } }],
        links: [],
      },
    } as unknown as InventoryOperation;
    expect(() => assertEffectReferencesValid([bad])).toThrow(/unknown input "typo"/);
  });

  it("accepts a computed { input } arg that names a declared input", () => {
    const ok = {
      key: "c",
      labelKey: "l",
      documentationStep: false,
      inputs: [{ key: "count", type: "integer", labelKey: "l" }],
      effect: {
        computed: [{ fn: "increment", into: "n", args: { current: { input: "count" }, start: { constant: 1 } } }],
        links: [],
      },
    } as unknown as InventoryOperation;
    expect(() => assertEffectReferencesValid([ok])).not.toThrow();
  });
});
