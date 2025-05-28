import { createTheme, ThemeOptions as MuiThemeOptions } from "@mui/material";
import { makeStyles } from "tss-react/mui";
import { grey, red } from "@mui/material/colors";
import { hslToHex } from "./util/colors";

/**
 * The RecordPalette type is used to define the colours used for each Inventory
 * record type.
 */
export type RecordPalette = {
  bg: string;
  fg: string;
  lighter?: string;
};

declare module "@mui/material/styles" {
  // eslint-disable-next-line no-unused-vars
  interface Theme {
    borders: {
      table?: string;
      descriptionList?: string;
      floatingActions?: string;
      menu?: string;
      section?: string;
      menuButton?: string;
      card?: string;
      themedDialog?: (hue: number, sat: number, lig: number) => string;
      themedDialogTitle?: (hue: number, sat: number, lig: number) => string;
    };
    transitions: {
      iconTransformations: string;
      filterToggle: string;
    };
  }

  // eslint-disable-next-line no-unused-vars
  interface SimplePaletteColorOptions {
    saturated?: string;
    placeholderText?: string;
    background?: string;
  }

  interface TransitionsOptions {
    iconTransformations: string;
    filterToggle: string;
  }
}

declare module "@mui/material/styles/createTheme" {
  // eslint-disable-next-line no-unused-vars
  interface ThemeOptions {
    borders?: {
      table: string;
      descriptionList: string;
      floatingActions: string;
      menu: string;
      section: string;
      menuButton: string;
      card: string;
      themedDialog: (hue: number, sat: number, lig: number) => string;
      themedDialogTitle: (hue: number, sat: number, lig: number) => string;
    };
  }
}

declare module "@mui/material/styles/createPalette" {
  // eslint-disable-next-line no-unused-vars
  interface PaletteColor {
    background: string;
    saturated?: string;
    placeholderText?: string;
  }
  interface Palette {
    callToAction: PaletteColor;
    standardIcon: PaletteColor;
    lightestGrey: string;
    warningRed: string;
    deletedGrey: string;
    modifiedHighlight: string;
    record: {
      container: RecordPalette;
      sample: RecordPalette;
      subSample: RecordPalette;
      sampleTemplate: RecordPalette;
      document: RecordPalette;
      mixed: RecordPalette;
      attachment: Pick<RecordPalette, "fg">;
      gallery: Pick<RecordPalette, "fg">;
    };
    hover: {
      tableRow: string;
      iconButton: string;
    };
  }
  interface PaletteOptions {
    callToAction: PaletteColorOptions;
    tertiary: PaletteColorOptions;
    hover: {
      tableRow: string;
      iconButton: string;
    };
    borders?: {
      card?: string;
    };
    record: {
      container: RecordPalette;
      sample: RecordPalette;
      subSample: RecordPalette;
      sampleTemplate: RecordPalette;
      document: RecordPalette;
      mixed: RecordPalette;
      attachment: Pick<RecordPalette, "fg">;
      gallery: Pick<RecordPalette, "fg">;
    };
    standardIcon: PaletteColorOptions;
    sidebar: {
      selected: {
        bg: string;
        badge: string;
      };
    };
    lightestGrey: string;
    menuIconGrey: string;
    faIconGrey: string;
    modifiedHighlight: string;
    warningRed: string;
    deletedGrey: string;
  }
  interface TypeBackground {
    alt?: string;
  }
}

/*
 * Various components in MUI have a `color` prop which can be used to change
 * the colour of the component. This prop is typed as a string, and the
 * available colours are defined in the default theme. However, it is often
 * useful to be able to use custom colours that extend the default theme.  In
 * the theme we define some additional colours, such as `callToAction` and as
 * these are available anywhere within the scope of our theme, they can be used
 * in the `color` prop of components such as Buttons. However, the TypeScript
 * type of the `color` prop does not allow for these custom colours so we need
 * to extend the interfaces that define the colours that these components
 * accept.
 */
