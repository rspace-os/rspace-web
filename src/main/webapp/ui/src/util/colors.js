// @flow strict

/**
 * Converts an HSL color value to RGB. Conversion formula
 * adapted from http://en.wikipedia.org/wiki/HSL_color_space.
 *
 * @arg hue        - must be a number between 0 and 359 inclusive.
 * @arg saturation - must be a number between 0 and 100 inclusive.
 * @arg lightness  - must be a number between 0 and 100 inclusive.
 * @arg opacity    - if provided, must be a number between 0 and 1 inclusive.
 * @returns value will be a valid hex string
 */
export function hslToHex(
  hue: number,
  saturation: number,
  lightness: number,
  opactity: number = 1
): string {
  const l = lightness / 100;
  const a = (saturation * Math.min(l, 1 - l)) / 100;
  const f = (n: number) => {
    const k = (n + hue / 30) % 12;
    const color = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1);
    return Math.round(255 * color)
      .toString(16)
      .padStart(2, "0");
  };
  const opacityHex = Math.round(255 * opactity)
    .toString(16)
    .padStart(2, "0");
  return `#${f(0)}${f(8)}${f(4)}${opacityHex}`;
}

/**
 * Find a number of colours, as defined by `colors`, equally spaced across the
 * colour spectrum with hue varying from 30 to 330, saturation at 100, and
 * lightness of 50. Return the colour with index `colorNum`, as hex string.
 */
export const selectColor = (colorNum: number, colors: number): string => {
  let c = colors;
  if (c < 1) c = 1; // defaults to one color - avoid divide by zero
  // Avoid red color by using hue from 30 to 330
  const hex = hslToHex(30 + colorNum * (300 / c), 100, 50);
  return hex;
};
