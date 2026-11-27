import { alertTitleClasses } from "@mui/material/AlertTitle";
import { buttonClasses } from "@mui/material/Button";
import { touchRippleClasses } from "@mui/material/ButtonBase";
import { buttonGroupClasses } from "@mui/material/ButtonGroup";
import { cardActionAreaClasses } from "@mui/material/CardActionArea";
import { checkboxClasses } from "@mui/material/Checkbox";
import { chipClasses } from "@mui/material/Chip";
import { dividerClasses } from "@mui/material/Divider";
import { formLabelClasses } from "@mui/material/FormLabel";
import { iconButtonClasses } from "@mui/material/IconButton";
import { inputAdornmentClasses } from "@mui/material/InputAdornment";
import { inputBaseClasses } from "@mui/material/InputBase";
import { inputLabelClasses } from "@mui/material/InputLabel";
import { linkClasses } from "@mui/material/Link";
import { listItemButtonClasses } from "@mui/material/ListItemButton";
import { listItemIconClasses } from "@mui/material/ListItemIcon";
import { listItemTextClasses } from "@mui/material/ListItemText";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import { paperClasses } from "@mui/material/Paper";
import { radioClasses } from "@mui/material/Radio";
import { selectClasses } from "@mui/material/Select";
import { svgIconClasses } from "@mui/material/SvgIcon";
import {
  type CSSObject,
  createTheme,
  type SimplePaletteColorOptions,
  type Theme,
  type ThemeOptions,
} from "@mui/material/styles";
import { textFieldClasses } from "@mui/material/TextField";
import { toolbarClasses } from "@mui/material/Toolbar";
import { typographyClasses } from "@mui/material/Typography";
import { alpha, darken, lighten } from "@mui/system";
import { deepmerge } from "@mui/utils";
import { getDataGridUtilityClass, gridClasses } from "@mui/x-data-grid";
import { treeItemClasses } from "@mui/x-tree-view/TreeItem";
import { darkModeAvailable } from "./colorScheme";
import baseTheme, { colorSchemes as baseColorSchemes } from "./theme";
import { type HslColor, hslContrast, hslString, matchLuminance, solveLightness } from "./util/contrast";

/**
 * Represents an HSL color.
 */
export type Hsl = {
  hue: number;
  saturation: number;
  lightness: number;
  opacity?: number;
};

declare module "@mui/material/Button" {
  interface ButtonClasses {
    containedCallToAction: string;
  }
}
declare module "@mui/material/styles" {
  interface Palette {
    accent: AccentTokens;
  }
  interface PaletteOptions {
    accent?: AccentTokens;
  }
  interface Components {
    MuiTreeItem?: {
      styleOverrides?: {
        root: unknown;
        content: unknown;
        label: unknown;
        iconContainer: unknown;
        groupTransition: unknown;
      };
      variants?: object;
    };
  }
}

/**
 * The definition of an accent colour, which is to say a colour that is used to
 * adjust the appearance of various elements in the application in accordance
 * with some branding; be it ours or that of a third-party.
 */
export type AccentColor = {
  /**
   * This the main accent colour from which various variants are derived.
   * The lightness MUST not be less than 5.
   * DO NOT specify an opacity.
   */
  main: Hsl;

  /**
   * A darker variant used for link text, icons, etc.
   * DO NOT specify an opacity.
   */
  darker: Hsl;

  /**
   * Used where text appears atop a background of the main color, and thus MUST
   * have a contrast ratio of at least 4.5:1. If the accent if particularly
   * dark then an almost-white is recommended, otherwise an almost-black.
   * DO NOT specify an opacity.
   */
  contrastText: Hsl;

  /**
   * Used when the background of an element should have the accent, such as app
   * bars and filled buttons. The almost-white background of panels and dialogs
   * is also derived from this colour.
   *
   * Satuation SHOULD be around 30 and MUST be greater than 10.
   * Lightness SHOULD be around 80 and MUST be greater than 20.
   */
  background: Hsl;

  /**
   * The colour of general text. This SHOULD be pretty close to black.
   *
   * Saturation SHOULD be around 20.
   * Lightness SHOULD be around 30.
   */
  backgroundContrastText: Hsl;
};

/**
 * The accented theme is used for pages that use the new styling, wherein the
 * page (or just part thereof) is styled with an accent colour. This is being
 * done to provide a consistent branding for the various parts of the product
 * as they are interwoven, as well as the integrations with third-parties.
 *
 * This function creates a new theme, given an accent colour.
 */

/**
 * The colours an accented theme derives from its accent, resolved for one
 * colour scheme. Component overrides read them from `theme.palette.accent`
 * so they follow the active scheme.
 */
export type AccentTokens = {
  mode: "light" | "dark";
  mainAccentColor: string;
  disabledColor: string;
  linkButtonText: string;
  /**
   * A background colour that can be used behind headers, toolbars, and other
   * elements to which the user's attention should be drawn. This colour, used
   * liberally across the UI, helps build a cohesive sense that of parts of the
   * UI (such as dialogs, cards, etc) belonging to different parts of the
   * product (such as the Gallery) based on their colour or being associated
   * with different organisations (such as DMPonline) where the colour is
   * similar to their respective branding.
   */
  accentedBackground: string;
  dialogBorder: string;
  accentedBorder: string;
  /** The near-white (or, in dark mode, near-black) background of dialogs and pages. */
  mainBackground: string;
  /** The panel background: white in light mode. */
  secondaryBackground: string;
  /** Text on accentedBackground or mainAccentColor. */
  contrastTextColor: string;
  /** Text on mainBackground. */
  backgroundContrastTextColor: string;
  /** Interactive elements: checkboxes, radiobuttons, and primary buttons. */
  interactiveColor: string;
  /** Interactive elements: text fields in app bar, chips. */
  lighterInteractiveColor: string;
  /** Links are slightly darker (lighter in dark mode) to more closely match surrounding text. */
  linkColor: string;
  standardIcon: string;
  /** Foreground and background used when the user prefers more contrast. */
  hcFg: string;
  hcBg: string;
  rowEven: string;
  rowHover: string;
  rowSelected: string;
  cardShadow: string;
  avatarBackground: string;
  avatarColor: string;
  /** Alert text overrides; null leaves MUI's colour for the scheme. */
  alertInfoText: string | null;
  alertSuccessText: string | null;
};

