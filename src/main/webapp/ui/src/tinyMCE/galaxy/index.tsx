// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { createRoot } from "react-dom/client";
import Galaxy from "./Galaxy";

// biome-ignore lint/complexity/useArrowFunction: initial biome migration
document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-galaxy");
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
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
      />,
    );
  }
});
// biome-ignore lint/complexity/useArrowFunction: initial biome migration
parent.tinymce.activeEditor?.on("galaxy-used", function () {
  // biome-ignore lint/complexity/useOptionalChain: initial biome migration
  if (parent && parent.tinymce) {
    parent.dispatchEvent(new CustomEvent("galaxy-used", { detail: { fieldId: parent.tinymce.activeEditor?.id } }));
  }
});
