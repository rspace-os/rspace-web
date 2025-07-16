import React from "react";
import Galaxy from "./Galaxy";
import { createRoot } from "react-dom/client";
document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-galaxy");
  const root = createRoot(domContainer);
  root.render(
    <Galaxy
        fieldId={
          parent.tinymce.activeEditor.id.substring(4)
        }
        recordId = {
          parent.tinymce.activeEditor.settings.recordId
        }
        attachedFileInfo={
          parent.tinymce.activeEditor.attachedFileRecordIdsToHtml
        }
      galaxy_web_url={
        parent.tinymce.activeEditor.settings.galaxy_web_url
      }
    />
  );
});
parent.tinymce.activeEditor.on("galaxy-used", function () {
  if (parent && parent.tinymce) {
    const ed = parent.tinymce.activeEditor;
    parent.dispatchEvent(new CustomEvent("galaxy-used", {detail: {fieldId:parent.tinymce.activeEditor.id}}));//TODO - add the datasets and history that were uploaded to Galaxy?
  }
});
