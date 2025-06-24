/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "./helpers";
import RsSet from "../../set";

describe("union", () => {
  test("Idempotence", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(setA.union(setB).isSame(setA.union(setB).union(setB))).toBe(
            true
          );
        }
      )
    );
  });
  test("Commutativity", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(setA.union(setB).isSame(setB.union(setA))).toBe(true);
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
              .union(setB)
              .union(setC)
              .isSame(setA.union(setB.union(setC)))
          ).toBe(true);
        }
      )
    );
  });
  test("Distributes over intersection", () => {
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
              .union(setB.intersection(setC))
              .isSame(setA.union(setB).intersection(setA.union(setC)))
          ).toBe(true);
        }
      )
    );
  });
  test("The empty set is the identity element", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.union(new RsSet()).isSame(set)).toBe(true);
      })
    );
  });
  test("isSuperset of either input", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), arbRsSet(fc.anything())),
        ([setA, setB]) => {
          expect(setA.union(setB).isSupersetOf(setA)).toBe(true);
          expect(setA.union(setB).isSupersetOf(setB)).toBe(true);
        }
      )
    );
  });
});