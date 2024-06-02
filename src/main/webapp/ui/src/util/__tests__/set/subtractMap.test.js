//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import RsSet from "../../set";
import { arbitraryMappableSets } from "./helpers";

describe("subtractMap", () => {
  test("Idempotence", () => {
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
  test("The empty set is the identity element", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA]) => {
        expect(setA.subtractMap(mapFn, new RsSet()).isSame(setA)).toBe(true);
      })
    );
  });
  test("The result is a subset of the minuend (first argument)", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(setA.subtractMap(mapFn, setB.map(mapFn)).isSubsetOf(setA)).toBe(
          true
        );
      })
    );
  });
  test("The intersection of result and subtrahend (section argument) is the empty set", () => {
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
