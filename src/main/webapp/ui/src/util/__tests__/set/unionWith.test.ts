import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import fc from "fast-check";
import RsSet, { unionWith } from "../../set";
import { arbitraryMappableSets } from "./helpers";

describe("unionWith", () => {
  it("Idempotence", () => {
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
  it("Associativity", () => {
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
  it("Identity element", () => {
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