import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/sysadmin";
import LoaderCircular from "../../../components/LoadingCircular";
import { UiPreferences } from "../../../hooks/api/useUiPreference";
import I18nRoot from "../../../modules/common/i18n/I18nRoot";
import { UsersPage } from "./index";

const queryClient = new QueryClient();
const wrapperDiv = document.getElementById("sysadminUsers");
if (wrapperDiv) {
  const root = createRoot(wrapperDiv);
  root.render(
    <I18nRoot namespaces={["system", "common"]} fallback={<LoaderCircular />}>
      <QueryClientProvider client={queryClient}>
        <StyledEngineProvider injectFirst enableCssLayer>
          <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <UiPreferences>
              <UsersPage />
            </UiPreferences>
          </ThemeProvider>
        </StyledEngineProvider>
      </QueryClientProvider>
    </I18nRoot>,
  );
}
