//@flow strict

import { createTheme } from "@mui/material";
import baseTheme from "./theme";
import { mergeThemes } from "./util/styles";
import { darken, alpha, lighten } from "@mui/system";
import { toolbarClasses } from "@mui/material/Toolbar";
import { typographyClasses } from "@mui/material/Typography";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { textFieldClasses } from "@mui/material/TextField";
import { inputBaseClasses } from "@mui/material/InputBase";
import { dividerClasses } from "@mui/material/Divider";
import { listItemButtonClasses } from "@mui/material/ListItemButton";
import { listItemIconClasses } from "@mui/material/ListItemIcon";
import { paperClasses } from "@mui/material/Paper";
import { cardActionAreaClasses } from "@mui/material/CardActionArea";
import { buttonClasses } from "@mui/material/Button";
import { iconButtonClasses } from "@mui/material/IconButton";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import { gridClasses } from "@mui/x-data-grid";
import { alertTitleClasses } from "@mui/material/AlertTitle";
import { checkboxClasses } from "@mui/material/Checkbox";
import { radioClasses } from "@mui/material/Radio";
import { chipClasses } from "@mui/material/Chip";
import { formLabelClasses } from "@mui/material/FormLabel";
import { inputLabelClasses } from "@mui/material/InputLabel";
import { inputAdornmentClasses } from "@mui/material/InputAdornment";

/**
 * This theme is used for pages that use the new styling, wherein the page (or
 * just part thereof) is styled with an accent colour. This is being done to
 * provide a consistent branding for the various parts of the product as they
 * are interwoven, as well as the integrations with third-parties.
 */

type Hsl = {|
  hue: number,
  saturation: number,
  lightness: number,
  opacity?: number,
|};

type AccentColor = {|
  /**
   * This the main accent colour from which various variants are derived.
   * The lightness MUST not be less than 5.
   * DO NOT specify an opacity.
   */
  main: Hsl,

  /**
   * A darker variant used for link text, icons, etc.
   * DO NOT specify an opacity.
   */
  darker: Hsl,

  /**
   * Used where text appears atop a background of the main color, and thus MUST
   * have a contrast ratio of at least 4.5:1. If the accent if particularly
   * dark then an almost-white is recommended, otherwise an almost-black.
   * DO NOT specify an opacity.
   */
  contrastText: Hsl,

  /**
   * Used when the background of an element should have the accent, such as app
   * bars and filled buttons. The almost-white background of panels and dialogs
   * is also derived from this colour.
   *
   * Satuation SHOULD be around 30 and MUST be greater than 10.
   * Lightness SHOULD be around 80 and MUST be greater than 20.
   */
  background: Hsl,

  /**
   * The colour of general text. This SHOULD be pretty close to black.
   *
   * Saturation SHOULD be around 20.
   * Lightness SHOULD be around 30.
   */
  backgroundContrastText: Hsl,
|};