const hslOf = ({ hue, saturation, lightness }: Hsl): HslColor => ({ hue, saturation, lightness });

function lightTokens(accent: AccentColor, prefersMoreContrast: boolean): AccentTokens {
  // All of these strings are formatted specifically so MUI can parse them and perform its own arithmetic
  const mainAccentColor = prefersMoreContrast
    ? "rgb(0,0,0)"
    : `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%)`;
  const accentedBackground = prefersMoreContrast
    ? "rgb(0,0,0)"
    : `hsl(${accent.background.hue}deg, ${accent.background.saturation}%, ${accent.background.lightness}%)`;
  const interactiveColor = prefersMoreContrast
    ? "rgb(0,0,0)"
    : darken(`hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%)`, 0.375);
  /**
   * This may seem pointless to define as its just white, but defining it this
   * way allows us to dynamically apply darken
   */
  const secondaryBackground = `hsl(${accent.background.hue}deg, ${accent.background.saturation}%, 100%)`;
  return {
    mode: "light",
    mainAccentColor,
    disabledColor: lighten(`hsl(${accent.main.hue}deg, 10%, ${accent.main.lightness}%)`, 0.5),
    linkButtonText: prefersMoreContrast
      ? "rgb(0,0,0)"
      : darken(`hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%)`, 0.5),
    accentedBackground,
    dialogBorder: `3px solid ${accentedBackground}`,
    accentedBorder: `2px solid ${accentedBackground}`,
    mainBackground: prefersMoreContrast
      ? "rgb(255,255,255)"
      : `hsl(${accent.background.hue}deg, ${accent.background.saturation}%, 98%)`,
    secondaryBackground,
    contrastTextColor: prefersMoreContrast
      ? "rgb(255,255,255)"
      : `hsl(${accent.contrastText.hue}deg, ${accent.contrastText.saturation}%, ${accent.contrastText.lightness}%, 100%)`,
    backgroundContrastTextColor: prefersMoreContrast
      ? "rgb(0,0,0)"
      : `hsl(${accent.backgroundContrastText.hue}deg, ${accent.backgroundContrastText.saturation}%, ${accent.backgroundContrastText.lightness}%, 100%)`,
    interactiveColor,
    lighterInteractiveColor: prefersMoreContrast
      ? secondaryBackground
      : `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, 90%)`,
    linkColor: prefersMoreContrast
      ? "rgb(0,0,0)"
      : `hsl(${accent.darker.hue}deg, ${accent.darker.saturation}%, ${accent.darker.lightness}%, 100%)`,
    standardIcon: darken(interactiveColor, 0.5),
    hcFg: "rgb(0,0,0)",
    hcBg: "rgb(255,255,255)",
    rowEven: `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%, 6%)`,
    rowHover: `hsl(${accent.background.hue}deg, 40%, ${accent.background.lightness}%, 25%)`,
    rowSelected: `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%, 20%)`,
    cardShadow: `hsl(${accent.main.hue}deg, 100%, 20%, 20%) 0px 2px 8px 0px`,
    avatarBackground: "rgb(255,255,255)",
    avatarColor: mainAccentColor,
    alertInfoText: "hsl(206.47deg 53.13% 25.1%)",
    alertSuccessText: "hsl(123deg 40% 19.61%)",
  };
}

/** sRGB of the booking stack's dark `--background` and `--card` tokens. */
const DARK_PAGE: [number, number, number] = [9, 11, 12];
const DARK_PANEL: [number, number, number] = [22, 27, 29];

/**
 * Dark tokens keep each light token's hue and solve its lightness so that it
 * has the same WCAG contrast against the dark surface as the light token has
 * against white, raised to 4.5:1 for text and 3:1 for interactive elements.
 */
