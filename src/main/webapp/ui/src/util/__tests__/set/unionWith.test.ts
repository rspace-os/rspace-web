/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import RsSet, { unionWith } from "../../set";
import { arbitraryMappableSets } from "./helpers";

describe("unionWith", () => {
  test("Idempotence", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB]) => {
        expect(
          unionWith(mapFn, [setA, setB, setB]).isSame(
            unionWith(mapFn, [setA, setB])
          )
        ).toBe(true);
      })
    );
  });
  test("Associativity", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA, setB, setC]) => {
        const twoThenOne = unionWith(mapFn, [
          unionWith(mapFn, [setA, setB]),
          setC,
        ]);
        const oneThenTwo = unionWith(mapFn, [
          setA,
          unionWith(mapFn, [setB, setC]),
        ]);
        const allThree = unionWith(mapFn, [setA, setB, setC]);
        expect(twoThenOne.isSame(oneThenTwo)).toBe(true);
        expect(oneThenTwo.isSame(allThree)).toBe(true);
      })
    );
  });
  test("Identity element", () => {
    fc.assert(
      fc.property(arbitraryMappableSets, ([mapFn, setA]) => {
        expect(
          unionWith(mapFn, [setA, new RsSet<{ id: unknown }>()]).isSame(setA)
        ).toBe(true);
        expect(
          unionWith(mapFn, [new RsSet<{ id: unknown }>(), setA]).isSame(setA)
        ).toBe(true);
      })
    );
  });
});