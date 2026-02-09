import { createRoot } from "react-dom/client";
import materialTheme from "@/theme";
import RaidConnections from "@/my-rspace/profile/RaidConnections/RaidConnections";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

declare global {
  interface Window {
    ___RaidConnectionsInitialised: boolean;
    __TANSTACK_QUERY_CLIENT__: import("@tanstack/query-core").QueryClient;
  }
}

if (!window.___RaidConnectionsInitialised) {
  const domContainer = document.getElementById("raid-connections");
  if (!domContainer) {
    throw new Error("RaidConnectionsEntrypoint: no domContainer");
  }

  const groupId = domContainer.getAttribute("data-group-id");

  if (!groupId) {
    throw new Error("RaidConnectionsEntrypoint: no groupId");
  }

  const queryClient = new QueryClient();

  const root = createRoot(domContainer);
  root.render(
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <QueryClientProvider client={queryClient}>
          <RaidConnections groupId={groupId} />
        </QueryClientProvider>
      </ThemeProvider>
    </StyledEngineProvider>,
  );

  window.___RaidConnectionsInitialised = true;
  // This code is for all users
  window.__TANSTACK_QUERY_CLIENT__ = queryClient;
}
