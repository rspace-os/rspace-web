import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { createRoot } from "react-dom/client";
import I18nRoot from "../modules/common/i18n/I18nRoot";
import materialTheme from "../theme";
import Snackbars from "./ToastMessage";
import { bufferPreMountToast } from "./toastMessageBuffer";

/*
 * Start buffering toasts straight away: Snackbars only listens once mounted,
 * and drains this buffer on mount.
 */
document.addEventListener("show-toast-message", bufferPreMountToast);

// render the snackbar wrapper component
const domContainer = document.getElementById("toast-message");
if (domContainer) {
  const root = createRoot(domContainer);
  /*
   * The providers wrap Snackbars rather than living inside it because as of
   * MUI v5 useStyles cannot be used in the same component as the root
   * MuiThemeProvider
   */
  root.render(
    <I18nRoot namespaces={["common"]}>
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <Snackbars />
        </ThemeProvider>
      </StyledEngineProvider>
    </I18nRoot>,
  );
}
