/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet, arbSubsetOf } from "./helpers";
import RsSet from "../../set";

describe("isSupersetOf", () => {
  test("Works with fc.subarray", () => {
    fc.assert(
      fc.property(
        arbRsSet(fc.anything()).chain((set) =>
          fc.tuple(
            fc.constant(set),
            arbSubsetOf(set)
          )
        ),
        ([set, subset]: [RsSet<unknown>, RsSet<unknown>]) => {
          expect(set.isSupersetOf(subset)).toBe(true);
        }
      )
    );
  });
  test("Transitivity", () => {
    fc.assert(
      fc.property(
        arbRsSet(fc.anything())
          .chain((setA) => fc.tuple(fc.constant(setA), arbSubsetOf(setA)))
          .chain(([setA, setB]) =>
            fc.tuple(
              fc.constant(setA),
              arbSubsetOf(setB)
            )
          ),
        ([setA, setC]: [RsSet<unknown>, RsSet<unknown>]) => {
          expect(setA.isSupersetOf(setC)).toBe(true);
        }
      )
    );
  });
  test("Reflexivity", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.isSupersetOf(set)).toBe(true);
      })
    );
  });
});