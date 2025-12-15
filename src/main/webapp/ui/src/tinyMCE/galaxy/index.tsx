import React from "react";
import Galaxy from "./Galaxy";
import { createRoot } from "react-dom/client";
document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-galaxy");
  if(domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <Galaxy
        fieldId = {
          parent.tinymce.activeEditor?.id.substring(4)
        }
        recordId = {
          // @ts-expect-error
          parent.tinymce.activeEditor?.settings?.recordId
        }
        attachedFileInfo={
          // @ts-expect-error
          parent.tinymce.activeEditor?.attachedFileRecordIdsToHtml
        }
    />
  );
  }
});
parent.tinymce.activeEditor?.on("galaxy-used", function () {
  if (parent && parent.tinymce) {
    parent.dispatchEvent(new CustomEvent("galaxy-used", {detail: {fieldId:parent.tinymce.activeEditor?.id}}));
  }
});
