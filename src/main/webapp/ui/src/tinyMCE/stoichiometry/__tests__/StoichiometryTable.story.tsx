import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import i18n from "@/modules/common/i18n";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";
import { StoichiometryTableControllerProvider } from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import { createStoichiometryTheme } from "@/tinyMCE/stoichiometry/theme";
import { useEditableStoichiometryTable } from "@/tinyMCE/stoichiometry/useEditableStoichiometryTable";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/chemistry";
import Alerts from "../../../components/Alerts/Alerts";

const stoichiometryLoadingLabel = i18n.t("common:stoichiometry.dialog.loadingTable");

function TestProviders({ children }: { children: React.ReactNode }) {
  const [queryClient] = React.useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: false },
          mutations: { retry: false },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
const StoichiometryTableWithQueryData = () => {
  const { hasChanges, tableController } = useEditableStoichiometryTable({
    stoichiometryId: 1,
    stoichiometryRevision: 1,
  });

  return (
    <StoichiometryTableControllerProvider value={tableController}>
      <StoichiometryTable stoichiometryId={1} stoichiometryRevision={1} editable hasChanges={hasChanges} />
    </StoichiometryTableControllerProvider>
  );
};

export const StoichiometryTableWithDataStory = () => {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createStoichiometryTheme(createAccentedTheme(ACCENT_COLOR))}>
        <TestProviders>
          <Alerts>
            <React.Suspense
              fallback={
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "center",
                    alignItems: "center",
                    minHeight: 100,
                    my: 2,
                    gap: 1,
                  }}
                >
                  <CircularProgress size={24} aria-label={stoichiometryLoadingLabel} />
                  <Typography variant="body2" color="textSecondary">
                    {stoichiometryLoadingLabel}
                  </Typography>
                </Box>
              }
            >
              <StoichiometryTableWithQueryData />
            </React.Suspense>
          </Alerts>
        </TestProviders>
      </ThemeProvider>
    </StyledEngineProvider>
  );
};

export function ReadOnlyStoichiometryTableStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createStoichiometryTheme(createAccentedTheme(ACCENT_COLOR))}>
        <TestProviders>
          <Alerts>
            <React.Suspense
              fallback={
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "center",
                    alignItems: "center",
                    minHeight: 100,
                    my: 2,
                    gap: 1,
                  }}
                >
                  <CircularProgress size={24} aria-label={stoichiometryLoadingLabel} />
                  <Typography variant="body2" color="textSecondary">
                    {stoichiometryLoadingLabel}
                  </Typography>
                </Box>
              }
            >
              <StoichiometryTable stoichiometryId={1} stoichiometryRevision={1} />
            </React.Suspense>
          </Alerts>
        </TestProviders>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
