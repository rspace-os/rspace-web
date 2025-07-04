/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import * as ArrayUtils from "../../ArrayUtils";

describe("filterNull", () => {
  test("Example", () => {
    const mixOfNumbersAndNulls: Array<number | null> = [1, 2, null];

    const withFilter: Array<number> = mixOfNumbersAndNulls.filter(
      (n) => n !== null
    );
    expect(withFilter).toEqual([1, 2]);

    // But now it does
    const withFilterNull: Array<number> =
      ArrayUtils.filterNull(mixOfNumbersAndNulls);
    expect(withFilterNull).toEqual([1, 2]);
  });
});
