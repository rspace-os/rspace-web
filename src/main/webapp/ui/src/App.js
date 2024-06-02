// @flow

import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { observer } from "mobx-react-lite";
import React, {
  useEffect,
  type Node,
  useState,
  type ComponentType,
} from "react";
import { createRoot } from "react-dom/client";
import Router from "./Router";
import useStores from "./stores/use-stores";
import materialTheme from "./theme";
import { ERROR_MSG } from "./components/ErrorBoundary";
import Analytics from "./components/Analytics";

function App(): Node {
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
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <Analytics>
              <Router />
            </Analytics>
          </ThemeProvider>
        </StyledEngineProvider>
      ) : (
        ERROR_MSG
      )}
    </>
  ) : null;
}

window.addEventListener("load", function () {
  const domContainer = document.getElementById("app");
  window.scrollTo(0, 1);

  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(<App />);
  }
});

export default (observer(App): ComponentType<{||}>);
