import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "./accentedTheme";
import { ACCENT_COLOR as INVENTORY_COLOR } from "./assets/branding/rspace/inventory";
import Analytics from "./components/Analytics";
import { ERROR_MSG } from "./components/ErrorBoundary";
import GoogleLoginProvider from "./components/GoogleLoginProvider";
import LoaderCircular from "./components/LoadingCircular";
import I18nRoot from "./modules/common/i18n/I18nRoot";
import Router from "./Router";
import useStores from "./stores/use-stores";

const queryClient = new QueryClient();

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
        await Promise.all([unitStore.fetchUnits(), authStore.fetchPidinstEnabled()]);
        setLoadingDone(true);
        if (currentUser) {
          await currentUser.getBench();
        }
      }
    })();
  }, []);

  return loadingDone ? (
    (authStore.isAuthenticated || authStore.isSigningOut) && peopleStore.currentUser ? (
      <>
        <GoogleLoginProvider />
        <StyledEngineProvider injectFirst enableCssLayer>
          <ThemeProvider theme={createAccentedTheme(INVENTORY_COLOR)}>
            <QueryClientProvider client={queryClient}>
              <Analytics>
                <Router />
              </Analytics>
            </QueryClientProvider>
          </ThemeProvider>
        </StyledEngineProvider>
      </>
    ) : (
      ERROR_MSG
    )
  ) : null;
}

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  window.scrollTo(0, 1);

  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
      <I18nRoot namespaces={["inventory", "common", "about"]} fallback={<LoaderCircular />}>
        <App />
      </I18nRoot>,
    );
  }

  if (window.location.pathname.startsWith("/inventory")) {
    const meta = document.createElement("meta");
    meta.name = "theme-color";
    meta.content = `hsl(${INVENTORY_COLOR.background.hue}, ${INVENTORY_COLOR.background.saturation}%, ${INVENTORY_COLOR.background.lightness}%)`;
    document.head?.appendChild(meta);
  }
});

export default observer(App);
