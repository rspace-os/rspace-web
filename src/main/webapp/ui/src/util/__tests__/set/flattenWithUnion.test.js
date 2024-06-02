//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet, arbSetOfSetsWithHighOverlap } from "./helpers";
import { flattenWithUnion } from "../../set";

describe("flattenWithUnion", () => {
  test("Size of output is less than or equal to the sum of the sizes of input sets", () => {
    fc.assert(
      fc.property(arbSetOfSetsWithHighOverlap, (setOfSets) => {
        const sumOfSizes = setOfSets.reduce((acc, set) => set.size + acc, 0);
        expect(flattenWithUnion(setOfSets).size).toBeLessThanOrEqual(
          sumOfSizes
        );
      })
    );
  });
  test("Distributes over intersection", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbSetOfSetsWithHighOverlap, arbRsSet(fc.anything())),
        ([setOfSets, otherSet]) => {
          expect(
            flattenWithUnion(setOfSets)
              .intersection(otherSet)
              .isSame(
                flattenWithUnion(
                  setOfSets.map((set) => set.intersection(otherSet))
                )
              )
          ).toBe(true);
        }
      )
    );
  });
});
