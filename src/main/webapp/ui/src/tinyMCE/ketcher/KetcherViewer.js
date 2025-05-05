// @flow

import React, { useEffect, useState, type Node } from "react";

import KetcherDialog from "../../components/Ketcher/KetcherDialog";
import { createRoot } from "react-dom/client";
import axios from "@/common/axios";
import useOauthToken from "../../common/useOauthToken";
import Analytics from "../../components/Analytics";

type ChemicalElement = {|
  chemId: number,
  chemFileId: number,
  chemElements: string,
  format: string,
|};

/**
 * This component retrieves the selected chemical element in the global tinymce
 * field, fetches chemical data from `/chemical/file/contents`, and
 * displays it in a dialog. Ketcher is in this case read-only, with most of the
 * editing buttons disabled. Note that it relies on jQuery and tinymce being
 * available as global variables.
 */
export const KetcherViewer = (): Node => {
  const [existingChemical, setExistingChemical] = useState("");
  const [dialogIsOpen, setDialogIsOpen] = useState(true);
  const { getToken } = useOauthToken();

  useEffect(() => {
    // $FlowExpectedError[cannot-resolve-name]
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
          // $FlowExpectedError[cannot-resolve-name]
          tinymceDialogUtils.showErrorAlert(
            "Problem loading chemical element."
          );
          return;
        }
        setExistingChemical(response.data);
      } catch (error) {
        // $FlowExpectedError[cannot-resolve-name]
        tinymceDialogUtils.showErrorAlert("Loading chemical elements failed.");
      }
    };

    // $FlowExpectedError[cannot-resolve-name]
    if ($(selectedNode).hasClass("chem")) {
      loadChemicalFile().catch(() => {
        // $FlowExpectedError[cannot-resolve-name]
        tinymceDialogUtils.showErrorAlert("Loading chemical elements failed.");
      });
    }
  }, []);

  const handleClose = () => {
    setDialogIsOpen(false);
    setExistingChemical("");
    // $FlowExpectedError[cannot-resolve-name]
    const editor = tinymce.activeEditor;
    editor.selection.select(editor.getBody());
    editor.selection.collapse(true);
  };

  // $FlowExpectedError[cannot-resolve-name]
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
