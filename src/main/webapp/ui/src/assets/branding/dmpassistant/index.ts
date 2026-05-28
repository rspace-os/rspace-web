/**
 * The colour used in the background of the logo. White backplate so the
 * mustard / goldenrod brand mark in logo.svg reads at full contrast; the
 * hue/saturation are retained so text and borders derived from this triple
 * (via IntegrationCard / AccentMenuItem foregroundColor) still carry the
 * mustard accent.
 */
export const LOGO_COLOR = {
  hue: 45,
  saturation: 72,
  lightness: 100,
};

/**
 * The colours used to accent the UI inside the DMP Assistant dialog.
 */
export const ACCENT_COLOR = {
  main: {
    hue: 45,
    saturation: 72,
    lightness: 48,
  },
  darker: {
    hue: 45,
    saturation: 72,
    lightness: 32,
  },
  contrastText: {
    hue: 45,
    saturation: 35,
    lightness: 22,
  },
  background: {
    hue: 45,
    saturation: 60,
    lightness: 88,
  },
  backgroundContrastText: {
    hue: 45,
    saturation: 25,
    lightness: 28,
  },
};
