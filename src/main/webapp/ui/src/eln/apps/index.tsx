import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../components/ErrorBoundary";
import App from "./App";
import Alerts from "../../components/Alerts/Alerts";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme, { COLORS } from "../../theme";
import Analytics from "../../components/Analytics";
import CssBaseline from "@mui/material/CssBaseline";
import { hslToHex } from "../../util/colors";

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <StyledEngineProvider injectFirst>
          <ThemeProvider
            theme={{
              ...materialTheme,
              components: {
                ...materialTheme.components,
                MuiLink: {
                  styleOverrides: {
                    root: {
                      color: hslToHex(
                        COLORS.primary.hue,
                        COLORS.primary.saturation,
                        33
                      ),
                      fontWeight: 700,
                      textUnderlineOffset: "2px",
                      textDecorationColor: hslToHex(
                        COLORS.primary.hue,
                        COLORS.primary.saturation,
                        33,
                        0.4
                      ),
                    },
                  },
                },
                MuiTab: {
                  styleOverrides: {
                    root: {
                      "&.Mui-selected": {
                        color: hslToHex(
                          COLORS.primary.hue,
                          COLORS.primary.saturation,
                          33
                        ),
                      },
                    },
                  },
                },
                MuiTabs: {
                  styleOverrides: {
                    indicator: {
                      backgroundColor: hslToHex(
                        COLORS.primary.hue,
                        COLORS.primary.saturation,
                        33
                      ),
                    },
                  },
                },
              },
            }}
          >
            <CssBaseline />
            <Analytics>
              <ErrorBoundary>
                <Alerts>
                  <App />
                </Alerts>
              </ErrorBoundary>
            </Analytics>
          </ThemeProvider>
        </StyledEngineProvider>
      </React.StrictMode>
    );

    const meta = document.createElement("meta");
    meta.name = "theme-color";
    meta.content = `hsl(200, 10%, 81%)`;
    document.head?.appendChild(meta);
  }
});
