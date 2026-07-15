import { describe, expect, it } from "vitest";
import { type AmountDefault, amountDefaultsAfterPerform, normalizeAmountDefault } from "../amountDefaults";

const each = { numericValue: 5, unitId: 3 };
const taken = { numericValue: 1, unitId: 3 };
const def: AmountDefault = { count: 2, eachAmount: each, amountTaken: taken };

describe("normalizeAmountDefault", () => {
  it("passes through a well-formed count + pair of quantities", () => {
    expect(normalizeAmountDefault(def)).toEqual(def);
  });

  it("rejects malformed / partial / missing stored values", () => {
    expect(normalizeAmountDefault(undefined)).toBeNull();
    expect(normalizeAmountDefault(null)).toBeNull();
    expect(normalizeAmountDefault({ eachAmount: each, amountTaken: taken })).toBeNull(); // no count
    expect(normalizeAmountDefault({ count: 2, eachAmount: each })).toBeNull();
    expect(normalizeAmountDefault({ count: 2, eachAmount: { numericValue: 5 }, amountTaken: taken })).toBeNull();
    expect(normalizeAmountDefault("nope")).toBeNull();
  });
});

describe("amountDefaultsAfterPerform", () => {
  it("stores the count and both amounts under the key when remember is on", () => {
    expect(amountDefaultsAfterPerform({}, "derive dna", 2, each, taken, true)).toEqual({ "derive dna": def });
  });

  it("forgets any previous amounts for the key when remember is off", () => {
    const current = { "derive dna": def, cryopreserve: def };
    expect(amountDefaultsAfterPerform(current, "derive dna", 2, each, taken, false)).toEqual({ cryopreserve: def });
  });

  it("leaves the map unchanged when remember is on but the count or an amount is missing", () => {
    expect(amountDefaultsAfterPerform({}, "derive dna", undefined, each, taken, true)).toEqual({});
    expect(amountDefaultsAfterPerform({}, "derive dna", 2, undefined, taken, true)).toEqual({});
    expect(amountDefaultsAfterPerform({}, "derive dna", 2, each, undefined, true)).toEqual({});
  });

  it("does not mutate the input map", () => {
    const current = { "derive boil": def };
    amountDefaultsAfterPerform(current, "derive dna", 2, each, taken, true);
    expect(current).toEqual({ "derive boil": def });
  });
});
