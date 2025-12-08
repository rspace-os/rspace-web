import createCache from "@emotion/cache";
import { CacheProvider } from "@emotion/react";
import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import * as React from "react";
import { createRoot } from "react-dom/client";
import { color, currentPage } from "@/util/pageBranding";
import createAccentedTheme from "../accentedTheme";
import Analytics from "../components/Analytics";
import AppBar from "../components/AppBar";
import { DialogBoundary } from "../components/DialogBoundary";
import ErrorBoundary from "../components/ErrorBoundary";

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
                <Analytics>
                    <ErrorBoundary>
                        <CssBaseline />
                        <ThemeProvider theme={createAccentedTheme(color(currentPage()))}>
                            <div style={{ fontSize: "1rem", lineHeight: "1.5" }}>
                                {/*
                                 * We use a DialogBoundary to keep the menu inside the shadow DOM
                                 */}
                                <DialogBoundary>
                                    <AppBar variant="page" currentPage={currentPage()} accessibilityTips={{}} />
                                </DialogBoundary>
                            </div>
                            <div style={{ height: "30px" }}></div>
                        </ThemeProvider>
                    </ErrorBoundary>
                </Analytics>
            </CacheProvider>
        </React.StrictMode>,
    );

    const meta = document.createElement("meta");
    meta.name = "theme-color";
    meta.content = `hsl(${color(currentPage()).background.hue}, ${
        color(currentPage()).background.saturation
    }%, ${color(currentPage()).background.lightness}%)`;
    document.head?.appendChild(meta);
});
