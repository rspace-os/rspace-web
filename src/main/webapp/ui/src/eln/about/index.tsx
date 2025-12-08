import createCache from "@emotion/cache";
import { CacheProvider } from "@emotion/react";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import * as React from "react";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR as OTHER_COLOR } from "../../assets/branding/rspace/other";
import Analytics from "../../components/Analytics";
import { AboutRSpaceContent } from "../../components/AppBar/AboutRSpaceDialog";
import { DialogBoundary } from "../../components/DialogBoundary";
import ErrorBoundary from "../../components/ErrorBoundary";

window.addEventListener("load", () => {
    const domContainer = document.getElementById("about-page");
    if (!domContainer) {
        console.error("Could not find element with id 'about-page'");
        return;
    }

    /*
     * We use a shadow DOM so that the MUI styles do not leak
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
                        <ThemeProvider theme={createAccentedTheme(OTHER_COLOR)}>
                            <div style={{ fontSize: "1rem", lineHeight: "1.5" }}>
                                <DialogBoundary>
                                    <Container maxWidth="sm">
                                        <Box py={4}>
                                            <Typography variant="h4" component="h1" align="center" gutterBottom>
                                                About RSpace
                                            </Typography>
                                            <AboutRSpaceContent />
                                        </Box>
                                    </Container>
                                </DialogBoundary>
                            </div>
                        </ThemeProvider>
                    </ErrorBoundary>
                </Analytics>
            </CacheProvider>
        </React.StrictMode>,
    );
});
