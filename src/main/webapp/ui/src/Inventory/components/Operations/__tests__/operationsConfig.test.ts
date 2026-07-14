import { describe, expect, it } from "vitest";
import { operations, operationsForSelectionSize } from "../operationsConfig";

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
