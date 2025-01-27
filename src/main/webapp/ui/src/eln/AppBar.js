//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../components/ErrorBoundary";
import AppBar from "../components/AppBar";
import { ThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import createAccentedTheme from "../accentedTheme";
import createCache from "@emotion/cache";
import { CacheProvider } from "@emotion/react";

const COLOR = {
  main: {
    hue: 200,
    saturation: 10,
    lightness: 70,
  },
  darker: {
    hue: 200,
    saturation: 10,
    lightness: 50,
  },
  contrastText: {
    hue: 200,
    saturation: 10,
    lightness: 20,
  },
  background: {
    hue: 200,
    saturation: 10,
    lightness: 81,
  },
  backgroundContrastText: {
    hue: 200,
    saturation: 4,
    lightness: 29,
  },
};

function currentPage() {
  const pages = {
    workspace: "Workspace",
    dashboard: "Other",
    system: "System",
    community: "System",
    record: "My RSpace",
    userform: "My RSpace",
    directory: "My RSpace",
    audit: "My RSpace",
    import: "My RSpace",
  };
  const firstPathFragment = window.location.pathname.split("/")[1];
  if (firstPathFragment in pages) return pages[firstPathFragment];
  return "Unknown";
}

function color(_page: string) {
  return COLOR;
}

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app-bar");
  if (domContainer) {
    window.scrollTo(0, 1);

    const shadow = domContainer.attachShadow({ mode: "open" });
    const wrapper = document.createElement("div");
    shadow.appendChild(wrapper);

    const cache = createCache({
      key: "css",
      prepend: true,
      container: shadow,
    });

    const root = createRoot(wrapper);
    root.render(
      <React.StrictMode>
        <CacheProvider value={cache}>
          <ErrorBoundary>
            <CssBaseline />
            <ThemeProvider theme={createAccentedTheme(color(currentPage()))}>
              <div style={{ position: "fixed", left: 0, right: 0 }}>
                <AppBar
                  variant="page"
                  currentPage={currentPage()}
                  accessibilityTips={{}}
                />
              </div>
              <div style={{ height: "30px" }}></div>
            </ThemeProvider>
          </ErrorBoundary>
        </CacheProvider>
      </React.StrictMode>
    );
  }
});