export default function createAccentedTheme(accent: AccentColor): { ... } {
  const prefersMoreContrast = window.matchMedia(
    "(prefers-contrast: more)"
  ).matches;
  const prefersReducedMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)"
  ).matches;

  // All of these strings are formatted specifically so MUI can parse them and perform its own arithmetic

  const mainAccentColor = prefersMoreContrast
    ? "rgb(0,0,0)"
    : `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%)`;
  const disabledColor = lighten(
    `hsl(${accent.main.hue}deg, 10%, ${accent.main.lightness}%)`,
    0.5
  );

  const linkButtonText = prefersMoreContrast
    ? "rgb(0,0,0)"
    : darken(
        `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%)`,
        0.5
      );

  /**
   * A background colour that can be used behind headers, toolbars, and other
   * elements to which the user's attention should be drawn. This colour, used
   * liberally across the UI, helps build a cohesive sense that of parts of the
   * UI (such as dialogs, cards, etc) belonging to different parts of the
   * product (such as the Gallery) based on their colour or being associated
   * with different organisations (such as DMPonline) where the colour is
   * similar to their respective branding.
   */
  const accentedBackground = prefersMoreContrast
    ? "rgb(0,0,0)"
    : `hsl(${accent.background.hue}deg, ${accent.background.saturation}%, ${accent.background.lightness}%)`;
  const dialogBorder = `3px solid ${accentedBackground}`;
  const accentedBorder = `2px solid ${accentedBackground}`;

  /**
   * The near-white background colour used for large parts of the UI such as the backgrounds of dialogs.
   */
  const mainBackground = prefersMoreContrast
    ? "rgb(255,255,255)"
    : `hsl(${accent.background.hue}deg, ${accent.background.saturation}%, 98%)`;
  /**
   * This may seem pointless to define as its just white, but defining it this
   * way allows us to dynamically apply darken
   */
  const secondaryBackground = `hsl(${accent.background.hue}deg, ${accent.background.saturation}%, 100%)`;

  // text on accentedBackground or main
  const contrastTextColor = prefersMoreContrast
    ? "rgb(255,255,255)"
    : `hsl(${accent.contrastText.hue}deg, ${accent.contrastText.saturation}%, ${accent.contrastText.lightness}%, 100%)`;
  // text on mainBackground
  const backgroundContrastTextColor = prefersMoreContrast
    ? "rgb(0,0,0)"
    : `hsl(${accent.backgroundContrastText.hue}deg, ${accent.backgroundContrastText.saturation}%, ${accent.backgroundContrastText.lightness}%, 100%)`;

  // Interactive elements: checkboxes, radiobuttons, and primary buttons
  const interactiveColor = prefersMoreContrast
    ? "rgb(0,0,0)"
    : darken(
        `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%)`,
        0.375
      );
  // Interactive elements: text fields in app bar, chips
  const lighterInteractiveColor = prefersMoreContrast
    ? secondaryBackground
    : `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, 90%)`;

  // Links are slightly darker to more closely match surrounding text
  const linkColor = prefersMoreContrast
    ? "rgb(0,0,0)"
    : `hsl(${accent.darker.hue}deg, ${accent.darker.saturation}%, ${accent.darker.lightness}%, 100%)`;

  /*
   * When interactive elements such as buttons and chips are hovered over,
   * their background and borders should be subtly darkened by this amount.
   */
  const hoverDarkenCoefficient = 0.05;

  return createTheme(
    mergeThemes(baseTheme, {
      palette: {
        primary: {
          main: mainAccentColor,
          contrastText: contrastTextColor,
          saturated: linkColor,
          dark: linkColor,
        },
        selection: {
          main: baseTheme.palette.primary.main,
        },
        standardIcon: {
          main: interactiveColor,
        },
      },
      borders: {
        card: accentedBorder,
      },
      components: {
        MuiAppBar: {
          styleOverrides: {
            root: {
              boxShadow: "unset",
              [`& > .${toolbarClasses.root}`]: {
                paddingBottom: baseTheme.spacing(0.25),
                paddingLeft: `${baseTheme.spacing(1.5)} !important`,
                paddingRight: `${baseTheme.spacing(1)} !important`,
                color: prefersMoreContrast ? "rgb(0,0,0)" : contrastTextColor,
                background: prefersMoreContrast ? "white" : accentedBackground,
                borderBottom: prefersMoreContrast ? accentedBorder : "none",
                [`& .${typographyClasses.root}`]: {
                  marginLeft: baseTheme.spacing(0.5),
                  letterSpacing: "0.02em",
                  color: prefersMoreContrast ? "rgb(0,0,0)" : contrastTextColor,
                },
                [`& .${svgIconClasses.root}`]: {
                  color: prefersMoreContrast ? "rgb(0,0,0)" : contrastTextColor,
                  transition: "all .3s ease",
                },
                [`& .${iconButtonClasses.root}.${iconButtonClasses.disabled}`]:
                  {
                    [`& .${svgIconClasses.root}`]: {
                      opacity: 0.5,
                    },
                  },
              },
              [`& .${textFieldClasses.root}`]: {
                background: lighterInteractiveColor,
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
                      fill: prefersMoreContrast
                        ? "rgb(0,0,0)"
                        : contrastTextColor,
                    },
                  },
                  "& input": {
                    padding: baseTheme.spacing(0.5),
                    paddingLeft: baseTheme.spacing(1),
                    color: prefersMoreContrast
                      ? "rgb(0,0,0)"
                      : contrastTextColor,
                    fontWeight: prefersMoreContrast ? "700" : "initial",
                  },
                  "& fieldset": {
                    border: prefersMoreContrast ? accentedBorder : "none",
                  },
                },
              },
              [`& .${dividerClasses.root}`]: {
                borderRight: accentedBorder,
                borderColor: prefersMoreContrast
                  ? "rgb(0,0,0)"
                  : alpha(contrastTextColor, 0.2),
              },
            },
          },
        },
        MuiDialog: {
          defaultProps: {
            TransitionProps: {
              timeout: prefersReducedMotion ? 0 : 200,
            },
          },
          styleOverrides: {
            paper: {
              overflow: "hidden",
              border: dialogBorder,
              borderRadius: 6,
              backgroundColor: mainBackground,
            },
            paperFullScreen: {
              borderRadius: 0,
            },
          },
        },
        MuiDialogContent: {
          styleOverrides: {
            root: {
              backgroundColor: mainBackground,
              padding: baseTheme.spacing(2),
            },
          },
        },
        MuiDialogTitle: {
          styleOverrides: {
            root: {
              backgroundColor: mainBackground,
              borderBottom: dialogBorder,
              borderWidth: "2px",
              textTransform: "uppercase",
              fontWeight: 700,
              lineHeight: 1.167,
              padding: "0",
              margin: baseTheme.spacing(2),
            },
          },
        },
        MuiDialogContentText: {
          styleOverrides: {
            root: {
              color: backgroundContrastTextColor,
            },
          },
        },
        MuiDialogActions: {
          styleOverrides: {
            root: {
              backgroundColor: mainBackground,
            },
          },
        },
        MuiDrawer: {
          styleOverrides: {
            root: {
              transition: prefersReducedMotion
                ? "none !important"
                : "width .25s cubic-bezier(0.4, 0, 0.2, 1)",
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
                  backgroundColor: accentedBackground,
                  [`& .${typographyClasses.root}`]: {
                    color: prefersMoreContrast ? "white" : contrastTextColor,
                  },
                  [`& .${listItemIconClasses.root}`]: {
                    color: prefersMoreContrast ? "white" : contrastTextColor,
                  },
                  "&:hover": {
                    backgroundColor: mainAccentColor,
                  },
                },
                "&:hover": {
                  backgroundColor: darken(
                    secondaryBackground,
                    hoverDarkenCoefficient
                  ),
                },
                "& .MuiTouchRipple-root": {
                  color: accentedBackground,
                },
                [`& .${listItemIconClasses.root}`]: {
                  minWidth: baseTheme.spacing(4),
                  color: prefersMoreContrast ? "rgb(0,0,0)" : linkColor,
                },
                [`& .${typographyClasses.body1}`]: {
                  fontWeight: 700,
                  fontSize: "0.8rem",
                },
              },
              [`& .${paperClasses.root}`]: {
                backgroundColor: secondaryBackground,
                borderRight: accentedBorder,
                transition: prefersReducedMotion
                  ? "none !important"
                  : "width .25s cubic-bezier(0.4, 0, 0.2, 1)",
              },
            },
          },
        },
        MuiCard: {
          styleOverrides: {
            root: {
              border: accentedBorder,
              [`&:has(.${cardActionAreaClasses.root})`]: {
                "&:hover": {
                  backgroundColor: "white",
                  borderColor: darken(
                    accentedBackground,
                    hoverDarkenCoefficient
                  ),
                },
              },
            },
          },
        },
        MuiButton: {
          defaultProps: {
            disableElevation: true,
          },
          styleOverrides: {
            root: {
              paddingTop: baseTheme.spacing(0.25),
              paddingBottom: baseTheme.spacing(0.25),
              paddingLeft: baseTheme.spacing(1.5),
              paddingRight: baseTheme.spacing(1.5),
              transition: "all ease 0.3s",
              fontWeight: 700,
              letterSpacing: "0.04em",
              color: prefersMoreContrast ? "rgb(0,0,0)" : linkButtonText,
              borderRadius: "3px",
              border: accentedBorder,
              "&:hover": {
                borderColor: darken(accentedBackground, hoverDarkenCoefficient),
              },
            },
            colorPrimary: {
              border: accentedBorder,
              [`&.${buttonClasses.disabled}`]: {
                border: accentedBorder,
                borderColor: disabledColor,
              },
            },
            containedSelection: {
              border: `2px solid ${
                prefersMoreContrast ? "black" : baseTheme.palette.primary.main
              }`,
              backgroundColor: prefersMoreContrast
                ? "black"
                : baseTheme.palette.primary.main,
              color: "white",
              "&:hover": {
                borderColor: baseTheme.palette.primary.main,
              },
            },
            containedPrimary: {
              backgroundColor: mainAccentColor,
              color: contrastTextColor,
              borderColor: mainAccentColor,
              "&:hover": {
                borderColor: darken(mainAccentColor, hoverDarkenCoefficient),
                backgroundColor: darken(
                  mainAccentColor,
                  hoverDarkenCoefficient
                ),
              },
              [`&.${buttonClasses.disabled}`]: {
                backgroundColor: disabledColor,
                borderColor: disabledColor,
              },
            },
            outlined: {
              color: linkButtonText,
              border: accentedBorder,
              "&:hover": {
                border: accentedBorder,
                borderColor: darken(accentedBackground, hoverDarkenCoefficient),
              },
            },
            outlinedPrimary: {
              color: interactiveColor,
              borderColor: interactiveColor,
              "&:hover": {
                /*
                 * we have to replicate specifying the border width here
                 * because DataGrid doesn't just set the borderColor in its
                 * :hover style so if we don't also set set the whole border
                 * then our width gets unset
                 */
                border: accentedBorder,
                borderColor: darken(interactiveColor, hoverDarkenCoefficient),
              },
            },
          },
        },
        MuiOutlinedInput: {
          styleOverrides: {
            root: {
              paddingLeft: baseTheme.spacing(0.5),
              paddingRight: baseTheme.spacing(0.5),
              "& input": {
                color: backgroundContrastTextColor,
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
                  border: `${accentedBorder} !important`,
                  borderColor: `${darken(
                    accentedBackground,
                    hoverDarkenCoefficient
                  )} !important`,
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
                  paddingLeft: baseTheme.spacing(1.5),
                  paddingRight: baseTheme.spacing(1.5),
                  borderRight: accentedBorder,
                  marginRight: 0,
                },
                [`&.${inputAdornmentClasses.positionEnd}`]: {
                  paddingRight: baseTheme.spacing(1.5),
                  paddingLeft: baseTheme.spacing(1.5),
                  borderLeft: accentedBorder,
                  marginLeft: 0,
                },
                [`& .${typographyClasses.root}`]: {
                  textTransform: "uppercase",
                  fontWeight: 700,
                  fontSize: "0.8125rem",
                  lineHeight: "20px",
                },
              },
            },
            notchedOutline: {
              borderColor: accentedBackground,
              borderRadius: 3,
              borderWidth: "2px",
              transition: "all ease 0.3s",
            },
          },
        },
        MuiDataGrid: {
          defaultProps: {
            getRowClassName: (params: {
              indexRelativeToCurrentPage: number,
              ...
            }) =>
              params.indexRelativeToCurrentPage % 2 === 0 ? "even" : "odd",
          },
          styleOverrides: {
            root: {
              border: "none",
              /*
               * typo.css is adding a margin to the bottom of all paragraphs,
               * breaking the layout of the DataGrid's pagination controls.
               */
              "& p": {
                marginBottom: 0,
              },
              [`& .${gridClasses.withBorderColor}`]: {
                borderColor: accentedBackground,
                borderWidth: "1px",
              },
              [`& .${gridClasses.columnSeparator}`]: {
                visibility: "visible",
                color: accentedBackground,
              },
              [`& .${gridClasses.menuIcon}`]: {
                width: "unset !important",
                visibility: "visible !important",
              },
              [`& .${gridClasses.columnHeader}`]: {
                color: backgroundContrastTextColor,
              },
              [`& .${gridClasses.row}`]: {
                "&.even": {
                  backgroundColor: `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%, 6%)`,
                },
                "&:hover": {
                  backgroundColor: `hsl(${accent.background.hue}deg, 40%, ${accent.background.lightness}%, 25%)`,
                },
                "&.Mui-selected": {
                  backgroundColor: `hsl(${accent.main.hue}deg, ${accent.main.saturation}%, ${accent.main.lightness}%, 20%)`,
                },
              },
              [`& .${gridClasses.toolbarContainer}`]: {
                paddingRight: 0,
                marginRight: "-2px",
                marginLeft: "-4px",
              },
              [`& .${buttonClasses.root}`]: {
                "&:hover": {
                  /*
                   * we have to replicate specifying the border width here
                   * because DataGrid doesn't just set the borderColor in its
                   * :hover style so if we don't also set set the whole border
                   * then our width gets unset
                   */
                  border: accentedBorder,
                  borderColor: darken(
                    accentedBackground,
                    hoverDarkenCoefficient
                  ),
                },
              },
              [`& .${buttonClasses.outlinedPrimary}`]: {
                color: linkButtonText,
                border: accentedBorder,
                "&:hover": {
                  borderColor: darken(
                    accentedBackground,
                    hoverDarkenCoefficient
                  ),
                },
              },
              [`& .${gridClasses.cellContent}, & .${gridClasses["cell--withRenderer"]}`]:
                {
                  color: backgroundContrastTextColor,
                },
            },
          },
        },
        MuiListItemButton: {
          styleOverrides: {
            root: {
              border: accentedBorder,
              borderRadius: 8,
              background: "#fff",
              boxShadow: `hsl(${accent.main.hue}deg, 100%, 20%, 20%) 0px 2px 8px 0px`,
              [`& .${svgIconClasses.root}`]: {
                color: linkColor,
                fontSize: "2.8em",
              },
              "&:focus-visible": {
                backgroundColor: darken(
                  secondaryBackground,
                  hoverDarkenCoefficient
                ),
              },
            },
          },
        },
        MuiTypography: {
          styleOverrides: {
            root: {
              letterSpacing: "0.02em",
              color: backgroundContrastTextColor,
              [`&.${alertTitleClasses.root}`]: {
                color: "inherit",
              },
            },
            h3: {
              fontWeight: 700,
              fontSize: "1.2rem",
              letterSpacing: "0.02em",
              opacity: "0.9",
              textTransform: "uppercase",
              borderBottom: accentedBorder,
            },
          },
        },
        MuiLink: {
          defaultProps: {
            target: "_blank",
            rel: "noreferrer",
          },
          styleOverrides: {
            root: {
              color: `${linkColor} !important`,
              fontWeight: 700,
              textDecorationColor: mainAccentColor,
              transition: "all .3s ease",
              textUnderlineOffset: "2px",
              textDecoration: "underline !important",
              "&:link": {
                textDecoration: "underline !important",
              },
            },
          },
        },
        MuiDivider: {
          styleOverrides: {
            root: {
              borderBottom: accentedBorder,
            },
            vertical: {
              borderBottom: "none",
              borderRight: accentedBorder,
            },
            withChildren: {
              borderBottom: "none",
              marginTop: baseTheme.spacing(-0.5),
              marginBottom: baseTheme.spacing(-0.5),
              "&:before, &:after": {
                borderTop: accentedBorder,
              },
              [`& .${dividerClasses.wrapper}`]: {
                fontWeight: 500,
                fontSize: "0.9em",
                color: backgroundContrastTextColor,
              },
            },
          },
        },
        MuiBreadcrumbs: {
          styleOverrides: {
            separator: {
              color: backgroundContrastTextColor,
            },
          },
        },
        MuiCheckbox: {
          styleOverrides: {
            root: {
              [`&.${checkboxClasses.checked}`]: {
                [`& .${svgIconClasses.root}`]: {
                  fill: interactiveColor,
                },
              },
            },
          },
        },
        MuiRadio: {
          styleOverrides: {
            root: {
              [`&.${radioClasses.checked}`]: {
                [`& .${svgIconClasses.root}`]: {
                  fill: interactiveColor,
                },
              },
            },
          },
        },
        MuiChip: {
          styleOverrides: {
            root: {
              padding: baseTheme.spacing(0.25, 0.5),
              fontWeight: 500,
              letterSpacing: "0.03em",
              color: backgroundContrastTextColor,
              [`&.${chipClasses.filled}`]: {
                backgroundColor: `hsl(${accent.background.hue}deg, ${accent.background.saturation}%, ${accent.background.lightness}%, 60%)`,
                [`& .${chipClasses.deleteIcon}`]: {
                  color: contrastTextColor,
                },
              },
              [`&.${chipClasses.filled}${chipClasses.clickable}`]: {
                backgroundColor: lighterInteractiveColor,
                color: linkColor,
                border: prefersMoreContrast ? accentedBorder : "none",
                "&:hover": {
                  backgroundColor: darken(
                    lighterInteractiveColor,
                    hoverDarkenCoefficient
                  ),
                },
              },
              [`&.${chipClasses.outlined}`]: {
                border: accentedBorder,
                [`& .${svgIconClasses.root}`]: {
                  color: linkColor,
                },
              },
              [`&.${chipClasses.deletable}`]: {
                padding: 0,
              },
            },
          },
        },
        MuiFormLabel: {
          styleOverrides: {
            root: {
              fontWeight: 700,
              fontSize: "1rem",
              letterSpacing: "0.02em",
              marginBottom: baseTheme.spacing(0.5),
              color: backgroundContrastTextColor,
              [`&.${formLabelClasses.focused}`]: {
                color: backgroundContrastTextColor,
              },
            },
          },
        },
        MuiAlert: {
          styleOverrides: {
            standardInfo: {
              [`& .${typographyClasses.root}`]: {
                color: "hsl(206.47deg 53.13% 25.1%) !important",
              },
            },
            standardSuccess: {
              [`& .${typographyClasses.root}`]: {
                color: "hsl(123deg 40% 19.61%) !important",
              },
            },
          },
        },
        MuiAvatar: {
          styleOverrides: {
            root: {
              backgroundColor: "rgb(255,255,255)",
              color: mainAccentColor,
            },
          },
        },
        MuiMenu: {
          styleOverrides: {
            paper: {
              boxShadow: "none",
              border: accentedBorder,
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
            content: {
              cursor: "default",
              backgroundColor: prefersMoreContrast
                ? "transparent"
                : darken(secondaryBackground, hoverDarkenCoefficient),
              border: prefersMoreContrast ? accentedBorder : "none",
              maxWidth: "fit-content",
              "&:hover": {
                backgroundColor: prefersMoreContrast
                  ? "transparent"
                  : darken(secondaryBackground, hoverDarkenCoefficient * 2),
              },
              "&.Mui-selected": {
                backgroundColor: accentedBackground,
                "&.Mui-focused": {
                  backgroundColor: prefersMoreContrast
                    ? accentedBackground
                    : darken(secondaryBackground, hoverDarkenCoefficient * 5),
                },
                "& .MuiTreeItem-label": {
                  color: contrastTextColor,
                },
                "& .MuiTreeItem-iconContainer": {
                  color: contrastTextColor,
                },
                "&:hover": {
                  backgroundColor: prefersMoreContrast
                    ? accentedBackground
                    : darken(secondaryBackground, hoverDarkenCoefficient * 6),
                },
              },
            },
            label: {
              fontWeight: 500,
              letterSpacing: "0.01em",
              color: backgroundContrastTextColor,
              marginRight: baseTheme.spacing(1),
            },
            iconContainer: {
              color: backgroundContrastTextColor,
            },
            groupTransition: {
              paddingLeft: "calc(2 * var(--TreeView-itemChildrenIndentation))",
            },
          },
        },
        MuiTableCell: {
          styleOverrides: {
            root: {
              color: backgroundContrastTextColor,
            },
          },
        },
        MuiTablePagination: {
          styleOverrides: {
            toolbar: {
              background: mainBackground,
              color: backgroundContrastTextColor,
              minHeight: "unset !important",
            },
          },
        },
      },
    })
  );
}
