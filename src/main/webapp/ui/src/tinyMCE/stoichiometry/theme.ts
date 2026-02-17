import { alpha, createTheme, lighten } from "@mui/material/styles";
import { type ThemeOptions } from "@mui/material";
import type {} from "@mui/x-data-grid/themeAugmentation";
import { mergeThemes } from "@/util/styles";

export const STOICHIOMETRY_TABLE_CLASS = "stoichiometry-table";

export function createStoichiometryTheme(baseTheme: unknown) {
  const theme = baseTheme as {
    palette: {
      background: { default: string };
      divider: string;
      action: { hover: string };
      primary: { background: string; contrastText: string };
    };
  };
  const tableBackground = theme.palette.background.default;

  return createTheme(
    mergeThemes(baseTheme as ThemeOptions, {
      palette: {
        DataGrid: {
          bg: tableBackground,
          headerBg: tableBackground,
          pinnedBg: tableBackground,
        },
      } as unknown as ThemeOptions["palette"],
      components: {
        MuiDataGrid: {
          styleOverrides: {
            root: {
              [`&.${STOICHIOMETRY_TABLE_CLASS}`]: {
                border: "none",
                backgroundColor: tableBackground,
                "& .MuiDataGrid-main": {
                  backgroundColor: tableBackground,
                },
                "& .MuiDataGrid-virtualScroller": {
                  backgroundColor: tableBackground,
                },
                "& .MuiDataGrid-columnHeaders": {
                  backgroundColor: tableBackground,
                  borderBottom: `2px solid ${theme.palette.divider}`,
                },
                "& .MuiDataGrid-cell": {
                  borderBottom: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
                },
                "& .MuiDataGrid-footerContainer": {
                  backgroundColor: tableBackground,
                },
                "& .MuiDataGrid-row:hover": {
                  backgroundColor: theme.palette.action.hover,
                },
                "& .stoichiometry-disabled-cell": {
                  backgroundColor: `${lighten(theme.palette.primary.background, 0.3)} !important`,
                  color: `${theme.palette.primary.contrastText} !important`,
                  fontStyle: "italic",
                },
              },
            },
            paper: {},
            menu: {},
          },
        },
      },
    }) as ThemeOptions,
  );
}
