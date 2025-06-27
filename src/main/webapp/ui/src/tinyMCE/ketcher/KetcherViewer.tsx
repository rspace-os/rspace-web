import React, { useEffect, useState } from "react";

import KetcherDialog from "../../components/Ketcher/KetcherDialog";
import { createRoot } from "react-dom/client";
import axios from "@/common/axios";
import Analytics from "../../components/Analytics";

/**
 * This component retrieves the selected chemical element in the global tinymce
 * field, fetches chemical data from `/chemical/file/contents`, and
 * displays it in a dialog. Ketcher is in this case read-only, with most of the
 * editing buttons disabled. Note that it relies on jQuery and tinymce being
 * available as global variables.
 */
export const KetcherViewer = (): React.ReactNode => {
  const [existingChemical, setExistingChemical] = useState("");
  const [dialogIsOpen, setDialogIsOpen] = useState(true);

  useEffect(() => {
    // @ts-expect-error TS cannot find this global
    const editor = tinymce.activeEditor;
    const selectedNode = editor.selection.getNode();
    const loadChemicalFile = async () => {
      const chemElemId = selectedNode.getAttribute("id");
      const revision = selectedNode.getAttribute("data-rsrevision");

      try {
        const response = await axios.get<string>("/chemical/file/contents", {
          params: new URLSearchParams({
            chemId: chemElemId,
            ...(revision === null ? {} : { revision }),
          }),
        });

        if (!response.data) {
          // @ts-expect-error global
          tinymceDialogUtils.showErrorAlert(
            "Problem loading chemical element."
          );
          return;
        }
        setExistingChemical(response.data);
      } catch {
        // @ts-expect-error global
        tinymceDialogUtils.showErrorAlert("Loading chemical elements failed.");
      }
    };

    // @ts-expect-error global
    if ($(selectedNode).hasClass("chem")) {
      loadChemicalFile().catch(() => {
        // @ts-expect-error global
        tinymceDialogUtils.showErrorAlert("Loading chemical elements failed.");
      });
    }
  }, []);

  const handleClose = () => {
    setDialogIsOpen(false);
    setExistingChemical("");
    // @ts-expect-error global
    const editor = tinymce.activeEditor;
    editor.selection.select(editor.getBody());
    editor.selection.collapse(true);
  };

  // @ts-expect-error global
  return $(tinymce.activeEditor.selection.getNode()).hasClass("chem") &&
    existingChemical === "" ? null : (
    <KetcherDialog
      isOpen={dialogIsOpen}
      handleInsert={() => {}}
      title={"Ketcher Chemical Viewer (Read-Only)"}
      existingChem={existingChemical}
      handleClose={handleClose}
      readOnly={true}
    />
  );
};

document.addEventListener("DOMContentLoaded", () => {
  window.addEventListener("OPEN_KETCHER_VIEWER", () => {
    // @ts-expect-error top wont be null
    const wrapperDiv = top.document.getElementById("tinymce-ketcher");
    if (wrapperDiv) {
      const root = createRoot(wrapperDiv);
      root.render(
        <Analytics>
          <KetcherViewer />
        </Analytics>
      );
    }
  });
});

export default KetcherViewer;
