import { createRoot } from "react-dom/client";
import Analytics from "../../components/Analytics";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import { getActiveEditorSetting } from "../activeEditorSetting";
import Clustermarket from "./Clustermarket";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-clustermarket");
  if (!domContainer) {
    throw new Error("tinymce-clustermarket container not found");
  }
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <Analytics>
        <Clustermarket clustermarket_web_url={getActiveEditorSetting("clustermarket_web_url")} />
      </Analytics>
    </I18nRoot>,
  );
});
