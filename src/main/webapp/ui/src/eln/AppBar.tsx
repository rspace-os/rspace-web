import * as React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../components/ErrorBoundary";
import AppBar from "../components/AppBar";
import { ThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import createAccentedTheme from "../accentedTheme";
import createCache from "@emotion/cache";
import { CacheProvider } from "@emotion/react";
import { DialogBoundary } from "../components/DialogBoundary";
import { ACCENT_COLOR as GALLERY_COLOR } from "../assets/branding/rspace/gallery";
import { ACCENT_COLOR as WORKSPACE_COLOR } from "../assets/branding/rspace/workspace";
import { ACCENT_COLOR as OTHER_COLOR } from "../assets/branding/rspace/other";

function currentPage(): string {
  const pages: Record<string, string> = {
    workspace: "Workspace",
    notebookEditor: "Workspace",
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
    oldGallery: "Gallery",
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
  domContainer.setAttribute("id", "app-bar");
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
