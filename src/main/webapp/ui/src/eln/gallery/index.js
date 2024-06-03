//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import GalleryPicker from "./picker";
import ErrorBoundary from "../../components/ErrorBoundary";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { COLOR } from "./theme";

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <ErrorBoundary>
          <ThemeProvider theme={createAccentedTheme(COLOR)}>
            <GalleryPicker open={true} onClose={() => {}} />
          </ThemeProvider>
        </ErrorBoundary>
      </React.StrictMode>
    );
  }
});
