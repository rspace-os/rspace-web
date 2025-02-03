//@flow
/* eslint-env jest */
import RsSet, { flattenWithIntersectionWithEq } from "../../set";

describe("flattenWithIntersectionWithEq", () => {
  test("Some overlap", () => {
    const actual = flattenWithIntersectionWithEq(
      new RsSet([
        new RsSet([{ id: 1 }, { id: 2 }]),
        new RsSet([{ id: 1 }, { id: 3 }]),
      ]),
      (elemA, elemB) => elemA.id === elemB.id
    );

    expect(actual.map(({ id }) => id).isSame(new RsSet([1]))).toBe(true);
  });

  test("No overlap", () => {
    const actual = flattenWithIntersectionWithEq(
      new RsSet([
        new RsSet([{ id: 1 }, { id: 2 }]),
        new RsSet([{ id: 3 }, { id: 4 }]),
      ]),
      (elemA, elemB) => elemA.id === elemB.id
    );

    expect(actual.map(({ id }) => id).isSame(new RsSet([]))).toBe(true);
  });

  test("All overlap", () => {
    const actual = flattenWithIntersectionWithEq(
      new RsSet([
        new RsSet([{ id: 1 }, { id: 2 }]),
        new RsSet([{ id: 1 }, { id: 2 }]),
      ]),
      (elemA, elemB) => elemA.id === elemB.id
    );

    expect(actual.map(({ id }) => id).isSame(new RsSet([1, 2]))).toBe(true);
  });
});