export function darkTokens(accent: AccentColor, prefersMoreContrast: boolean): AccentTokens {
  const bg = accent.background;
  const main = accent.main;
  const white: HslColor = { hue: bg.hue, saturation: bg.saturation, lightness: 100 };
  const lightPage: HslColor = { hue: bg.hue, saturation: bg.saturation, lightness: 98 };
  const page = matchLuminance({ hue: bg.hue, saturation: Math.min(bg.saturation, 20) }, DARK_PAGE);
  const panel = matchLuminance({ hue: bg.hue, saturation: Math.min(bg.saturation, 15) }, DARK_PANEL);

  if (prefersMoreContrast) {
    const black = "rgb(0,0,0)";
    const fg = "rgb(255,255,255)";
    return {
      mode: "dark",
      mainAccentColor: fg,
      disabledColor: hslString({ hue: main.hue, saturation: 10, lightness: 40 }),
      linkButtonText: fg,
      accentedBackground: fg,
      dialogBorder: `3px solid ${fg}`,
      accentedBorder: `2px solid ${fg}`,
      mainBackground: black,
      secondaryBackground: hslString({ hue: bg.hue, saturation: bg.saturation, lightness: 0 }),
      contrastTextColor: black,
      backgroundContrastTextColor: fg,
      interactiveColor: fg,
      lighterInteractiveColor: black,
      linkColor: fg,
      standardIcon: fg,
      hcFg: fg,
      hcBg: black,
      rowEven: "rgba(255,255,255,0.06)",
      rowHover: "rgba(255,255,255,0.16)",
      rowSelected: "rgba(255,255,255,0.24)",
      cardShadow: "none",
      avatarBackground: black,
      avatarColor: fg,
      alertInfoText: null,
      alertSuccessText: null,
    };
  }

  const solveLike = (light: HslColor, lightAgainst: HslColor, darkAgainst: HslColor, floor: number, satCap = 100) =>
    solveLightness(
      { hue: light.hue, saturation: Math.min(light.saturation, satCap) },
      darkAgainst,
      Math.min(Math.max(hslContrast(light, lightAgainst), floor), 15.5),
      "lighter",
    );

  // The app bar band keeps its light-mode separation from the panel, but is
  // lowered when its text could not otherwise reach the target contrast.
  const contrastTarget = Math.max(hslContrast(hslOf(accent.contrastText), hslOf(bg)), 4.5);
  let bandRatio = hslContrast(hslOf(bg), white);
  let band: HslColor;
  let contrastText: HslColor;
  for (;;) {
    band = solveLightness({ hue: bg.hue, saturation: Math.min(bg.saturation, 40) }, panel, bandRatio, "lighter");
    contrastText = solveLightness(
      { hue: accent.contrastText.hue, saturation: Math.min(accent.contrastText.saturation, 30) },
      band,
      contrastTarget,
      "lighter",
    );
    if (hslContrast(contrastText, band) >= contrastTarget - 0.01 || bandRatio <= 1.05) break;
    bandRatio -= 0.05;
  }
  // Filled primary elements sit darker than their (now light) text.
  const mainAccent = solveLightness(
    main,
    contrastText,
    Math.max(hslContrast(hslOf(accent.contrastText), hslOf(main)), 4.5),
    "darker",
  );
  const interactiveLight: HslColor = { ...hslOf(main), lightness: main.lightness * 0.625 };
  const interactive = solveLike(interactiveLight, white, panel, 3);
  const standardIcon = solveLike({ ...interactiveLight, lightness: main.lightness * 0.3125 }, white, panel, 4.5);
  const linkButtonText = solveLike({ ...hslOf(main), lightness: main.lightness * 0.5 }, white, panel, 4.5);
  const link = solveLike(hslOf(accent.darker), lightPage, panel, 4.5);
  const text = solveLike(hslOf(accent.backgroundContrastText), lightPage, page, 4.5, 30);
  const lighterInteractive = solveLike({ ...hslOf(main), lightness: 90 }, white, panel, 0, 35);
  const disabled = solveLike(
    { hue: main.hue, saturation: 10, lightness: main.lightness + (100 - main.lightness) * 0.5 },
    white,
    panel,
    0,
  );
  const accentedBackground = hslString(band);
  return {
    mode: "dark",
    mainAccentColor: hslString(mainAccent),
    disabledColor: hslString(disabled),
    linkButtonText: hslString(linkButtonText),
    accentedBackground,
    dialogBorder: `3px solid ${accentedBackground}`,
    accentedBorder: `2px solid ${accentedBackground}`,
    mainBackground: hslString(page),
    secondaryBackground: hslString(panel),
    contrastTextColor: hslString(contrastText),
    backgroundContrastTextColor: hslString(text),
    interactiveColor: hslString(interactive),
    lighterInteractiveColor: hslString(lighterInteractive),
    linkColor: hslString(link),
    standardIcon: hslString(standardIcon),
    hcFg: "rgb(255,255,255)",
    hcBg: "rgb(0,0,0)",
    rowEven: alpha(accentedBackground, 0.3),
    rowHover: alpha(accentedBackground, 0.6),
    rowSelected: alpha(accentedBackground, 0.9),
    cardShadow: "rgba(0, 0, 0, 0.6) 0px 2px 8px 0px",
    avatarBackground: hslString(panel),
    avatarColor: hslString(link),
    alertInfoText: null,
    alertSuccessText: null,
  };
}

/*
 * When interactive elements such as buttons and chips are hovered over,
 * their background and borders should be subtly darkened (lightened in dark
 * mode) by this amount.
 */
const hoverDarkenCoefficient = 0.05;

function shift(t: AccentTokens, color: string, coefficient: number): string {
  return t.mode === "dark" ? lighten(color, coefficient) : darken(color, coefficient);
}

/**
 * Wraps a style slot so it is computed from the active colour scheme.
 */
function slot(fn: (t: AccentTokens, theme: Theme) => CSSObject) {
  // A theme derived from a non-accented one has no tokens; leave those slots unstyled.
  return ({ theme }: { theme: Theme }) => (theme.palette.accent ? fn(theme.palette.accent, theme) : {});
}

function accentPalette(t: AccentTokens) {
  return {
    accent: t,
    DataGrid: {
      bg: t.mainBackground,
    },
    primary: {
      main: t.mainAccentColor,
      contrastText: t.contrastTextColor,
      saturated: t.linkColor,
      background: t.accentedBackground,
      dark: t.linkColor,
    },
    callToAction: {
      main: (baseTheme.palette.callToAction as SimplePaletteColorOptions).main,
    },
    standardIcon: {
      main: t.standardIcon,
    },
  };
}

/**
 * The accented theme is used for pages that use the new styling, wherein the
 * page (or just part thereof) is styled with an accent colour. This is being
 * done to provide a consistent branding for the various parts of the product
 * as they are interwoven, as well as the integrations with third-parties.
 *
 * This function creates a new theme, given an accent colour, with a light
 * and a dark colour scheme.
 */
