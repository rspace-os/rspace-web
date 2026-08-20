import { describe, expect, it } from "vitest";
import { type InventoryOperation, operations } from "../operationsConfig";
import { addProcessName, filterProcessNames, processNameDefaultAfterPerform, rememberKey } from "../processNames";

// Operation keys never contain spaces, so a single space separates operation key from process name.
const DERIVE_DNA = ["derive", "dna extraction"].join(" ");
const DERIVE_BOIL = ["derive", "boil"].join(" ");
function op(key: string): InventoryOperation {
  const found = operations.find((o) => o.key === key);
  if (!found) throw new Error(`no operation "${key}"`);
  return found;
}
const derive = op("derive");
const cryopreserve = op("cryopreserve");

describe("rememberKey", () => {
  it("keys a process-name operation by operation + resolved (trimmed) process name", () => {
    expect(rememberKey(derive, { processName: "dna extraction" })).toBe(DERIVE_DNA);
    expect(rememberKey(derive, { processName: "  boil  " })).toBe(DERIVE_BOIL);
  });

  it("keys a fixed-process operation by the operation key", () => {
    expect(rememberKey(cryopreserve, {})).toBe("cryopreserve");
  });

  it("falls back to the operation key when the process name is empty/whitespace", () => {
    expect(rememberKey(derive, { processName: "   " })).toBe("derive");
    expect(rememberKey(derive, {})).toBe("derive");
  });
});

describe("addProcessName", () => {
  it("appends a new trimmed name", () => {
    expect(addProcessName(["dna extraction"], "boil")).toEqual(["dna extraction", "boil"]);
    expect(addProcessName([], "  dna extraction  ")).toEqual(["dna extraction"]);
  });

  it("dedupes and ignores empty", () => {
    expect(addProcessName(["boil"], "  boil  ")).toEqual(["boil"]);
    expect(addProcessName(["boil"], "   ")).toEqual(["boil"]);
  });
});

describe("filterProcessNames", () => {
  const options = ["dna extraction", "boil"];

  it("shows all when the input is empty or whitespace-only", () => {
    expect(filterProcessNames(options, "")).toEqual(options);
    expect(filterProcessNames(options, "   ")).toEqual(options);
  });

  it("prefix-filters case-insensitively, ignoring leading whitespace", () => {
    expect(filterProcessNames(options, "d")).toEqual(["dna extraction"]);
    expect(filterProcessNames(options, "D")).toEqual(["dna extraction"]);
    expect(filterProcessNames(options, " d")).toEqual(["dna extraction"]);
    expect(filterProcessNames(options, "boil")).toEqual(["boil"]);
  });

  it("returns nothing when the prefix matches no option (user can still free-type)", () => {
    expect(filterProcessNames(options, "de")).toEqual([]);
  });
});

describe("processNameDefaultAfterPerform", () => {
  it("stores the trimmed name as the operation's default when remember is on", () => {
    expect(processNameDefaultAfterPerform({}, "derive", "  dna extraction  ", true)).toEqual({
      derive: "dna extraction",
    });
  });

  it("forgets any previous default for the operation when remember is off", () => {
    expect(processNameDefaultAfterPerform({ derive: "boil", cryopreserve: "x" }, "derive", "boil", false)).toEqual({
      cryopreserve: "x",
    });
  });

  it("forgets the operation when remember is on but the name is blank", () => {
    expect(processNameDefaultAfterPerform({ derive: "boil" }, "derive", "   ", true)).toEqual({});
  });

  it("does not mutate the input map", () => {
    const current = { derive: "boil" };
    processNameDefaultAfterPerform(current, "derive", "dna", true);
    expect(current).toEqual({ derive: "boil" });
  });
});
