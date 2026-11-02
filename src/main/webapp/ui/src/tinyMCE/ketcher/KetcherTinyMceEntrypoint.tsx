import { ThemeProvider } from "@mui/material/styles";
import { createRoot, type Root } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import KetcherTinyMce from "@/tinyMCE/ketcher/KetcherTinyMce";
import Alerts from "../../components/Alerts/Alerts";
import Analytics from "../../components/Analytics";
import theme from "../../theme";

const KETCHER_DIALOG_CONTAINER_ID = "tinymce-ketcher";
const OPEN_KETCHER_DIALOG_EVENT = "OPEN_KETCHER_DIALOG";

const ketcherDialogRoots = new WeakMap<HTMLElement, Root>();

let ketcherDialogListenerRegistered = false;

function getKetcherMountContainer(): HTMLElement | null {
  const rootDocument = window.top?.document ?? document;
  const container = rootDocument.getElementById(KETCHER_DIALOG_CONTAINER_ID);

  return container instanceof HTMLElement ? container : null;
}

function renderKetcherDialog(): void {
  const wrapperDiv = getKetcherMountContainer();

  if (!wrapperDiv) {
    return;
  }

  const root = ketcherDialogRoots.get(wrapperDiv) ?? createRoot(wrapperDiv);
  ketcherDialogRoots.set(wrapperDiv, root);

  const unmount = () => {
    root.unmount();
    ketcherDialogRoots.delete(wrapperDiv);
  };

  root.render(
    <ThemeProvider theme={theme}>
      <Analytics>
        <Alerts>
          <I18nRoot namespaces={["common"]}>
            <KetcherTinyMce onUnmount={unmount} />
          </I18nRoot>
        </Alerts>
      </Analytics>
    </ThemeProvider>,
  );
}

function registerKetcherDialogListener(): void {
  if (ketcherDialogListenerRegistered) {
    return;
  }

  window.addEventListener(OPEN_KETCHER_DIALOG_EVENT, renderKetcherDialog);
  ketcherDialogListenerRegistered = true;
}

document.addEventListener("DOMContentLoaded", () => {
  registerKetcherDialogListener();
});

if (document.readyState !== "loading") {
  registerKetcherDialogListener();
}
