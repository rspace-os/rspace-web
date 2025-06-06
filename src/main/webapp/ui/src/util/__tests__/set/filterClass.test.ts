/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import RsSet from "../../set";

describe("filterClass", () => {
  test("Simple example", () => {
    class Foo {}
    class Bar {}
    const set = new RsSet([new Foo(), new Foo(), new Bar()]);

    expect(set.filterClass(Foo).size).toBe(2);
  });
});