//@flow
/* eslint-env jest */
import fc from "fast-check";
import { msToHMS } from "../../Units";

describe("msToHMS", () => {
  test("Input of less than a day should output format of a series of 2 digits", () => {
    fc.assert(
      fc.property(fc.nat(24 * 60 * 60 * 1000), (ms) => {
        expect(/\d\d:\d\d:\d\d/.test(msToHMS(ms))).toBe(true);
      })
    );
  });
});
