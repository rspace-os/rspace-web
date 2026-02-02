import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { invertObject } from "../../Util";

describe("invertObject", () => {
  it("Simple example", () => {
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


