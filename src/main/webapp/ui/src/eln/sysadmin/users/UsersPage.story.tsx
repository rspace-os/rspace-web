import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Suspense } from "react";
import materialTheme from "../../../theme";
import { UsersPage } from "./index";

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
