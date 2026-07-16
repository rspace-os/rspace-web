import { describe, expect, it } from "vitest";
import {
  type InventoryOperation,
  operations,
  operationsForSelectionSize,
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
  });

  it("declares Derive as single-origin with an IsDerivedFrom link and an origin-amount input", () => {
    const derive = operations.find((o) => o.key === "derive");
    expect(derive?.maxSelected).toBe(1);
    expect(derive?.effect.amountTakenFrom).toBe("amountTaken");
    expect(derive?.effect.links[0].relationType).toBe("IsDerivedFrom");
  });

  it("offers both operations for a single-subsample selection", () => {
    expect(operationsForSelectionSize(1).map((o) => o.key)).toEqual(["derive", "cryopreserve"]);
  });
});
