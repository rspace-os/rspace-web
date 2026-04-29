import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import CssBaseline from "@mui/material/CssBaseline";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/chemistry";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import { DialogBoundary } from "@/components/DialogBoundary";
import ConfirmProvider from "../../components/ConfirmProvider";
import StoichiometryDialog, {
  type StandaloneDialogInnerProps,
} from "./dialog/StoichiometryDialog";
import { createStoichiometryTheme } from "@/tinyMCE/stoichiometry/theme";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

const theme = createStoichiometryTheme(createAccentedTheme(ACCENT_COLOR));

const StoichiometryDialogEntrypoint = (props: StandaloneDialogInnerProps) => {
  return (
    <StyledEngineProvider injectFirst>
      <CssBaseline />
      <ThemeProvider theme={theme}>
        <Analytics>
          <ErrorBoundary>
            <Alerts>
              <QueryClientProvider client={queryClient}>
                <DialogBoundary>
                  <ConfirmProvider>
                    <StoichiometryDialog {...props} />
                  </ConfirmProvider>
                </DialogBoundary>
              </QueryClientProvider>
            </Alerts>
          </ErrorBoundary>
        </Analytics>
      </ThemeProvider>
    </StyledEngineProvider>
  );
};

export default StoichiometryDialogEntrypoint;
