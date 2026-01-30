import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import fc from "fast-check";
import RsSet from "../../set";
import { arbitraryMappableSets } from "./helpers";

describe("subtractMap", () => {
  it("Idempotence", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(
          setA
            .subtractMap(mapFn, setB.map(mapFn))
            .subtractMap(mapFn, setB.map(mapFn))
            .isSame(setA.subtractMap(mapFn, setB.map(mapFn)))
        ).toBe(true);
      })
    );
  });
  it("The empty set is the identity element", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA]) => {
        expect(setA.subtractMap(mapFn, new RsSet()).isSame(setA)).toBe(true);
      })
    );
  });
  it("The result is a subset of the minuend (first argument)", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(setA.subtractMap(mapFn, setB.map(mapFn)).isSubsetOf(setA)).toBe(
          true
        );
      })
    );
  });
  it("The intersection of result and subtrahend (section argument) is the empty set", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(
          setA
            .subtractMap(mapFn, setB.map(mapFn))
            .map(mapFn)
            .intersection(setB.map(mapFn))
            .isSame(new RsSet())
        ).toBe(true);
      })
    );
  });
});