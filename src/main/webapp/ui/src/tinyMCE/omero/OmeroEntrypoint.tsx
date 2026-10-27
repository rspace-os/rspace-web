import { createRoot } from "react-dom/client";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import { getActiveEditorSetting } from "../activeEditorSetting";
import Omero from "./Omero";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-omero");
  if (!domContainer) {
    throw new Error("tinymce-omero container not found");
  }
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <Omero omero_web_url={getActiveEditorSetting("omero_web_url")} />
    </I18nRoot>,
  );
});
