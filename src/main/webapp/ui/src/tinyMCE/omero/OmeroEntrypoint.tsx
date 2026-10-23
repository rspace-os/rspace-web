import { createRoot } from "react-dom/client";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import Omero from "./Omero";

type OmeroEditor = NonNullable<typeof parent.tinymce.activeEditor> & {
  settings: { omero_web_url: string };
};

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-omero");
  if (!domContainer) {
    throw new Error("tinymce-omero container not found");
  }
  const activeEditor = parent.tinymce.activeEditor;
  if (!activeEditor) {
    throw new Error("Active TinyMCE editor not found for OMERO");
  }
  const editor = activeEditor as OmeroEditor;
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["workspace", "common"]}>
      <Omero omero_web_url={editor.settings.omero_web_url} />
    </I18nRoot>,
  );
});
