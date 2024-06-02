/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import RsSet from "../../set";

describe("unionWithEq", () => {
  test("Simple example", () => {
    expect(
      new RsSet([{ id: 1 }, { id: 2 }])
        .unionWithEq(new RsSet([{ id: 1 }, { id: 3 }]), (a, b) => a.id === b.id)
        .map(({ id }) => id)
        .isSame(new RsSet([1, 2, 3]))
    ).toBe(true);
  });
});
