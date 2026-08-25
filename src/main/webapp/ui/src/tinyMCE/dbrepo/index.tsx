import { createRoot } from "react-dom/client";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import DBRepo from "./DBRepo";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-dbrepo");
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
      <I18nRoot namespaces={["workspace", "common"]}>
        <DBRepo />
      </I18nRoot>,
    );
  }
});
