import { mapValues } from "es-toolkit";
import { describe, expect, test } from "vitest";
import { PENDING_TEXT_COLOR, STATUS_BACKGROUND } from "../RequestsStatusChip";

type RGB = { red: number; green: number; blue: number };
const getColor = (color: string): RGB => {
  const hex = color.match(/^#(..)(..)(..)$/);
  if (hex) {
    const [r, g, b] = hex.slice(1);
    return { red: parseInt(r, 16), green: parseInt(g, 16), blue: parseInt(b, 16) };
  }
  const rgb = color.match(/^rgb\((\d+),\s*(\d+),\s*(\d+)\)$/);
  if (!rgb) throw new Error(`Unsupported colour format: ${color}`);
  const [r, g, b] = rgb.slice(1);
  return { red: Number(r), green: Number(g), blue: Number(b) };
};

// Algorithm taken from https://www.w3.org/TR/WCAG20/#relativeluminancedef - matching
// src/__tests__/theme/palette/record.test.ts's own copy of the same formula.
const luminosity = (color: RGB): number => {
  const { red, green, blue } = mapValues(color, (v) => {
    const x = v / 255;
    return x < 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
};
// Algorithm taken from https://www.w3.org/TR/WCAG20/#contrast-ratiodef
const contrastRatio = (a: RGB, b: RGB): number => {
  const [lighter, darker] = [luminosity(a), luminosity(b)].sort((x, y) => y - x);
  return (lighter + 0.05) / (darker + 0.05);
};
// https://www.w3.org/TR/UNDERSTANDING-WCAG20/visual-audio-contrast-contrast.html
const WCAG_AA_SMALL_TEXT_THRESHOLD = 4.5;

describe("RequestsStatusChip", () => {
  test("the Pending chip's text meets WCAG AA contrast against its own background", () => {
    const pendingText = getColor(PENDING_TEXT_COLOR);
    const pendingBackground = getColor(STATUS_BACKGROUND.PENDING);

    expect(contrastRatio(pendingText, pendingBackground)).toBeGreaterThanOrEqual(WCAG_AA_SMALL_TEXT_THRESHOLD);
  });
});