declare module "@mui/material/Button" {
  // eslint-disable-next-line no-unused-vars
  interface ButtonPropsColorOverrides {
    standardIcon: true;
    callToAction: true;
  }
}
declare module "@mui/material/IconButton" {
  // eslint-disable-next-line no-unused-vars
  interface IconButtonPropsColorOverrides {
    standardIcon: true;
  }
}
declare module "@mui/material/Badge" {
  interface BadgePropsColorOverrides {
    callToAction: true;
  }
}

declare module "@mui/material/styles/createTypography" {
  interface Typography {
    letterSpacing: {
      spaced: string;
      dense: string;
    };
  }

  interface TypographyOptions {
    letterSpacing?: {
      spaced: string;
      dense: string;
    };
  }
}

/**
 * These colours are defined in a way that can be adjusted where they are
 * needed; made darker or reduced contrast depending upon the situation.
 */
export const COLORS = {
  primary: {
    hue: 196.57,
    saturation: 100,
    lightness: 46.86,
  },
};

const baseTheme = createTheme({
  palette: {
    primary: {
      main: hslToHex(
        COLORS.primary.hue,
        COLORS.primary.saturation,
        COLORS.primary.lightness
      ),
      contrastText: "#fff",
      saturated: "#009ad6",
      placeholderText: "#8babcb",
      background: "#00adef22",
      dark: "rgba(0, 121, 167, 1)",
      light: "rgba(51, 189, 242, 1)",
    },
    callToAction: {
      main: hslToHex(
        COLORS.primary.hue,
        COLORS.primary.saturation,
        COLORS.primary.lightness
      ),
      contrastText: "#fff",
      saturated: "#009ad6",
      placeholderText: "#8babcb",
      background: "#00adef22",
      dark: "rgba(0, 121, 167, 1)",
      light: "rgba(51, 189, 242, 1)",
    },
    secondary: {
      main: "#f50057",
      light: "#fc6da0",
      contrastText: "#fff",
    },
    tertiary: {
      main: "#fb8c00",
      light: "#ffcc80",
      contrastText: "#000",
    },
    background: {
      default: "#fafafa",
      alt: "#fff",
    },
    error: {
      main: "#d32f2f",
    },
    warning: {
      main: "#f57c00",
    },
    info: {
      main: "#2196f3",
      contrastText: "#0d3c61",
    },
    hover: {
      tableRow: "rgba(0,0,0,0.04)",
      iconButton: "rgba(0,0,0,0.04)",
    },
    record: {
      container: {
        fg: "#2196f3",
        bg: "#4175AA",
        lighter: "#e6edf4",
      },
      sample: {
        fg: "#4caf50",
        bg: "#509554",
        lighter: "#e9f2e9",
      },
      subSample: {
        fg: "#21b07c",
        bg: "#256F5C",
        lighter: "#e9f1ef",
      },
      sampleTemplate: {
        fg: "#651ef5",
        bg: "#6B5C8A",
        lighter: "#ecebf0",
      },
      document: {
        bg: "#af5076",
        fg: "#d16691",
      },
      mixed: {
        bg: "#77858d",
        fg: "#fafafa",
        lighter: "#ecedef",
      },
      attachment: {
        fg: "#e64a19",
      },
      gallery: {
        fg: "#a768c6",
      },
    },
    standardIcon: {
      main: "rgba(0,0,0,0.54)",
    },
    sidebar: {
      selected: {
        bg: "#e6e6e6",
        badge: "#f7f7f7",
      },
    },

    // the lightest grey on a white background that has AA contrast
    lightestGrey: "#949494",
    menuIconGrey: "#616161",
    faIconGrey: "#8d8d8d",
    modifiedHighlight: "teal",
    warningRed: red[500],
    deletedGrey: grey[700],
  },
  breakpoints: {
    values: {
      xs: 0,
      sm: 600,
      md: 770,
      lg: 1150,
      xl: 1920,
    },
  },
  borders: {
    table: "1px solid rgba(224, 224, 224, 1)",
    descriptionList: "1px solid rgba(224, 224, 224, 1)",
    floatingActions: "1px solid rgba(224, 224, 224, 1)",
    menu: "1px solid #d3d4d5",
    section: "2px solid rgb(235, 235, 235)",
    menuButton: "1px solid rgba(192 ,192, 192, 1)",
    card: "1px solid rgba(0, 0, 0, 0.12)",
    themedDialog: (hue: number, sat: number, lig: number) =>
      `3px solid hsl(${hue} ${sat}% ${lig}% / 25%)`,
    themedDialogTitle: (hue: number, sat: number, lig: number) =>
      `1px solid hsl(${hue} ${sat}% ${lig}% / 20%)`,
  },
  transitions: {
    iconTransformations: "transform 0.3s cubic-bezier(0.42, 0, 0.94, 1.49) 0s", // just a little bounce
    filterToggle: "filter .2s ease-in-out, opacity .2s ease-in-out",
  },
  typography: {
    h6: {
      fontSize: "1.1rem",
    },
    letterSpacing: {
      spaced: "0.03em",
      dense: "0.01071em",
    },
  },
});

