/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { mergeThemes } from "../../styles";
import { ThemeOptions } from "@mui/material";

describe("mergeThemes", () => {
  test("Simple checks", () => {
    const theme1 = {
      components: {
        MuiButton: {
          defaultProps: {
            color: "primary",
          },
          styleOverrides: {
            root: {
              color: "red",
            },
          },
        },
        MuiLink: {
          styleOverrides: {
            root: {
              "&:hover": {
                color: "blue",
              },
            },
          },
        },
      },
    } as ThemeOptions;

    const theme2 = {
      components: {
        MuiLink: {
          styleOverrides: {
            color: "green",
            root: {
              "&:hover": {
                color: "orange",
              },
            },
          },
        },
      },
    } as ThemeOptions;

    expect(mergeThemes(theme1, theme2)).toEqual({
      components: {
        MuiButton: {
          defaultProps: {
            color: "primary",
          },
          styleOverrides: {
            root: {
              color: "red",
            },
          },
        },
        MuiLink: {
          styleOverrides: {
            root: {
              "&:hover": {
                color: "orange",
              },
            },
            color: "green",
          },
        },
      },
    });

    expect(mergeThemes(theme2, theme1)).toEqual({
      components: {
        MuiLink: {
          styleOverrides: {
            color: "green",
            root: {
              "&:hover": {
                color: "blue",
              },
            },
          },
        },
        MuiButton: {
          defaultProps: {
            color: "primary",
          },
          styleOverrides: {
            root: {
              color: "red",
            },
          },
        },
      },
    });
  });
});
