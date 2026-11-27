import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ACCENT_COLOR } from "@/assets/branding/chemistry";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import ErrorBoundary from "@/components/ErrorBoundary";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import { createStoichiometryTheme } from "@/tinyMCE/stoichiometry/theme";
import createAccentedTheme from "../../accentedTheme";
import ConfirmProvider from "../../components/ConfirmProvider";
import StoichiometryDialog, { type StandaloneDialogInnerProps } from "./dialog/StoichiometryDialog";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

const theme = createStoichiometryTheme(createAccentedTheme(ACCENT_COLOR));

const StoichiometryDialogRoot = (props: StandaloneDialogInnerProps) => {
  return (
    <MuiCssLayerProvider>
      <CssBaseline />
      <ThemeProvider theme={theme}>
        <Analytics>
          <ErrorBoundary>
            <Alerts>
              <QueryClientProvider client={queryClient}>
                <ConfirmProvider>
                  <StoichiometryDialog {...props} />
                </ConfirmProvider>
              </QueryClientProvider>
            </Alerts>
          </ErrorBoundary>
        </Analytics>
      </ThemeProvider>
    </MuiCssLayerProvider>
  );
};

export default StoichiometryDialogRoot;
