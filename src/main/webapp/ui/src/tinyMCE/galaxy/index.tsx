import { createRoot } from "react-dom/client";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import Galaxy from "./Galaxy";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-galaxy");
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
      <I18nRoot namespaces={["apps", "common"]}>
        <Galaxy
          fieldId={parent.tinymce.activeEditor?.id.substring(4)}
          recordId={
            // @ts-expect-error
            parent.tinymce.activeEditor?.settings?.recordId
          }
          attachedFileInfo={
            // @ts-expect-error
            parent.tinymce.activeEditor?.attachedFileRecordIdsToHtml
          }
        />
      </I18nRoot>,
    );
  }
});
parent.tinymce.activeEditor?.on("galaxy-used", () => {
  if (parent?.tinymce) {
    parent.dispatchEvent(new CustomEvent("galaxy-used", { detail: { fieldId: parent.tinymce.activeEditor?.id } }));
  }
});
