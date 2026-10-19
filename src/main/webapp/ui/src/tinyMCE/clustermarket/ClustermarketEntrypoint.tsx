import { createRoot } from "react-dom/client";
import Analytics from "../../components/Analytics";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import Clustermarket from "./Clustermarket";

type ClustermarketEditor = NonNullable<typeof parent.tinymce.activeEditor> & {
  settings: { clustermarket_web_url: string };
};

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-clustermarket");
  if (!domContainer) {
    throw new Error("tinymce-clustermarket container not found");
  }
  const activeEditor = parent.tinymce.activeEditor;
  if (!activeEditor) {
    throw new Error("Active TinyMCE editor not found for Clustermarket");
  }
  const editor = activeEditor as ClustermarketEditor;
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <Analytics>
        <Clustermarket clustermarket_web_url={editor.settings.clustermarket_web_url} />
      </Analytics>
    </I18nRoot>,
  );
});
