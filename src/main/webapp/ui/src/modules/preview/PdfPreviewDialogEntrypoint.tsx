import React from "react";
import { createRoot } from "react-dom/client";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { PdfPreviewDialogWrapper } from "@/modules/preview/PdfPreviewDialog";

window.addEventListener("load", () => {
  const domContainer = document.createElement("div");
  domContainer.setAttribute("id", "pdf-preview-dialog-root");
  document.body.appendChild(domContainer);

  const root = createRoot(domContainer);
  root.render(
    <React.StrictMode>
      <I18nRoot namespaces={["gallery", "common"]}>
        <PdfPreviewDialogWrapper />
      </I18nRoot>
    </React.StrictMode>,
  );
});
