import { Suspense } from "react";
import { UsersPage } from "./index";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

export function UsersPageWithProviders() {
  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <UsersPage />
          </ThemeProvider>
        </StyledEngineProvider>
      </Suspense>
    </QueryClientProvider>
  );
}
