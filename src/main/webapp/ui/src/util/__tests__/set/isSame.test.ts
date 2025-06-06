/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet, arbSubsetOf } from "./helpers";
import RsSet from "../../set";

describe("isSame", () => {
  test("Symmetric", () => {
    fc.assert(
      fc.property(
        arbRsSet(fc.anything()).chain((set) =>
          fc.tuple(
            fc.constant(set),
            arbSubsetOf(set)
          )
        ),
        ([setA, setB]: [RsSet<unknown>, RsSet<unknown>]) => {
          expect(setA.isSame(setB)).toEqual(setB.isSame(setA));
        }
      )
    );
  });
});