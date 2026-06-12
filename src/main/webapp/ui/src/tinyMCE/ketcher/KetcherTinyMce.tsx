import Backdrop from "@mui/material/Backdrop";
import CircularProgress from "@mui/material/CircularProgress";
import { ThemeProvider } from "@mui/material/styles";
import type { Ketcher } from "ketcher-core";
import React, { useEffect, useRef, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import axios from "@/common/axios";
import { IsInvalid, IsValid, type ValidationResult } from "@/components/ValidatingSubmitButton";
import { blobToBase64 } from "@/util/files";
import Alerts from "../../components/Alerts/Alerts";
import Analytics from "../../components/Analytics";
import useChemicalImport, { type RspaceCompoundId } from "../../hooks/api/useChemicalImport";
import AnalyticsContext from "../../stores/contexts/Analytics";
import theme from "../../theme";

type TinyMceEditor = {
  id: string;
  selection: {
    getNode: () => Node | null;
  };
  execCommand: (command: string, ui: boolean, value?: string) => void;
  windowManager: {
    close: () => void;
  };
};

type TinyMceDialogUtils = {
  showErrorAlert: (message: string) => void;
};

type MustacheRenderer = {
  render: <T extends object>(template: string, data: T) => string;
};

const CHEM_CLASS_NAME = "chem";
const KETCHER_DIALOG_CONTAINER_ID = "tinymce-ketcher";
const OPEN_KETCHER_DIALOG_EVENT = "OPEN_KETCHER_DIALOG";
const EMPTY_MOLECULE_MESSAGE = "Please draw, paste, or open a molecule to insert into the document";

const ketcherDialogRoots = new WeakMap<HTMLElement, Root>();

let ketcherDialogListenerRegistered = false;

function getTinyMceDialogUtils(): TinyMceDialogUtils | undefined {
  return (globalThis as { tinymceDialogUtils?: TinyMceDialogUtils }).tinymceDialogUtils;
}

function getMustacheRenderer(): MustacheRenderer | undefined {
  return (globalThis as { Mustache?: MustacheRenderer }).Mustache;
}

function getActiveEditor(): TinyMceEditor | null {
  return window.tinymce?.activeEditor ?? null;
}

function getSelectedNode(editor: TinyMceEditor | null): Node | null {
  return editor?.selection.getNode() ?? null;
}

function isElementNode(node: EventTarget | Node | null): node is Element {
  return typeof node === "object" && node !== null && "nodeType" in node && node.nodeType === Node.ELEMENT_NODE;
}

function isChemicalElement(node: Node | null): node is Element {
  return isElementNode(node) && node.classList.contains(CHEM_CLASS_NAME);
}

function getSelectedChemicalElement(editor: TinyMceEditor | null): Element | null {
  const selectedNode = getSelectedNode(editor);
  return isChemicalElement(selectedNode) ? selectedNode : null;
}

function getFieldId(editor: TinyMceEditor | null): string | null {
  const fieldId = editor?.id.replace(/^\D+/g, "") ?? "";

  return fieldId === "" ? null : fieldId;
}

function containsMolecule(ketData: string): boolean {
  try {
    const parsedKet: unknown = JSON.parse(ketData);

    if (typeof parsedKet !== "object" || parsedKet === null) {
      return false;
    }

    return Object.keys(parsedKet).some((key) => key.startsWith("mol"));
  } catch {
    return false;
  }
}

const KetcherDialog = React.lazy(() => import("../../components/Ketcher/KetcherDialog"));

function showErrorAlert(message: string): void {
  getTinyMceDialogUtils()?.showErrorAlert(message);
}

function renderTemplate<T extends object>(template: string, data: T): string {
  return getMustacheRenderer()?.render(template, data) ?? "";
}

function getKetcherMountContainer(): HTMLElement | null {
  const rootDocument = window.top?.document ?? document;
  const container = rootDocument.getElementById(KETCHER_DIALOG_CONTAINER_ID);

  return container instanceof HTMLElement ? container : null;
}

function renderKetcherDialog(): void {
  const wrapperDiv = getKetcherMountContainer();

  if (!wrapperDiv) {
    return;
  }

  const root = ketcherDialogRoots.get(wrapperDiv) ?? createRoot(wrapperDiv);
  ketcherDialogRoots.set(wrapperDiv, root);

  const unmount = () => {
    root.unmount();
    ketcherDialogRoots.delete(wrapperDiv);
  };

  root.render(
    <ThemeProvider theme={theme}>
      <Analytics>
        <Alerts>
          <KetcherTinyMce onUnmount={unmount} />
        </Alerts>
      </Analytics>
    </ThemeProvider>,
  );
}

function registerKetcherDialogListener(): void {
  if (ketcherDialogListenerRegistered) {
    return;
  }

  window.addEventListener(OPEN_KETCHER_DIALOG_EVENT, renderKetcherDialog);
  ketcherDialogListenerRegistered = true;
}

export const KetcherTinyMce = ({ onUnmount }: { onUnmount: () => void }): React.ReactNode => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [existingChemical, setExistingChemical] = useState("");
  const [isValid, setIsValid] = useState<ValidationResult>(IsValid());
  const ketcherRef = useRef<Ketcher | null>(null);
  const { save } = useChemicalImport();
  const activeEditor = getActiveEditor();
  const selectedChemicalElement = getSelectedChemicalElement(activeEditor);

  useEffect(() => {
    let isCurrent = true;
    const editor = getActiveEditor();
    const chemicalElement = getSelectedChemicalElement(editor);

    if (!chemicalElement) {
      return undefined;
    }

    const chemElemId = chemicalElement.getAttribute("id");

    if (!chemElemId) {
      return undefined;
    }

    const revision = chemicalElement.getAttribute("data-rsrevision");

    void (async () => {
      try {
        const response = await axios.get<{ data: { chemElements: string } } | null>("/chemical/ajax/loadChemElements", {
          params: {
            chemId: chemElemId,
            ...(revision === null ? {} : { revision }),
          },
        });

        if (isCurrent && response.data !== null) {
          setExistingChemical(response.data.data.chemElements);
        }
      } catch {
        if (isCurrent) {
          showErrorAlert("Loading chemical elements failed.");
        }
      }
    })();

    return () => {
      isCurrent = false;
    };
  }, []);

  const saveFullSizeImage = async (chemical: string, newChemElemId: RspaceCompoundId): Promise<void> => {
    const ketcher = ketcherRef.current;

    if (!ketcher) {
      return;
    }

    try {
      const imageBlob = await ketcher.generateImage(chemical);
      const imgString = await blobToBase64(imageBlob);
      await axios.post("/chemical/ajax/saveChemImage", null, {
        params: {
          chemId: newChemElemId,
          imageBase64: imgString,
        },
      });
    } catch {
      console.warn("Saving chemical preview failed.");
    }
  };

  const insertChemicalIntoDoc = async (newChemElemId: RspaceCompoundId): Promise<void> => {
    const editor = getActiveEditor();

    if (!editor) {
      return;
    }

    const fullWidth = 500;
    const fullHeight = 500;
    const previewWidth = 250;
    const previewHeight = 250;
    const fieldId = getFieldId(editor);

    if (!fieldId) {
      return;
    }

    const templateData = {
      id: newChemElemId,
      ecatChemFileId: null,
      sourceParentId: fieldId,
      width: previewWidth,
      height: previewHeight,
      fullwidth: fullWidth,
      fullheight: fullHeight,
      fieldId,
      tstamp: Date.now(),
    };
    const url = "/fieldTemplates/ajax/chemElementLink";

    const { data: template } = await axios.get<string>(url);
    const html = renderTemplate(template, templateData);

    if (html !== "") {
      editor.execCommand("mceInsertContent", false, html);
      const event = new CustomEvent<RspaceCompoundId>("tinymce-chem-inserted", {
        detail: templateData.id,
      });
      document.dispatchEvent(event);
      trackEvent("user:add:chemistry_object:document", { from: "ketcher" });
    }

    editor.windowManager.close();
  };

  const saveChemicalAndInsert = async (chemical: string): Promise<void> => {
    const fieldId = getFieldId(getActiveEditor());

    if (!fieldId) {
      return;
    }

    const data = {
      chemElements: chemical,
      chemElementsFormat: "ket" as const,
      fieldId,
    };

    const { id: newChemElemId } = await save(data);
    void saveFullSizeImage(chemical, newChemElemId);
    await insertChemicalIntoDoc(newChemElemId);
  };

  const handleInsert = (ketcher: Ketcher): void => {
    ketcherRef.current = ketcher;

    void (async () => {
      try {
        const chemical = await ketcher.getKet();
        await saveChemicalAndInsert(chemical);
        await window.ketcher.setMolecule("");
        onUnmount();
      } catch {
        // Ignore failed insert attempts; save() already reports API errors.
      }
    })();
  };

  const handleClose = (): void => {
    onUnmount();
  };

  const validate = (ketcher: Ketcher | null | undefined): void => {
    if (!ketcher) {
      setIsValid(IsValid());
      return;
    }

    void (async () => {
      try {
        const ketData = await ketcher.getKet();

        if (!containsMolecule(ketData)) {
          setIsValid(IsInvalid(EMPTY_MOLECULE_MESSAGE));
          return;
        }

        setIsValid(IsValid());
      } catch {
        setIsValid(IsInvalid(EMPTY_MOLECULE_MESSAGE));
      }
    })();
  };

  if (!activeEditor) {
    return null;
  }

  return selectedChemicalElement && existingChemical === "" ? null : (
    <React.Suspense
      fallback={
        <Backdrop
          open
          sx={{
            color: "#fff",
            zIndex: 2, // higher than the TinyMCE form field editor
          }}
        >
          <CircularProgress color="inherit" />
        </Backdrop>
      }
    >
      <KetcherDialog
        isOpen={true}
        handleInsert={handleInsert}
        title={"Ketcher Insert Chemical"}
        existingChem={existingChemical}
        handleClose={handleClose}
        actionBtnText={"Insert"}
        validationResult={isValid}
        onChange={() => {
          validate(window.ketcher);
        }}
      />
    </React.Suspense>
  );
};

document.addEventListener("DOMContentLoaded", () => {
  registerKetcherDialogListener();
});

if (document.readyState !== "loading") {
  registerKetcherDialogListener();
}

export default KetcherTinyMce;
