/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { invertObject } from "../../Util";

describe("invertObject", () => {
  test("Simple example", () => {
    expect(
      invertObject({
        one: "foo",
        two: "bar",
      })
    ).toEqual({
      foo: "one",
      bar: "two",
    });
  });
});
