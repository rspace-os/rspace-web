import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { observer } from "mobx-react-lite";
import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import Router from "./Router";
import useStores from "./stores/use-stores";
import materialTheme from "./theme";
import { ERROR_MSG } from "./components/ErrorBoundary";
import Analytics from "./components/Analytics";
import { ACCENT_COLOR as INVENTORY_COLOR } from "./assets/branding/rspace/inventory";
import GoogleLoginProvider from "./components/GoogleLoginProvider";

function App(): React.ReactNode {
  const { authStore, peopleStore, unitStore } = useStores();
  const [loadingDone, setLoadingDone] = useState(false);

  useEffect(() => {
    void (async () => {
      await authStore.synchronizeWithSessionStorage();
      if (!authStore.isAuthenticated) {
        setLoadingDone(true);
      } else {
        const currentUser = await peopleStore.fetchCurrentUser();
        await unitStore.fetchUnits();
        setLoadingDone(true);
        if (currentUser) {
          await currentUser.getBench();
        }
      }
    })();
  }, []);

  return loadingDone ? (
    <>
      {(authStore.isAuthenticated || authStore.isSigningOut) &&
      peopleStore.currentUser ? (
        <>
          <GoogleLoginProvider />
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
              <Analytics>
                <Router />
              </Analytics>
            </ThemeProvider>
          </StyledEngineProvider>
        </>
      ) : (
        ERROR_MSG
      )}
    </>
  ) : null;
}

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  window.scrollTo(0, 1);

  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(<App />);
  }

  if (window.location.pathname.startsWith("/inventory")) {
    const meta = document.createElement("meta");
    meta.name = "theme-color";
    meta.content = `hsl(${INVENTORY_COLOR.background.hue}, ${INVENTORY_COLOR.background.saturation}%, ${INVENTORY_COLOR.background.lightness}%)`;
    document.head?.appendChild(meta);
  }
});

export default observer(App);
