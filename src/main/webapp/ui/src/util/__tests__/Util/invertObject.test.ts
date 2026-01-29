/*
 */
import { describe, test, expect } from "vitest";
import "@testing-library/jest-dom/vitest";
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


