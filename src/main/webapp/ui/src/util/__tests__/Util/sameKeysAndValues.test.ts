/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { sameKeysAndValues } from "../../Util";

describe("sameKeysAndValues", () => {
  test("Return true for keys in same order", () => {
    expect(sameKeysAndValues({ foo: 1, bar: 2 }, { foo: 1, bar: 2 })).toBe(
      true
    );
  });

  /*
   * This is likely to an unintuitive aspect of the behaviour of the function
   * if one doesn't look at the implementation
   */
  test("Returns false for keys in different order.", () => {
    expect(sameKeysAndValues({ bar: 2, foo: 1 }, { foo: 1, bar: 2 })).toBe(
      false
    );
  });
});
