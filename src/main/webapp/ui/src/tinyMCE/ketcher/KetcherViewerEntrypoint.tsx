import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import KetcherViewer from "@/tinyMCE/ketcher/KetcherViewer";
import Analytics from "../../components/Analytics";

document.addEventListener("DOMContentLoaded", () => {
  window.addEventListener("OPEN_KETCHER_VIEWER", () => {
    const rootDocument = window.top?.document ?? document;
    const wrapperDiv = rootDocument.getElementById("tinymce-ketcher");

    if (wrapperDiv instanceof HTMLElement) {
      const root = createRoot(wrapperDiv);
      root.render(
        <Analytics>
          <MuiCssLayerProvider>
            <I18nRoot namespaces={["common"]}>
              <KetcherViewer />
            </I18nRoot>
          </MuiCssLayerProvider>
        </Analytics>,
      );
    }
  });
});
