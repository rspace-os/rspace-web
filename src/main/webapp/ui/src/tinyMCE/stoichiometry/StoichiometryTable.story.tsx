import React from "react";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/chemistry";
import Alerts from "../../components/Alerts/Alerts";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { useGetStoichiometryQuery } from "@/modules/stoichiometry/queries";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";
import { createStoichiometryTheme } from "@/tinyMCE/stoichiometry/theme";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

function StoichiometryTableWithQueryData(): React.ReactNode {
  const { getToken } = useOauthToken();
  const { data, isFetching } = useGetStoichiometryQuery({
    stoichiometryId: 1,
    revision: 1,
    getToken,
  });

  return (
    <StoichiometryTable
      stoichiometryId={data.id}
      stoichiometryRevision={data.revision}
      isFetching={isFetching}
      onUpdateStoichiometry={async () => data}
      onDeleteStoichiometry={async () => true}
      onGetMoleculeInfo={async () => ({
        formula: "",
        molecularWeight: 0,
      })}
      editable
    />
  );
}

export function StoichiometryTableWithDataStory(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider
        theme={createStoichiometryTheme(createAccentedTheme(ACCENT_COLOR))}
      >
        <QueryClientProvider client={queryClient}>
          <Alerts>
            <React.Suspense
              fallback={
                <Box
                  display="flex"
                  flexDirection="column"
                  justifyContent="center"
                  alignItems="center"
                  minHeight={100}
                  my={2}
                  gap={1}
                >
                  <CircularProgress
                    size={24}
                    aria-label="Loading stoichiometry table"
                  />
                  <Typography variant="body2" color="textSecondary">
                    Loading stoichiometry table...
                  </Typography>
                </Box>
              }
            >
              <StoichiometryTableWithQueryData />
            </React.Suspense>
          </Alerts>
        </QueryClientProvider>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
