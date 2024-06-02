//@flow
/* eslint-env jest */
import RsSet from "../../set";
import fc from "fast-check";

describe("hasWithEq", () => {
  test("Example", () => {
    expect(
      new RsSet(["foo", "bar"]).hasWithEq("baz", (a, b) => a[0] === b[0])
    ).toBe(true);
  });

  test("If the equality function always returns true, then hasWithEq will return true for non-empty set.", () => {
    fc.assert(
      fc.property(
        fc.tuple(fc.anything(), fc.anything()),
        ([setContent, checkingValue]) => {
          expect(
            new RsSet([setContent]).hasWithEq(checkingValue, () => true)
          ).toBe(true);
        }
      )
    );
  });

  test("If the equality function always returns false, then hasWithEq will return false for any set and any element.", () => {
    fc.assert(
      fc.property(
        fc.tuple(fc.array(fc.anything()), fc.anything()),
        (contents, checkingValue) => {
          expect(
            new RsSet(contents).hasWithEq(checkingValue, () => false)
          ).toBe(false);
        }
      )
    );
  });
});
