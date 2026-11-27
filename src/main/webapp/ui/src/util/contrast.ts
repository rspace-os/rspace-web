type Rgb = readonly [number, number, number];

function hslToRgb(hue: number, saturation: number, lightness: number): Rgb {
  const s = saturation / 100;
  const l = lightness / 100;
  const a = s * Math.min(l, 1 - l);
  const f = (n: number) => {
    const k = (n + hue / 30) % 12;
    return 255 * (l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1));
  };
  return [f(0), f(8), f(4)];
}

function relativeLuminance([r, g, b]: Rgb): number {
  const channel = (c: number) => {
    const v = c / 255;
    return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function contrastRatio(a: Rgb, b: Rgb): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/**
 * An HSL colour whose components are plain numbers, so that contrast can be
 * computed without parsing CSS.
 */
export type HslColor = { hue: number; saturation: number; lightness: number };

export const hslString = ({ hue, saturation, lightness }: HslColor): string =>
  `hsl(${hue}deg, ${saturation}%, ${lightness.toFixed(2)}%)`;

/**
 * WCAG 2 contrast ratio between two HSL colours.
 */
export function hslContrast(a: HslColor, b: HslColor): number {
  return contrastRatio(hslToRgb(a.hue, a.saturation, a.lightness), hslToRgb(b.hue, b.saturation, b.lightness));
}

/**
 * Keeps `hue` and `saturation` and finds the lightness, on the requested side
 * of `against`, that is closest to `against` while still reaching `ratio`.
 * When the gamut cannot reach `ratio`, returns the extreme lightness.
 */
export function solveLightness(
  { hue, saturation }: Pick<HslColor, "hue" | "saturation">,
  against: HslColor,
  ratio: number,
  side: "lighter" | "darker",
): HslColor {
  const bg = hslToRgb(against.hue, against.saturation, against.lightness);
  const bgLuminance = relativeLuminance(bg);
  let lo = 0;
  let hi = 100;
  let best: number | null = null;
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    const candidate = hslToRgb(hue, saturation, mid);
    const onSide = relativeLuminance(candidate) >= bgLuminance === (side === "lighter");
    if (onSide && contrastRatio(candidate, bg) >= ratio) {
      best = mid;
      if (side === "lighter") hi = mid;
      else lo = mid;
    } else if (side === "lighter") lo = mid;
    else hi = mid;
  }
  return { hue, saturation, lightness: best ?? (side === "lighter" ? 100 : 0) };
}

/**
 * Keeps `hue` and `saturation` and finds the lightness whose relative
 * luminance matches the given sRGB colour.
 */
export function matchLuminance({ hue, saturation }: Pick<HslColor, "hue" | "saturation">, target: Rgb): HslColor {
  const goal = relativeLuminance(target);
  let lo = 0;
  let hi = 100;
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    if (relativeLuminance(hslToRgb(hue, saturation, mid)) < goal) lo = mid;
    else hi = mid;
  }
  return { hue, saturation, lightness: (lo + hi) / 2 };
}
