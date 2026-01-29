/*
 * @vitest-environment jsdom
 */
import { describe, test, expect } from "vitest";
import "@testing-library/jest-dom/vitest";
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


