/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import theme from "../../../theme";
import { mapObject } from "../../../util/Util";

type RGB = {
  red: number;
  green: number;
  blue: number;
};

const getColor = (hex: string): RGB => {
  const rgb = hex.match("#(..)(..)(..)");
  if (rgb === null) throw new Error("Invalid hex string");
  const [r, g, b] = rgb.slice(1);
  return {
    red: parseInt(r, 16),
    green: parseInt(g, 16),
    blue: parseInt(b, 16),
  };
};

type Luminosity = number; // 0 to 1

// Algorithm taken from https://www.w3.org/TR/WCAG20/#relativeluminancedef
const luminosity = (color: RGB): Luminosity => {
  const { red, green, blue } = mapObject((_, v) => {
    const x = v / 255;
    return x < 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
  }, color);
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
};

// Algorithm taken from https://www.w3.org/TR/WCAG20/#contrast-ratiodef
const relativeLuminosity = ({
  lighter,
  darker,
}: {
  lighter: RGB;
  darker: RGB;
}) => {
  return (luminosity(lighter) + 0.05) / (luminosity(darker) + 0.05);
};

// Values taken from https://www.w3.org/TR/UNDERSTANDING-WCAG20/visual-audio-contrast-contrast.html
const WCAG_CONTRAST_THRESHOLDS = {
  AA_large_text: 3.0,
  AA: 4.5,
  AAA: 7.0,
};

describe("theme.palette.record", () => {
  /*
   * For accessibility reasons, there should be sufficient contrast between
   * text and the background it is rendered on. Throughout the UI, we often
   * display white text over the various background colours for each record
   * type. When we do this, there must be sufficient contrast for the text to
   * be legible.
   */
  describe("There is sufficient contrast when white text is rendered on the", () => {
    test("container background color.", () => {
      expect(
        relativeLuminosity({
          lighter: getColor("#ffffff"),
          darker: getColor(theme.palette.record.container.bg),
        })
      ).toBeGreaterThan(WCAG_CONTRAST_THRESHOLDS.AA_large_text);
    });
    test("sample background color.", () => {
      expect(
        relativeLuminosity({
          lighter: getColor("#ffffff"),
          darker: getColor(theme.palette.record.sample.bg),
        })
      ).toBeGreaterThan(WCAG_CONTRAST_THRESHOLDS.AA_large_text);
    });
    test("subsample background color.", () => {
      expect(
        relativeLuminosity({
          lighter: getColor("#ffffff"),
          darker: getColor(theme.palette.record.subSample.bg),
        })
      ).toBeGreaterThan(WCAG_CONTRAST_THRESHOLDS.AA_large_text);
    });
    test("template background color.", () => {
      expect(
        relativeLuminosity({
          lighter: getColor("#ffffff"),
          darker: getColor(theme.palette.record.sampleTemplate.bg),
        })
      ).toBeGreaterThan(WCAG_CONTRAST_THRESHOLDS.AA_large_text);
    });
  });
});
