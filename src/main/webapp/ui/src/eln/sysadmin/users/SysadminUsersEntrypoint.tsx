import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
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
        <MuiCssLayerProvider>
          <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <UiPreferences>
              <UsersPage />
            </UiPreferences>
          </ThemeProvider>
        </MuiCssLayerProvider>
      </QueryClientProvider>
    </I18nRoot>,
  );
}
