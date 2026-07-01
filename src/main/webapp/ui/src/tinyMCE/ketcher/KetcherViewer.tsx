import Backdrop from "@mui/material/Backdrop";
import CircularProgress from "@mui/material/CircularProgress";
import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import Analytics from "../../components/Analytics";

const KetcherDialog = React.lazy(() => import("../../components/Ketcher/KetcherDialog"));

type TinyMceEditor = {
  selection: {
    getNode: () => Node | null;
    select: (node: Node) => void;
    collapse: (toStart: boolean) => void;
  };
  getBody: () => HTMLElement;
};

type TinyMceDialogUtils = {
  showErrorAlert: (message: string) => void;
};

const CHEM_CLASS_NAME = "chem";

function getActiveEditor(): TinyMceEditor | null {
  return (globalThis as { tinymce?: { activeEditor?: TinyMceEditor } }).tinymce?.activeEditor ?? null;
}

function getSelectedNode(editor: TinyMceEditor | null): Node | null {
  return editor?.selection.getNode() ?? null;
}

function isElementNode(node: EventTarget | Node | null): node is Element {
  return (
    typeof node === "object" &&
    node !== null &&
    "nodeType" in node &&
    node.nodeType === Node.ELEMENT_NODE &&
    "classList" in node
  );
}

function isChemicalElement(node: Node | null): node is Element {
  return isElementNode(node) && node.classList.contains(CHEM_CLASS_NAME);
}

function getSelectedChemicalElement(editor: TinyMceEditor | null): Element | null {
  const selectedNode = getSelectedNode(editor);

  return isChemicalElement(selectedNode) ? selectedNode : null;
}

function showErrorAlert(message: string): void {
  (globalThis as { tinymceDialogUtils?: TinyMceDialogUtils }).tinymceDialogUtils?.showErrorAlert(message);
}

/**
 * This component retrieves the selected chemical element in the global tinymce
 * field, fetches chemical data from `/chemical/file/contents`, and
 * displays it in a dialog. Ketcher is in this case read-only, with most of the
 * editing buttons disabled. Note that it relies on tinymce being available as
 * a global variable.
 */
export const KetcherViewer = (): React.ReactNode => {
  const { t } = useTranslation("common");
  const [existingChemical, setExistingChemical] = useState("");
  const [dialogIsOpen, setDialogIsOpen] = useState(true);

  useEffect(() => {
    const editor = getActiveEditor();
    const selectedChemicalElement = getSelectedChemicalElement(editor);

    const loadChemicalFile = async (chemicalElement: Element) => {
      const chemElemId = chemicalElement.getAttribute("id");

      if (!chemElemId) {
        return;
      }

      const revision = chemicalElement.getAttribute("data-rsrevision");

      try {
        const response = await axios.get<string>("/chemical/file/contents", {
          params: new URLSearchParams({
            chemId: chemElemId,
            ...(revision === null ? {} : { revision }),
          }),
        });

        if (!response.data) {
          showErrorAlert(t("apiErrors.chemicals.loadElementFailed"));
          return;
        }
        setExistingChemical(response.data);
      } catch {
        showErrorAlert(t("apiErrors.chemicals.loadElementsFailed"));
      }
    };

    if (selectedChemicalElement) {
      loadChemicalFile(selectedChemicalElement).catch(() => {
        showErrorAlert(t("apiErrors.chemicals.loadElementsFailed"));
      });
    }
  }, []);

  const handleClose = () => {
    setDialogIsOpen(false);
    setExistingChemical("");
    const editor = getActiveEditor();

    if (!editor) {
      return;
    }

    editor.selection.select(editor.getBody());
    editor.selection.collapse(true);
  };

  const selectedChemicalElement = getSelectedChemicalElement(getActiveEditor());

  return selectedChemicalElement && existingChemical === "" ? null : (
    <React.Suspense
      fallback={
        <Backdrop
          open
          sx={{
            color: "#fff",
            zIndex: 1301, // more than the menu bar that opens the ketcher viewer
          }}
        >
          <CircularProgress color="inherit" />
        </Backdrop>
      }
    >
      <KetcherDialog
        isOpen={dialogIsOpen}
        handleInsert={() => {}}
        title={t("ketcher.viewerTitle")}
        existingChem={existingChemical}
        handleClose={handleClose}
        readOnly={true}
      />
    </React.Suspense>
  );
};

document.addEventListener("DOMContentLoaded", () => {
  window.addEventListener("OPEN_KETCHER_VIEWER", () => {
    const rootDocument = window.top?.document ?? document;
    const wrapperDiv = rootDocument.getElementById("tinymce-ketcher");

    if (wrapperDiv instanceof HTMLElement) {
      const root = createRoot(wrapperDiv);
      root.render(
        <Analytics>
          <MuiCssLayerProvider>
            <I18nRoot namespaces={["common"]}>
              <KetcherViewer />
            </I18nRoot>
          </MuiCssLayerProvider>
        </Analytics>,
      );
    }
  });
});

export default KetcherViewer;
