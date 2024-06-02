/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import * as ArrayUtils from "../../ArrayUtils";

describe("ArrayUtils.reverse", () => {
  test("Is an involutary function.", () => {
    fc.assert(
      fc.property(fc.array(fc.anything()), (list) => {
        expect(ArrayUtils.reverse(ArrayUtils.reverse(list))).toEqual(list);
      })
    );
  });
  test("Is identity on singleton lists.", () => {
    fc.assert(
      fc.property(
        fc.array(fc.anything(), { minLength: 1, maxLength: 1 }),
        (list) => {
          expect(ArrayUtils.reverse(list)).toEqual(list);
        }
      )
    );
  });
  test("Appending and then reversing is the same as reversing then appending", () => {
    fc.assert(
      fc.property(
        fc.array(fc.anything()),
        fc.array(fc.anything()),
        (listA, listB) => {
          expect(ArrayUtils.reverse(listA.concat(listB))).toEqual(
            ArrayUtils.reverse(listB).concat(ArrayUtils.reverse(listA))
          );
        }
      )
    );
  });
});