export default function createAccentedTheme(accent: AccentColor): Theme {
  const prefersMoreContrast = window.matchMedia("(prefers-contrast: more)").matches;
  const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const light = lightTokens(accent, prefersMoreContrast);
  const dark = darkTokens(accent, prefersMoreContrast);

  const {
    palette: _palette,
    colorSchemes: _colorSchemes,
    components: baseComponents,
    borders: _borders,
    ...baseOptions
  } = baseTheme as unknown as ThemeOptions & { colorSchemes: unknown };

  const lightScheme = deepmerge(baseColorSchemes.light as object, {
    palette: accentPalette(light),
    borders: { card: light.accentedBorder, section: light.accentedBorder },
  }) as { palette: ThemeOptions["palette"]; borders: ThemeOptions["borders"] };
  const components = deepmerge(baseComponents ?? {}, accentComponents(prefersMoreContrast, prefersReducedMotion));
  if (!darkModeAvailable) {
    return createTheme({ ...baseOptions, ...lightScheme, components });
  }
  return createTheme({
    ...baseOptions,
    colorSchemes: {
      light: lightScheme,
      dark: deepmerge(baseColorSchemes.dark as object, {
        palette: {
          ...accentPalette(dark),
          background: { default: dark.mainBackground, paper: dark.secondaryBackground, alt: dark.secondaryBackground },
          text: { primary: dark.backgroundContrastTextColor },
        },
        borders: { card: dark.accentedBorder, section: dark.accentedBorder },
      }),
    } as unknown as ThemeOptions["colorSchemes"],
    components,
  });
}

