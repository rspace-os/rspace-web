import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import fc from "fast-check";
import { arbRsSet } from "./helpers";
import RsSet from "../../set";

describe("subtract", () => {
  it("Idempotence", () => {
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
  it("The empty set is the identity element", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.subtract(new RsSet()).isSame(set)).toBe(true);
      })
    );
  });
  it("Subtracting anything from the empty set gives the empty set", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(new RsSet<unknown>().subtract(set).isSame(new RsSet())).toBe(true);
      })
    );
  });
  it("The result is a subset of the minuend (first argument)", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(setA.subtract(setB).isSubsetOf(setA)).toBe(true);
        }
      )
    );
  });
  it("The intersection of result and subtrahend (section argument) is the empty set", () => {
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