/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import * as ArrayUtils from "../../ArrayUtils";

describe("intersperse", () => {
  test("Simple example", () => {
    expect(ArrayUtils.intersperse(", ", ["foo", "bar"])).toEqual([
      "foo",
      ", ",
      "bar",
    ]);
  });
});
