/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import { mergeThemes } from "../../styles";

describe("mergeThemes", () => {
  test("Simple checks", () => {
    const theme1 = {
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
    };

    const theme2 = {
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
    };

    expect(mergeThemes(theme1, theme2)).toEqual({
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
    });

    expect(mergeThemes(theme2, theme1)).toEqual({
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
    });
  });
});
