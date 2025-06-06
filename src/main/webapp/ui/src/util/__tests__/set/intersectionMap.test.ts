/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import RsSet from "../../set";
import { arbitraryMappableSets } from "./helpers";

describe("intersectionMap", () => {
  test("Idempotence", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(
          setA
            .intersectionMap(mapFn, setB.map(mapFn))
            .intersectionMap(mapFn, setB.map(mapFn))
            .isSame(setA.intersectionMap(mapFn, setB.map(mapFn)))
        ).toBe(true);
      })
    );
  });
  test("Commutativity", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(
          setA
            .intersectionMap(mapFn, setB.map(mapFn))
            .map(mapFn)
            .isSame(setB.intersectionMap(mapFn, setA.map(mapFn)).map(mapFn))
        ).toBe(true);
      })
    );
  });
  test("Associativity", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB, setC]) => {
        expect(
          setA
            .intersectionMap(mapFn, setB.map(mapFn))
            .intersectionMap(mapFn, setC.map(mapFn))
            .map(mapFn)
            .isSame(
              setA
                .intersectionMap(
                  mapFn,
                  setB.intersectionMap(mapFn, setC.map(mapFn)).map(mapFn)
                )
                .map(mapFn)
            )
        ).toBe(true);
      })
    );
  });
  test("Distributes over union", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB, setC]) => {
        expect(
          setA
            .intersectionMap(mapFn, setB.union(setC).map(mapFn))
            .map(mapFn)
            .isSame(
              setA
                .intersectionMap(mapFn, setB.map(mapFn))
                .union(setA.intersectionMap(mapFn, setC.map(mapFn)))
                .map(mapFn)
            )
        ).toBe(true);
      })
    );
  });
  test("The empty set is the absorbing element", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA]) => {
        expect(
          setA.intersectionMap(mapFn, new RsSet()).isSame(new RsSet())
        ).toBe(true);
      })
    );
  });
  test("The result is a subset of both sets", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(
          setA.intersectionMap(mapFn, setB.map(mapFn)).isSubsetOf(setA)
        ).toBe(true);
        expect(
          setA
            .intersectionMap(mapFn, setB.map(mapFn))
            .map(mapFn)
            .isSubsetOf(setB.map(mapFn))
        ).toBe(true);
      })
    );
  });
});