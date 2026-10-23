import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import Shortcuts from "@/tinyMCE/shortcutsPlugin/Shortcuts";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-shortcuts");
  const root = createRoot(domContainer as HTMLElement);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <MuiCssLayerProvider>
        <Shortcuts />
      </MuiCssLayerProvider>
    </I18nRoot>,
  );
});
