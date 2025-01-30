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
import { DialogBoundary } from "../components/DialogBoundary";
import { COLOR as GALLERY_COLOR } from "./gallery/common";

const WORKSPACE_COLOR = {
  main: {
    hue: 120,
    saturation: 18,
    lightness: 71,
  },
  darker: {
    hue: 120,
    saturation: 18,
    lightness: 50,
  },
  contrastText: {
    hue: 120,
    saturation: 18,
    lightness: 20,
  },
  background: {
    hue: 120,
    saturation: 18,
    lightness: 81,
  },
  backgroundContrastText: {
    hue: 120,
    saturation: 4,
    lightness: 29,
  },
};

const OTHER_COLOR = {
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
    groups: "My RSpace",
    gallery: "Gallery",
  };
  const firstPathFragment = window.location.pathname.split("/")[1];
  if (firstPathFragment in pages) return pages[firstPathFragment];
  return "Unknown";
}

function color(page: string) {
  if (page === "Workspace") return WORKSPACE_COLOR;
  if (page === "Gallery") return GALLERY_COLOR;
  return OTHER_COLOR;
}

window.addEventListener("load", () => {
  /*
   * We append the app bar to the body to be outside of the wide margins on
   * many pages
   */
  const domContainer = document.createElement("div");
  document.body?.insertBefore(domContainer, document.body.firstChild);

  /*
   * We use a shadow DOM so that the MUI styles to not leak
   */
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
            <div style={{ fontSize: "1rem", lineHeight: "1.5" }}>
              {/*
               * We use a DialogBoundary to keep the menu inside the shadow DOM
               */}
              <DialogBoundary>
                <AppBar
                  variant="page"
                  currentPage={currentPage()}
                  accessibilityTips={{}}
                />
              </DialogBoundary>
            </div>
            <div style={{ height: "30px" }}></div>
          </ThemeProvider>
        </ErrorBoundary>
      </CacheProvider>
    </React.StrictMode>
  );

  const meta = document.createElement("meta");
  meta.name = "theme-color";
  meta.content = `hsl(${color(currentPage()).background.hue}, ${
    color(currentPage()).background.saturation
  }%, ${color(currentPage()).background.lightness}%)`;
  document.head?.appendChild(meta);
});
