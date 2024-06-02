//@flow
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
          fc.tuple<RsSet<mixed>, RsSet<mixed>>(
            fc.constant(set),
            arbSubsetOf(set)
          )
        ),
        ([set, subset]) => {
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
            fc.tuple<RsSet<mixed>, RsSet<mixed>>(
              fc.constant(setA),
              arbSubsetOf(setB)
            )
          ),
        ([setA, setC]) => {
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
