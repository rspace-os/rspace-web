/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "./helpers";
import RsSet from "../../set";

describe("intersection", () => {
  test("Idempotence", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(
            setA
              .intersection(setB)
              .isSame(setA.intersection(setB).intersection(setB))
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
          expect(setA.intersection(setB).isSame(setB.intersection(setA))).toBe(
            true
          );
        }
      )
    );
  });
  test("Associativity", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          arbRsSet(fc.anything()),
          arbRsSet(fc.anything()),
          arbRsSet(fc.anything())
        ),
        ([setA, setB, setC]) => {
          expect(
            setA
              .intersection(setB)
              .intersection(setC)
              .isSame(setA.intersection(setB.intersection(setC)))
          ).toBe(true);
        }
      )
    );
  });
  test("Distributes over union", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          arbRsSet(fc.anything()),
          arbRsSet(fc.anything()),
          arbRsSet(fc.anything())
        ),
        ([setA, setB, setC]) => {
          expect(
            setA
              .intersection(setB.union(setC))
              .isSame(setA.intersection(setB).union(setA.intersection(setC)))
          ).toBe(true);
        }
      )
    );
  });
  test("The empty set is the absorbing element", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.intersection(new RsSet()).isSame(new RsSet())).toBe(true);
      })
    );
  });
  test("isSubset of either input", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(setA.intersection(setB).isSubsetOf(setA)).toBe(true);
          expect(setA.intersection(setB).isSubsetOf(setB)).toBe(true);
        }
      )
    );
  });
});