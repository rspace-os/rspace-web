//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "./helpers";
import RsSet from "../../set";

describe("subtract", () => {
  test("Idempotence", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(
            setA.subtract(setB).isSame(setA.subtract(setB).subtract(setB))
          ).toBe(true);
        }
      )
    );
  });
  test("The empty set is the identity element", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.subtract(new RsSet()).isSame(set)).toBe(true);
      })
    );
  });
  test("Subtracting anything from the empty set gives the empty set", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(new RsSet<mixed>().subtract(set).isSame(new RsSet())).toBe(true);
      })
    );
  });
  test("The result is a subset of the minuend (first argument)", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(setA.subtract(setB).isSubsetOf(setA)).toBe(true);
        }
      )
    );
  });
  test("The intersection of result and subtrahend (section argument) is the empty set", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(
            setA.subtract(setB).intersection(setB).isSame(new RsSet())
          ).toBe(true);
        }
      )
    );
  });
});
