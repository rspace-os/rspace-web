/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import * as ArrayUtils from "../../ArrayUtils";

describe("filterClass", () => {
  test("Simple example", () => {
    class SuperClass {}
    class SubClassA extends SuperClass {}
    class SubClassB extends SuperClass {}

    const a = new SubClassA();
    const b = new SubClassB();
    const list: Array<SuperClass> = [a, b];
    const filteredList: Array<SubClassA> = ArrayUtils.filterClass(
      SubClassA,
      list
    );

    expect(filteredList).toEqual([a]);
  });
});
