import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { createRoot } from "react-dom/client";
import Alerts from "../../components/Alerts/Alerts";
import Analytics from "../../components/Analytics";
import ErrorBoundary from "../../components/ErrorBoundary";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import materialTheme, { COLORS } from "../../theme";
import { hslToHex } from "../../util/colors";
import App from "./App";

const queryClient = new QueryClient();

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <StyledEngineProvider injectFirst enableCssLayer>
          <ThemeProvider
            theme={{
              ...materialTheme,
              components: {
                ...materialTheme.components,
                MuiLink: {
                  styleOverrides: {
                    root: {
                      color: hslToHex(COLORS.primary.hue, COLORS.primary.saturation, 33),
                      fontWeight: 700,
                      textUnderlineOffset: "2px",
                      textDecorationColor: hslToHex(COLORS.primary.hue, COLORS.primary.saturation, 33, 0.4),
                    },
                  },
                },
                MuiTab: {
                  styleOverrides: {
                    root: {
                      "&.Mui-selected": {
                        color: hslToHex(COLORS.primary.hue, COLORS.primary.saturation, 33),
                      },
                    },
                  },
                },
                MuiTabs: {
                  styleOverrides: {
                    indicator: {
                      backgroundColor: hslToHex(COLORS.primary.hue, COLORS.primary.saturation, 33),
                    },
                  },
                },
              },
            }}
          >
            <CssBaseline />
            <QueryClientProvider client={queryClient}>
              <Analytics>
                <ErrorBoundary>
                  <I18nRoot namespaces={["apps", "common"]} fullPage>
                    <Alerts>
                      <App />
                    </Alerts>
                  </I18nRoot>
                </ErrorBoundary>
              </Analytics>
            </QueryClientProvider>
          </ThemeProvider>
        </StyledEngineProvider>
      </React.StrictMode>,
    );

    const meta = document.createElement("meta");
    meta.name = "theme-color";
    meta.content = `hsl(200, 10%, 81%)`;
    document.head?.appendChild(meta);
  }
});
