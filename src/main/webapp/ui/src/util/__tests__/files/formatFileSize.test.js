/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { formatFileSize } from "../../files";
import fc from "fast-check";

describe("formatFileSize", () => {
  test('formatFileSize(1024) === "1.02 kB"', () => {
    expect(formatFileSize(1024)).toBe("1.02 kB");
  });

  test('formatFileSize(9976500, 3) === "9.976 MB"', () => {
    expect(formatFileSize(9976500, 3)).toBe("9.976 MB");
  });

  test("formatFileSize output should match regex", () => {
    fc.assert(
      // max nat is (2^31)-1 so 9dp is sufficient
      fc.property(fc.nat(), fc.nat(9), (bytes, dp) => {
        expect(formatFileSize(bytes, dp)).toMatch(
          /*
           * Note the double backslash. The first backslash escapes the string,
           * the second actually inserts it into the regex. This was a very
           * subtle bug because a period matches anything and so the test was
           * passing, but simply wasn't being as rigorous as it ought to be.
           */
          new RegExp("[0-9]{1,3}(\\.[0-9]{1," + Math.max(dp, 1) + "})? .?B")
        );
      })
    );
  });
});
