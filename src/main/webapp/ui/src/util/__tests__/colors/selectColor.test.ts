/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { selectColor } from "../../colors";

describe("selectColor", () => {
  test("One colour", () => {
    const pink = selectColor(1, 1);
    expect(pink).toBe("#ff0080ff");
  });

  test("Two colours", () => {
    const cyan = selectColor(1, 2);
    expect(cyan).toBe("#00ffffff");
    const pink = selectColor(2, 2);
    expect(pink).toBe("#ff0080ff");
  });

  test("Three colours", () => {
    const green = selectColor(1, 3);
    expect(green).toBe("#00ff2aff");
    const blue = selectColor(2, 3);
    expect(blue).toBe("#002affff");
    const pink = selectColor(3, 3);
    expect(pink).toBe("#ff0080ff");
  });
});
