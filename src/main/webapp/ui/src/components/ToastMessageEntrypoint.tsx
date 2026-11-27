import { ThemeProvider } from "@mui/material/styles";
import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "../modules/common/i18n/I18nRoot";
import materialTheme from "../theme";
import Snackbars from "./ToastMessage";
import { bufferPreMountToast } from "./toastMessageBuffer";

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
      <MuiCssLayerProvider>
        <ThemeProvider theme={materialTheme}>
          <Snackbars />
        </ThemeProvider>
      </MuiCssLayerProvider>
    </I18nRoot>,
  );
}