/**
 * The standard theme used across the application.
 */
export default createTheme({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ...(baseTheme as any as MuiThemeOptions),
  components: {
    MuiFormLabel: {
      styleOverrides: {
        root: {
          cursor: "text !important",
          color: "rgb(10,10,10)",
          marginBottom: baseTheme.spacing(0.25),
          fontWeight: 600,
          fontSize: "0.95em",
          "&.MuiInputLabel-outlined": {
            fontWeight: "initial",
            fontSize: "1.1em",
          },
          "&.Mui-focused": {
            color: "rgb(10,10,10)",
          },
        },
        asterisk: {
          color: baseTheme.palette.error.main,
        },
      },
    },
    MuiButtonBase: {
      styleOverrides: {
        root: {
          cursor: "default",
          "&:focus-visible": {
            outline: `2px solid ${baseTheme.palette.primary.main}`,
          },
        },
      },
    },
    MuiButton: {
      defaultProps: {
        color: "standardIcon",
      },
    },
    MuiSelect: {
      styleOverrides: {
        root: {
          /*
           * For some unknown reason, MuiSelects that follow HTMLLabelElements
           * in the DOM have a margin-top when no other input component does,
           * thereby mis-aligning MuiSelects when rendered next to other such
           * components.
           */
          marginTop: "0 !important",
        },
        select: {
          cursor: "default",
        },
      },
    },
    MuiFormControlLabel: {
      styleOverrides: {
        root: {
          cursor: "text !important",
        },
      },
    },
    MuiTableSortLabel: {
      styleOverrides: {
        root: {
          cursor: "default",
          "&:focus-visible": {
            outline: `2px solid ${baseTheme.palette.primary.main}`,
            borderRadius: baseTheme.spacing(0.5),
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        deleteIcon: {
          cursor: "default",
        },
        clickable: {
          cursor: "default",
        },
      },
    },
    MuiTypography: {
      styleOverrides: {
        h1: {
          fontSize: "2rem",
          fontWeight: 500,
          color: "#5482b7",
          textAlign: "center",
        },
        subtitle1: {
          fontSize: "1.05rem",
          color: "#5482b7",
          letterSpacing: "0.02em",
        },
        body1: {
          letterSpacing: "0.03em",
        },
      },
    },
    MuiTablePagination: {
      styleOverrides: {
        displayedRows: {
          margin: 0,
        },
      },
    },
  },
} as MuiThemeOptions);

/**
 * Some global styles used in various places
 * @deprecated
 */
export const globalStyles = makeStyles()(() => ({
  greyOut: {
    filter: "grayscale(1)",
    pointerEvents: "none !important" as "none",
    opacity: 0.6,
  },
}));
