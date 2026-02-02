import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import * as ArrayUtils from "../../ArrayUtils";

describe("intersperse", () => {
  it("Simple example", () => {
    expect(ArrayUtils.intersperse(", ", ["foo", "bar"])).toEqual([
      "foo",
      ", ",
      "bar",
    ]);
  });
});


