//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet, arbSetOfSetsWithHighOverlap } from "./helpers";
import { flattenWithIntersection } from "../../set";

describe("flattenWithIntersection", () => {
  test("Size of output is less than or equal to the maximum of the sizes of input sets", () => {
    fc.assert(
      fc.property(arbSetOfSetsWithHighOverlap, (setOfSets) => {
        const sizeOfLargestInnerSet = setOfSets
          .map((set) => set.size)
          .reduce((a, b) => Math.max(a, b), 0);
        expect(flattenWithIntersection(setOfSets).size).toBeLessThanOrEqual(
          sizeOfLargestInnerSet
        );
      })
    );
  });
  test("Distributes over union", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbSetOfSetsWithHighOverlap, arbRsSet(fc.anything())),
        ([setOfSets, otherSet]) => {
          expect(
            flattenWithIntersection(setOfSets)
              .union(otherSet)
              .isSame(
                flattenWithIntersection(
                  setOfSets.map((set) => set.union(otherSet))
                )
              )
          ).toBe(true);
        }
      )
    );
  });
});
