import { describe, expect, it } from "vitest";
import { type AccentColor, darkTokens } from "@/accentedTheme";
import { ACCENT_COLOR as DSW } from "@/assets/branding/dsw";
import { ACCENT_COLOR as GALLERY } from "@/assets/branding/rspace/gallery";
import { ACCENT_COLOR as INVENTORY } from "@/assets/branding/rspace/inventory";
import { ACCENT_COLOR as SYSADMIN } from "@/assets/branding/rspace/sysadmin";
import { type HslColor, hslContrast } from "@/util/contrast";

function parseHsl(css: string): HslColor {
  const match = /hsl\(([\d.]+)deg, ([\d.]+)%, ([\d.]+)%\)/.exec(css);
  if (!match) throw new Error(`Not an hsl() colour: ${css}`);
  return { hue: Number(match[1]), saturation: Number(match[2]), lightness: Number(match[3]) };
}

describe("darkTokens", () => {
  it.each([
    ["gallery", GALLERY],
    ["inventory", INVENTORY],
    ["sysadmin", SYSADMIN],
    ["dsw", DSW],
  ] as Array<[string, AccentColor]>)("keeps %s text readable on its dark surfaces", (_, accent) => {
    const t = darkTokens(accent, false);
    const ratio = (fg: string, bg: string) => hslContrast(parseHsl(fg), parseHsl(bg));
    expect(ratio(t.contrastTextColor, t.accentedBackground)).toBeGreaterThanOrEqual(4.49);
    expect(ratio(t.contrastTextColor, t.mainAccentColor)).toBeGreaterThanOrEqual(4.49);
    expect(ratio(t.backgroundContrastTextColor, t.mainBackground)).toBeGreaterThanOrEqual(4.49);
    expect(ratio(t.linkColor, t.secondaryBackground)).toBeGreaterThanOrEqual(4.49);
    expect(ratio(t.interactiveColor, t.secondaryBackground)).toBeGreaterThanOrEqual(2.99);
  });
});
