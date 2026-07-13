import { CacheProvider } from "@emotion/react";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import * as React from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import { createMuiCssLayerCache } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR as OTHER_COLOR } from "../../assets/branding/rspace/other";
import Analytics from "../../components/Analytics";
import { AboutRSpaceContent } from "../../components/AppBar/AboutRSpaceDialog";
import { DialogBoundary } from "../../components/DialogBoundary";
import ErrorBoundary from "../../components/ErrorBoundary";

const queryClient = new QueryClient();

function AboutPage(): React.ReactElement {
  const { t } = useTranslation("about");
  return (
    <>
      <Typography variant="h4" component="h1" align="center" gutterBottom>
        {t("title")}
      </Typography>
      <AboutRSpaceContent />
    </>
  );
}

window.addEventListener("load", () => {
  const domContainer = document.getElementById("about-page");
  if (!domContainer) {
    console.error("Could not find element with id 'about-page'");
    return;
  }

  // Already mounted (the load handler can run more than once in dev). Bail to
  // avoid re-attaching a shadow root (and a duplicate React root).
  if (domContainer.shadowRoot) {
    return;
  }

  /*
   * We use a shadow DOM so that the MUI styles do not leak
   */
  const shadow = domContainer.attachShadow({ mode: "open" });
  const wrapper = document.createElement("div");
  shadow.appendChild(wrapper);

  const cache = createMuiCssLayerCache({
    key: "css",
    prepend: true,
    container: shadow,
  });

  const root = createRoot(wrapper);
  root.render(
    <React.StrictMode>
      <CacheProvider value={cache}>
        <QueryClientProvider client={queryClient}>
          <Analytics>
            <ErrorBoundary>
              <CssBaseline />
              <ThemeProvider theme={createAccentedTheme(OTHER_COLOR)}>
                <I18nRoot namespaces={["about"]}>
                  <Box sx={{ fontSize: "1rem", lineHeight: "1.5" }}>
                    <DialogBoundary>
                      <Container maxWidth="sm">
                        <Box sx={{ py: 4 }}>
                          <AboutPage />
                        </Box>
                      </Container>
                    </DialogBoundary>
                  </Box>
                </I18nRoot>
              </ThemeProvider>
            </ErrorBoundary>
          </Analytics>
        </QueryClientProvider>
      </CacheProvider>
    </React.StrictMode>,
  );
});