function accentComponents(prefersMoreContrast: boolean, prefersReducedMotion: boolean): ThemeOptions["components"] {
  return {
    MuiAppBar: {
      styleOverrides: {
        /*
         * Generally, we convert the accent colour to black when the user
         * has requested more contrast, but in the app bar we convert to
         * white as otherwise there would be a big block of black at the
         * top of the viewport, which is excessively distracting.
         */
        root: slot((t) => ({
          boxShadow: "unset",
          [`& > .${toolbarClasses.root}`]: {
            paddingBottom: baseTheme.spacing(0.25),
            paddingLeft: 0,
            paddingRight: `${baseTheme.spacing(1)} !important`,
            color: prefersMoreContrast ? t.hcFg : t.contrastTextColor,
            background: prefersMoreContrast ? t.hcBg : t.accentedBackground,
            borderBottom: prefersMoreContrast ? t.accentedBorder : "none",
            [`& .${typographyClasses.h6}`]: {
              color: prefersMoreContrast ? t.hcFg : t.contrastTextColor,
            },
            [`& .${svgIconClasses.root}`]: {
              color: prefersMoreContrast ? t.hcFg : t.contrastTextColor,
              transition: "all .3s ease",
            },
            [`& .${iconButtonClasses.root}.${iconButtonClasses.disabled}`]: {
              [`& .${svgIconClasses.root}`]: {
                opacity: 0.5,
              },
            },
          },
          [`& .${textFieldClasses.root}`]: {
            background: t.lighterInteractiveColor,
            borderRadius: "3px",
            [`& .${inputBaseClasses.root}`]: {
              paddingLeft: baseTheme.spacing(1),
              [`&:has(.${inputAdornmentClasses.positionStart})`]: {
                paddingLeft: 0,
              },
              [`& .${inputAdornmentClasses.root}`]: {
                paddingLeft: baseTheme.spacing(1),
                paddingRight: baseTheme.spacing(1),
                [`& .${svgIconClasses.root}`]: {
                  fill: prefersMoreContrast ? t.hcFg : t.contrastTextColor,
                },
              },
              "& input": {
                padding: baseTheme.spacing(0.5),
                paddingLeft: baseTheme.spacing(1),
                color: prefersMoreContrast ? t.hcFg : t.contrastTextColor,
                fontWeight: prefersMoreContrast ? "700" : "initial",
              },
              "& fieldset": {
                border: prefersMoreContrast ? t.accentedBorder : "none",
              },
            },
          },
          [`& .${dividerClasses.root}`]: {
            borderRight: t.accentedBorder,
            borderColor: prefersMoreContrast ? t.hcFg : alpha(t.contrastTextColor, 0.2),
          },
          [`& .${linkClasses.root}`]: {
            textTransform: "capitalize",
            color: t.contrastTextColor,
            "&[href]": {
              cursor: "pointer",
              fontWeight: "400",
              letterSpacing: "0.02em",
              paddingTop: baseTheme.spacing(0.625),
              paddingBottom: baseTheme.spacing(0.25),
              paddingLeft: baseTheme.spacing(0.5),
              paddingRight: baseTheme.spacing(0.5),
              textDecoration: "unset !important",
              color: `${prefersMoreContrast ? t.hcFg : t.contrastTextColor} !important`,
              "&::after": {
                content: "''",
                width: "100%",
                height: "2px",
                backgroundColor: prefersMoreContrast ? t.hcFg : t.contrastTextColor,
                position: "relative",
                left: 0,
                bottom: "2px",
                display: "block",
                transform: "translateY(2px)",
                opacity: "0",
                transition: "all .1s ease-in-out",
              },
              "&:hover": {
                "&::after": {
                  opacity: "0.75",
                  transform: "translateY(0px)",
                },
              },
              '&[aria-current="page"]': {
                "&::after": {
                  opacity: "0.9",
                  transform: "translateY(0px)",
                  left: baseTheme.spacing(-1),
                  width: `calc(100% + ${baseTheme.spacing(2)})`,
                  bottom: "-2px",
                },
                lineHeight: "25px",
                marginTop: baseTheme.spacing(0.25),
                paddingTop: baseTheme.spacing(0.25),
                paddingLeft: baseTheme.spacing(1),
                paddingRight: baseTheme.spacing(1),
                backgroundColor: t.lighterInteractiveColor,
                borderTopLeftRadius: "4px",
                borderTopRightRadius: "4px",
              },
            },
          },
          [`& .${listItemButtonClasses.root}`]: {
            boxShadow: "unset",
            backgroundColor: prefersMoreContrast ? t.hcBg : t.accentedBackground,
            border: t.accentedBorder,
            borderColor: shift(t, t.accentedBackground, hoverDarkenCoefficient),
            padding: "0 12px",
            borderRadius: "3px",
            [`& .${listItemTextClasses.root}`]: {
              margin: "3px 0",
            },
            [`& .${listItemTextClasses.primary}`]: {
              fontWeight: "500",
              letterSpacing: "0.02em",
              fontSize: "0.95rem",
            },
            [`& .${listItemIconClasses.root}`]: {
              minWidth: "unset",
              [`& .${svgIconClasses.root}`]: {
                fontSize: "1.3em",
                marginLeft: "4px",
              },
            },
          },
        })),
      },
    },
    MuiDialog: {
      defaultProps: {
        transitionDuration: prefersReducedMotion ? 0 : 200,
      },
      styleOverrides: {
        paper: slot((t) => ({
          overflow: "hidden",
          border: t.dialogBorder,
          borderRadius: 6,
          backgroundColor: t.mainBackground,
        })),
        paperFullScreen: {
          borderRadius: 0,
        },
      },
    },
    MuiDialogContent: {
      styleOverrides: {
        root: slot((t) => ({
          backgroundColor: t.mainBackground,
          padding: baseTheme.spacing(2),
        })),
      },
    },
    MuiDialogTitle: {
      styleOverrides: {
        root: slot((t) => ({
          backgroundColor: t.mainBackground,
          borderBottom: t.dialogBorder,
          borderWidth: "2px",
          textTransform: "uppercase", // this is where it is coming from
          fontWeight: 700,
          lineHeight: 1.167,
          padding: "0",
          margin: baseTheme.spacing(2),
        })),
      },
    },
    MuiDialogContentText: {
      styleOverrides: {
        root: slot((t) => ({
          color: t.backgroundContrastTextColor,
        })),
      },
    },
    MuiDialogActions: {
      styleOverrides: {
        root: slot((t) => ({
          backgroundColor: t.mainBackground,
        })),
      },
    },
    MuiDrawer: {
      styleOverrides: {
        root: slot((t) => ({
          transition: prefersReducedMotion ? "none !important" : "width .25s cubic-bezier(0.4, 0, 0.2, 1)",
          [`& .${listItemButtonClasses.root}`]: {
            paddingLeft: baseTheme.spacing(3),
            border: "none",
            borderRadius: 0,
            background: "unset",
            boxShadow: "none",
            [`& .${svgIconClasses.root}`]: {
              color: "unset",
              fontSize: "1rem",
            },
            [`&.${listItemButtonClasses.selected}`]: {
              backgroundColor: t.accentedBackground,
              [`& .${typographyClasses.root}`]: {
                color: prefersMoreContrast ? t.hcBg : t.contrastTextColor,
              },
              [`& .${listItemIconClasses.root}`]: {
                color: prefersMoreContrast ? t.hcBg : t.contrastTextColor,
              },
              "&:hover": {
                backgroundColor: t.mainAccentColor,
              },
            },
            "&:hover": {
              backgroundColor: shift(t, t.secondaryBackground, hoverDarkenCoefficient),
            },
            [`& .${touchRippleClasses.root}`]: {
              color: t.accentedBackground,
            },
            [`& .${listItemIconClasses.root}`]: {
              minWidth: baseTheme.spacing(4),
              color: prefersMoreContrast ? t.hcFg : t.standardIcon,
            },
            [`& .${typographyClasses.body1}`]: {
              fontWeight: 700,
              fontSize: "0.8rem",
            },
          },
          [`& .${paperClasses.root}`]: {
            backgroundColor: t.secondaryBackground,
            borderRight: t.accentedBorder,
            transition: prefersReducedMotion ? "none !important" : "width .25s cubic-bezier(0.4, 0, 0.2, 1)",
          },
        })),
      },
    },
    MuiCard: {
      styleOverrides: {
        root: slot((t) => ({
          border: t.accentedBorder,
          [`&:has(.${cardActionAreaClasses.root})`]: {
            "&:hover": {
              backgroundColor: t.secondaryBackground,
              borderColor: shift(t, t.accentedBackground, hoverDarkenCoefficient),
            },
          },
        })),
      },
    },
    MuiButton: {
      defaultProps: {
        disableElevation: true,
      },
      styleOverrides: {
        root: slot((t) => ({
          paddingTop: baseTheme.spacing(0.25),
          paddingBottom: baseTheme.spacing(0.25),
          paddingLeft: baseTheme.spacing(1.5),
          paddingRight: baseTheme.spacing(1.5),
          transition: "all ease 0.3s",
          fontWeight: 700,
          letterSpacing: "0.04em",
          color: prefersMoreContrast ? t.hcFg : t.linkButtonText,
          borderRadius: "3px",
          border: t.accentedBorder,
          "&:hover": {
            borderColor: shift(t, t.accentedBackground, hoverDarkenCoefficient),
          },
        })),
        colorPrimary: slot((t) => ({
          border: t.accentedBorder,
          [`&.${buttonClasses.disabled}`]: {
            border: t.accentedBorder,
            borderColor: t.disabledColor,
          },
        })),
        outlined: slot((t) => ({
          color: t.linkButtonText,
          border: t.accentedBorder,
          "&:hover": {
            border: t.accentedBorder,
            borderColor: shift(t, t.accentedBackground, hoverDarkenCoefficient),
          },
          [`&.${buttonClasses.disabled}`]: {
            border: t.accentedBorder,
            borderColor: t.disabledColor,
          },
        })),
      },
      variants: [
        {
          props: { variant: "contained", color: "callToAction" as "primary" },
          style: slot((t) => ({
            border: "none",
            paddingTop: baseTheme.spacing(0.5),
            paddingBottom: baseTheme.spacing(0.5),
            backgroundColor: prefersMoreContrast
              ? t.hcFg
              : (baseTheme.palette.callToAction as SimplePaletteColorOptions).main,
            color: prefersMoreContrast
              ? t.hcBg
              : (baseTheme.palette.callToAction as SimplePaletteColorOptions).contrastText,
            "&:hover": {
              backgroundColor: prefersMoreContrast
                ? t.hcFg
                : (baseTheme.palette.callToAction as SimplePaletteColorOptions).main,
            },
            "&&": {
              color: prefersMoreContrast
                ? t.hcBg
                : (baseTheme.palette.callToAction as SimplePaletteColorOptions).contrastText,
              "@media (prefers-contrast: more), (forced-colors: active)": {
                backgroundColor: t.hcFg,
                color: t.hcBg,
                forcedColorAdjust: "none",
                "&:hover": {
                  backgroundColor: t.hcFg,
                },
              },
            },
          })),
        },
        {
          props: { variant: "contained", color: "primary" },
          style: slot((t) => ({
            backgroundColor: t.accentedBackground,
            color: t.contrastTextColor,
            borderColor: t.accentedBackground,
            "&:hover": {
              borderColor: shift(t, t.mainAccentColor, hoverDarkenCoefficient),
              backgroundColor: shift(t, t.mainAccentColor, hoverDarkenCoefficient),
            },
            [`&.${buttonClasses.disabled}`]: {
              backgroundColor: t.disabledColor,
              borderColor: t.disabledColor,
            },
          })),
        },
        {
          props: { variant: "outlined", color: "primary" },
          style: slot((t) => ({
            color: t.interactiveColor,
            "&:hover": {
              /*
               * we have to replicate specifying the border width here
               * because DataGrid doesn't just set the borderColor in its
               * :hover style so if we don't also set set the whole border
               * then our width gets unset
               */
              border: t.accentedBorder,
            },
          })),
        },
        {
          props: { variant: "outlined", color: "error" },
          style: ({ theme }: { theme: Theme }) => ({
            color: theme.palette.error.main,
            borderColor: theme.palette.error.main,
          }),
        },
      ],
    },
    MuiButtonGroup: {
      styleOverrides: {
        root: {
          [`& .${buttonGroupClasses.firstButton}, & .${buttonGroupClasses.middleButton}`]: {
            borderRight: "none",
          },
        },
      },
    },
    MuiIconButton: {
      styleOverrides: {
        colorPrimary: slot((t) => ({
          color: prefersMoreContrast ? t.hcFg : t.linkColor,
        })),
      },
    },
    MuiCheckbox: {
      styleOverrides: {
        root: slot((t) => ({
          color: prefersMoreContrast ? t.hcFg : t.interactiveColor,
          [`&.${checkboxClasses.checked}`]: {
            color: prefersMoreContrast ? t.hcFg : t.interactiveColor,
          },
        })),
      },
    },
    MuiRadio: {
      styleOverrides: {
        root: slot((t) => ({
          color: prefersMoreContrast ? t.hcFg : t.interactiveColor,
          [`&.${radioClasses.checked}`]: {
            color: prefersMoreContrast ? t.hcFg : t.interactiveColor,
          },
        })),
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: slot((t) => ({
          paddingLeft: baseTheme.spacing(0.5),
          paddingRight: baseTheme.spacing(0.5),
          "& input": {
            color: t.backgroundContrastTextColor,
          },
          // this has to be here (i.e. :hover and then
          // .MuiOutlinedInput-notchedOutline inside) because of the way
          // Mui applies its styles
          "&:hover": {
            [`& .${outlinedInputClasses.notchedOutline}`]: {
              /*
               * These !importants are needed because when a className is
               * applied to a TextField, it is attached to the
               * .MuiFormControl-root that wraps .MuiOutlinedInput-root, so
               * that would have a higher specificity than this style. As
               * such, without the !important it would be impossible for a
               * styled component to disable the border when not hovering,
               * but keep it when hovering, as it would override all styles
               * on the notched outline. With the !important, if a style
               * components want to override the hover effect too, they too
               * can use !important
               */
              borderColor: `${shift(t, t.accentedBackground, hoverDarkenCoefficient)} !important`,
            },
          },
          [`&:has(.${inputAdornmentClasses.positionStart})`]: {
            paddingLeft: 0,
            [`& .${outlinedInputClasses.input}`]: {
              paddingTop: "5px",
              paddingBottom: "5px",
              paddingLeft: baseTheme.spacing(1.5),
            },
          },
          [`& .${inputAdornmentClasses.root}`]: {
            height: "100%",
            [`&.${inputAdornmentClasses.positionStart}`]: {
              paddingLeft: baseTheme.spacing(1),
              paddingRight: baseTheme.spacing(0.5),
              marginTop: baseTheme.spacing(0.5),
              marginBottom: baseTheme.spacing(0.5),
              borderRight: t.accentedBorder,
              marginRight: 0,
              [`& .${iconButtonClasses.root}`]: {
                padding: baseTheme.spacing(0.25),
              },
            },
            [`&.${inputAdornmentClasses.positionEnd}`]: {
              paddingRight: baseTheme.spacing(1.5),
              paddingLeft: baseTheme.spacing(1.5),
              borderLeft: t.accentedBorder,
              marginLeft: 0,
              [`&:has(.${selectClasses.select})`]: {
                paddingRight: 0,
                paddingLeft: 0,
              },
            },
            [`& .${typographyClasses.root}`]: {
              textTransform: "uppercase",
              fontWeight: 700,
              fontSize: "0.8125rem",
              lineHeight: "20px",
            },
          },
          [`&.${outlinedInputClasses.disabled}`]: {
            borderColor: t.disabledColor,
            [`& .${outlinedInputClasses.notchedOutline}`]: {
              borderColor: t.disabledColor,
            },
            [`& .${inputAdornmentClasses.root}`]: {
              borderRightColor: t.disabledColor,
              color: t.disabledColor,
            },
            "&:hover": {
              [`& .${outlinedInputClasses.notchedOutline}`]: {
                borderColor: `${t.disabledColor} !important`,
              },
            },
          },
        })),
        notchedOutline: slot((t) => ({
          borderColor: t.accentedBackground,
          borderRadius: 3,
          borderWidth: "2px",
          transition: "all ease 0.3s",
        })),
      },
    },
    MuiDataGrid: {
      defaultProps: {
        getRowClassName: (params: { indexRelativeToCurrentPage: number }) =>
          params.indexRelativeToCurrentPage % 2 === 0 ? "even" : "odd",
      },
      styleOverrides: {
        root: slot((t) => ({
          "--DataGrid-containerBackground": t.mainBackground,
          border: "none",
          /*
           * typo.css is adding a margin to the bottom of all paragraphs,
           * breaking the layout of the DataGrid's pagination controls.
           */
          "& p": {
            marginBottom: 0,
          },
          [`& .${gridClasses.withBorderColor}`]: {
            borderColor: t.accentedBackground,
            borderWidth: "1px",
          },
          [`& .${gridClasses.columnSeparator}`]: {
            visibility: "visible",
            color: t.accentedBackground,
          },
          [`& .${gridClasses.menuIcon}`]: {
            width: "unset !important",
            visibility: "visible !important",
          },
          [`& .${gridClasses.columnHeader}`]: {
            color: t.backgroundContrastTextColor,
          },
          [`& .${gridClasses.row}`]: {
            "&.even": {
              backgroundColor: t.rowEven,
            },
            "&:hover": {
              backgroundColor: t.rowHover,
            },
            // DataGrid exposes no state-class constant; its own
            // getDataGridUtilityClass resolves the global Mui-selected class.
            [`&.${getDataGridUtilityClass("selected")}`]: {
              backgroundColor: t.rowSelected,
            },
          },
          [`& .${gridClasses.toolbarContainer}`]: {
            paddingRight: 0,
            marginRight: "-2px",
            marginLeft: "-4px",
            marginBottom: baseTheme.spacing(1),
          },
          [`& .${buttonClasses.root}`]: {
            "&:hover": {
              /*
               * we have to replicate specifying the border width here
               * because DataGrid doesn't just set the borderColor in its
               * :hover style so if we don't also set set the whole border
               * then our width gets unset
               */
              border: t.accentedBorder,
              borderColor: shift(t, t.accentedBackground, hoverDarkenCoefficient),
            },
          },
          [`& .${buttonClasses.outlined}.${buttonClasses.colorPrimary}`]: {
            color: t.linkButtonText,
            border: t.accentedBorder,
            "&:hover": {
              borderColor: shift(t, t.accentedBackground, hoverDarkenCoefficient),
            },
          },
        })),
        paper: slot((t) => ({
          boxShadow: "none",
          border: t.accentedBorder,
        })),
        menu: slot((t) => ({
          [`& .${paperClasses.root}`]: {
            boxShadow: "none",
          },
          [`& .${gridClasses.menuList}`]: {
            border: t.accentedBorder,
          },
        })),
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: slot((t) => ({
          border: t.accentedBorder,
          borderRadius: 8,
          background: t.secondaryBackground,
          boxShadow: t.cardShadow,
          [`& .${svgIconClasses.root}`]: {
            color: t.linkColor,
            fontSize: "2.8em",
          },
          "&:focus-visible": {
            backgroundColor: shift(t, t.secondaryBackground, hoverDarkenCoefficient),
          },
        })),
      },
    },
    MuiTypography: {
      styleOverrides: {
        root: slot((t) => ({
          letterSpacing: "0.02em",
          color: t.backgroundContrastTextColor,
          [`&.${alertTitleClasses.root}`]: {
            color: "inherit",
            fontWeight: 700,
          },
          "& kbd": {
            fontWeight: 700,
          },
          "& cite": {
            fontWeight: 700,
            fontStyle: "normal",
          },
        })),
        h1: slot((t) => ({
          color: t.linkColor,
          marginBottom: baseTheme.spacing(3),
          textAlign: "left",
          borderBottom: t.accentedBorder,
          textTransform: "uppercase",
          fontWeight: 700,
          fontSize: "1.9rem",
        })),
        h3: slot((t) => ({
          fontWeight: 700,
          fontSize: "1.2rem",
          letterSpacing: "0.02em",
          opacity: "0.9",
          textTransform: "uppercase",
          borderBottom: t.accentedBorder,
        })),
        h4: {
          fontWeight: 700,
          textTransform: "uppercase",
          fontSize: "0.9rem",
          letterSpacing: "0.01em",
        },
      },
    },
    MuiLink: {
      defaultProps: {
        target: "_blank",
        rel: "noreferrer",
      },
      styleOverrides: {
        root: slot((t) => ({
          color: `${t.linkColor} !important`,
          fontWeight: 700,
          textDecorationColor: t.mainAccentColor,
          transition: "all .3s ease",
          textUnderlineOffset: "2px",
          textDecoration: "underline !important",
          "&:link": {
            textDecoration: "underline !important",
          },
        })),
      },
    },
    MuiDivider: {
      styleOverrides: {
        root: slot((t) => ({
          borderBottom: t.accentedBorder,
        })),
        vertical: slot((t) => ({
          borderBottom: "none",
          borderRight: t.accentedBorder,
        })),
        withChildren: slot((t) => ({
          borderBottom: "none",
          marginTop: baseTheme.spacing(-0.5),
          marginBottom: baseTheme.spacing(-0.5),
          "&:before, &:after": {
            borderTop: t.accentedBorder,
          },
          [`& .${dividerClasses.wrapper}`]: {
            fontWeight: 500,
            fontSize: "0.9em",
            color: t.backgroundContrastTextColor,
          },
        })),
      },
    },
    MuiBreadcrumbs: {
      styleOverrides: {
        separator: slot((t) => ({
          color: t.backgroundContrastTextColor,
        })),
      },
    },
    MuiChip: {
      styleOverrides: {
        root: slot((t) => ({
          padding: baseTheme.spacing(0.25, 0.5),
          fontWeight: 500,
          letterSpacing: "0.03em",
          color: t.backgroundContrastTextColor,
          [`&.${chipClasses.filled}`]: {
            backgroundColor: t.accentedBackground,
            color: t.contrastTextColor,
            [`& .${chipClasses.deleteIcon}`]: {
              color: t.contrastTextColor,
            },
          },
          [`&.${chipClasses.filled}.${chipClasses.clickable}`]: {
            backgroundColor: t.lighterInteractiveColor,
            color: t.linkColor,
            border: prefersMoreContrast ? t.accentedBorder : "none",
            "&:hover": {
              backgroundColor: shift(t, t.lighterInteractiveColor, hoverDarkenCoefficient),
            },
          },
          [`&.${chipClasses.clickable}`]: {
            color: t.linkColor,
            [`& .${chipClasses.icon}`]: {
              color: t.linkColor,
            },
          },
          [`&.${chipClasses.outlined}`]: {
            border: t.accentedBorder,
            [`& .${svgIconClasses.root}`]: {
              color: t.linkColor,
            },
          },
          [`&.${chipClasses.deletable}`]: {
            padding: 0,
          },
        })),
      },
    },
    MuiFormLabel: {
      styleOverrides: {
        root: slot((t) => ({
          marginBottom: baseTheme.spacing(0.5),
          fontWeight: 700,
          fontSize: "0.9rem",
          letterSpacing: "0.02em",
          color: t.backgroundContrastTextColor,
          [`&.${formLabelClasses.focused}`]: {
            color: t.backgroundContrastTextColor,
          },
        })),
      },
    },
    MuiAlert: {
      variants: [
        {
          props: { variant: "standard", severity: "info" },
          style: slot((t, theme) => ({
            [`& .${typographyClasses.root}`]: {
              color: `${t.alertInfoText ?? lighten(theme.palette.info.light, 0.6)} !important`,
            },
          })),
        },
        {
          props: { variant: "standard", severity: "success" },
          style: slot((t, theme) => ({
            [`& .${typographyClasses.root}`]: {
              color: `${t.alertSuccessText ?? lighten(theme.palette.success.light, 0.6)} !important`,
            },
          })),
        },
      ],
    },
    MuiAvatar: {
      styleOverrides: {
        root: slot((t) => ({
          backgroundColor: t.avatarBackground,
          color: t.avatarColor,
        })),
      },
    },
    MuiMenu: {
      styleOverrides: {
        paper: slot((t) => ({
          boxShadow: "none",
          border: t.accentedBorder,
          ...(prefersReducedMotion
            ? {
                transition: "none !important",
              }
            : {}),
        })),
      },
    },
    MuiPopover: {
      styleOverrides: {
        paper: {
          ...(prefersReducedMotion
            ? {
                transition: "none !important",
              }
            : {}),
        },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          [`& .${formLabelClasses.root}`]: {
            [`& .${inputLabelClasses.root}`]: {
              [`& .${inputLabelClasses.outlined}`]: {
                fontSize: "0.9375em",
              },
            },
          },
        },
      },
    },
    MuiTreeItem: {
      styleOverrides: {
        root: {
          marginTop: baseTheme.spacing(0.5),
        },
        content: slot((t) => ({
          cursor: "default",
          backgroundColor: prefersMoreContrast
            ? "transparent"
            : shift(t, t.secondaryBackground, hoverDarkenCoefficient),
          border: prefersMoreContrast ? t.accentedBorder : "none",
          maxWidth: "fit-content",
          "&:hover": {
            backgroundColor: prefersMoreContrast
              ? "transparent"
              : shift(t, t.secondaryBackground, hoverDarkenCoefficient * 2),
          },
          // x-tree-view v9: selection/focus are data attributes, not Mui-selected/Mui-focused classes.
          "&[data-selected]": {
            backgroundColor: t.accentedBackground,
            "&[data-focused]": {
              backgroundColor: prefersMoreContrast
                ? t.accentedBackground
                : shift(t, t.secondaryBackground, hoverDarkenCoefficient * 5),
            },
            [`& .${treeItemClasses.label}`]: {
              color: t.contrastTextColor,
              [`& .${typographyClasses.root}`]: {
                color: t.contrastTextColor,
              },
            },
            [`& .${treeItemClasses.iconContainer}`]: {
              color: t.contrastTextColor,
            },
            "&:hover": {
              backgroundColor: prefersMoreContrast
                ? t.accentedBackground
                : shift(t, t.secondaryBackground, hoverDarkenCoefficient * 6),
            },
          },
        })),
        label: slot((t) => ({
          fontWeight: 500,
          letterSpacing: "0.01em",
          color: t.backgroundContrastTextColor,
          marginRight: baseTheme.spacing(1),
        })),
        iconContainer: slot((t) => ({
          color: t.backgroundContrastTextColor,
        })),
        groupTransition: {
          paddingLeft: "calc(2 * var(--TreeView-itemChildrenIndentation))",
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: slot((t) => ({
          color: t.backgroundContrastTextColor,
        })),
      },
    },
    MuiTablePagination: {
      styleOverrides: {
        toolbar: slot((t) => ({
          background: t.mainBackground,
          color: t.backgroundContrastTextColor,
          minHeight: "unset !important",
        })),
      },
    },
  };
}
