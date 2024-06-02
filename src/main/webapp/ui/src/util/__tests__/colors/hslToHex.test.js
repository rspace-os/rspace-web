/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { hslToHex } from "../../colors";
import fc from "fast-check";

describe("hslToHex", () => {
  // leading whitespace in test names is to align test output
  test("(130, 100,  50) = #00ff2a", () => {
    expect(hslToHex(130, 100, 50)).toBe("#00ff2aff");
  });

  test("(231, 100,  54) = #1438ff", () => {
    expect(hslToHex(231, 100, 54)).toBe("#1438ffff");
  });

  test("(  0, 100,  50) = #ff0000", () => {
    expect(hslToHex(0, 100, 50)).toBe("#ff0000ff");
  });

  test("Output should be valid hex string", () => {
    fc.assert(
      fc.property(
        fc.tuple(fc.nat(359), fc.nat(100), fc.nat(100)),
        ([hue, saturation, brightness]) => {
          expect(
            /#[0-9a-f]{6}/.test(hslToHex(hue, saturation, brightness))
          ).toBe(true);
        }
      )
    );
  });

  test("When saturation is 0, the output will always be grey.", () => {
    fc.assert(
      fc.property(fc.tuple(fc.nat(359), fc.nat(100)), ([hue, lightness]) => {
        const matches = hslToHex(hue, 0, lightness).match(
          /^#(?<r>..)(?<g>..)(?<b>..)/
        );
        if (!matches || !matches.groups)
          throw new Error("Invalid rgb hex string");
        const { r, g, b } = matches.groups;
        expect(r).toEqual(g);
        expect(g).toEqual(b);
      })
    );
  });
});
