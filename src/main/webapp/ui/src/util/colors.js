// @flow strict

/*
 * Converts an HSL color value to RGB. Conversion formula
 * adapted from http://en.wikipedia.org/wiki/HSL_color_space.
 * Hue must be a number between 0 and 359 inclusive.
 * Saturation must be a number between 0 and 100 inclusive.
 * Lightness must be a number between 0 and 100 inclusive.
 * Opacity, if provided, must be a number between 0 and 1 inclusive.
 * Return value will be a valid hex string
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

/*
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

const padZero = (str: string, length: number = 2) => {
  const zeros = new Array<string>(length).join("0");
  return (zeros + str).slice(-length);
};

export const invertColor = (inputHex: string, bw: boolean = true): string => {
  let hex = inputHex;
  if (hex.indexOf("#") === 0) {
    hex = hex.slice(1);
  }
  // convert 3-digit hex to 6-digits.
  if (hex.length === 3) {
    hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
  }
  if (hex.length !== 6) {
    throw new Error("Invalid HEX color.");
  }
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  if (bw) {
    // http://stackoverflow.com/a/3943023/112731
    return r * 0.299 + g * 0.587 + b * 0.114 > 186 ? "#000000" : "#FFFFFF";
  }
  // invert and pad with zeros
  return (
    "#" +
    padZero((255 - r).toString(16)) +
    padZero((255 - g).toString(16)) +
    padZero((255 - b).toString(16))
  );
};
