/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "./helpers";
import RsSet from "../../set";

describe("disjunctiveUnion", () => {
  test("Involution", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(
            setA.disjunctiveUnion(setB).disjunctiveUnion(setB).isSame(setA)
          ).toBe(true);
        }
      )
    );
  });
  test("Commutativity", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(
            setA.disjunctiveUnion(setB).isSame(setB.disjunctiveUnion(setA))
          ).toBe(true);
        }
      )
    );
  });
  test("The empty set is the identity element", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.disjunctiveUnion(new RsSet()).isSame(set)).toBe(true);
      })
    );
  });
  test("if B is a subset of A, then the disjunctive union of A and B will be the same as A subtract B", () => {
    fc.assert(
      fc.property(
        fc
          .tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything()))
          .filter(([setA, setB]) => setB.isSubsetOf(setA)),
        ([setA, setB]) => {
          expect(setA.disjunctiveUnion(setB).isSame(setA.subtract(setB))).toBe(
            true
          );
        }
      )
    );
  });
});