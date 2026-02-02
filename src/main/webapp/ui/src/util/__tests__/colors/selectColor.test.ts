import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { selectColor } from "../../colors";

describe("selectColor", () => {
  it("One colour", () => {
    const pink = selectColor(1, 1);
    expect(pink).toBe("#ff0080ff");
  });

  it("Two colours", () => {
    const cyan = selectColor(1, 2);
    expect(cyan).toBe("#00ffffff");
    const pink = selectColor(2, 2);
    expect(pink).toBe("#ff0080ff");
  });

  it("Three colours", () => {
    const green = selectColor(1, 3);
    expect(green).toBe("#00ff2aff");
    const blue = selectColor(2, 3);
    expect(blue).toBe("#002affff");
    const pink = selectColor(3, 3);
    expect(pink).toBe("#ff0080ff");
  });
});


