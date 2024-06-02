/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import { sum } from "../../iterators";

describe("sum", () => {
  test("Works on arrays", () => {
    expect(sum([1, 2, 3])).toBe(6);
  });

  test("Works on sets", () => {
    expect(sum(new Set([1, 2, 3]))).toBe(6);
  });
});
