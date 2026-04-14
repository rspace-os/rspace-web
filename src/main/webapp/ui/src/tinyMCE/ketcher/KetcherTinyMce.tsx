import React, { useEffect, useRef, useState } from "react";

import { createRoot } from "react-dom/client";
import { blobToBase64 } from "@/util/files";
import axios from "@/common/axios";
import AnalyticsContext from "../../stores/contexts/Analytics";
import Analytics from "../../components/Analytics";
import { ThemeProvider } from "@mui/material/styles";
import theme from "../../theme";
import useChemicalImport, {
  type RspaceCompoundId,
} from "../../hooks/api/useChemicalImport";
import Alerts from "../../components/Alerts/Alerts";
import {
  IsInvalid,
  IsValid,
  type ValidationResult,
} from "@/components/ValidatingSubmitButton";
import CircularProgress from "@mui/material/CircularProgress";
import Backdrop from "@mui/material/Backdrop";
import type { Ketcher } from "ketcher-core";

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

declare const tinymceDialogUtils: {
  showErrorAlert: (message: string) => void;
};

declare const Mustache: {
  render: <T extends object>(template: string, data: T) => string;
};

const CHEM_CLASS_NAME = "chem";
const EMPTY_MOLECULE_MESSAGE =
  "Please draw, paste, or open a molecule to insert into the document";

function getActiveEditor(): TinyMceEditor | null {
  return (window.tinymce?.activeEditor as TinyMceEditor | undefined) ?? null;
}

function getSelectedNode(editor: TinyMceEditor | null): Node | null {
  return editor?.selection.getNode() ?? null;
}

function isChemicalElement(node: Node | null): node is Element {
  return node instanceof Element && node.classList.contains(CHEM_CLASS_NAME);
}

function getSelectedChemicalElement(editor: TinyMceEditor | null): Element | null {
  const selectedNode = getSelectedNode(editor);
  return isChemicalElement(selectedNode) ? selectedNode : null;
}

function getFieldId(editor: TinyMceEditor | null): string | null {
  return editor?.id.replace(/^\D+/g, "") ?? null;
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

const KetcherDialog = React.lazy(
  () => import("../../components/Ketcher/KetcherDialog"),
);

export const KetcherTinyMce = (): React.ReactNode => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [existingChemical, setExistingChemical] = useState("");
  const [dialogIsOpen, setDialogIsOpen] = useState(true);
  const [isValid, setIsValid] = useState<ValidationResult>(IsValid());
  const ketcherRef = useRef<Ketcher | null>(null);
  const { save } = useChemicalImport();
  const activeEditor = getActiveEditor();
  const selectedChemicalElement = getSelectedChemicalElement(activeEditor);

  useEffect(() => {
    const editor = getActiveEditor();
    const chemicalElement = getSelectedChemicalElement(editor);

    if (!chemicalElement) {
      return;
    }

    const chemElemId = chemicalElement.getAttribute("id");

    if (!chemElemId) {
      return;
    }

    const revision = chemicalElement.getAttribute("data-rsrevision");

    void axios
      .get<{ data: { chemElements: string } } | null>(
        "/chemical/ajax/loadChemElements",
        {
        params: {
          chemId: chemElemId,
          ...(revision === null ? {} : { revision }),
        },
        },
      )
      .then((response) => {
        if (response.data !== null) {
          setExistingChemical(response.data.data.chemElements);
        }
      })
      .catch(() => {
        tinymceDialogUtils.showErrorAlert(
          "Loading chemical elements failed.",
        );
      });
  }, []);

  const saveFullSizeImage = async (
    chemical: string,
    newChemElemId: RspaceCompoundId,
  ): Promise<void> => {
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

  const insertChemicalIntoDoc = async (
    newChemElemId: RspaceCompoundId,
  ): Promise<void> => {
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
    const html = Mustache.render(template, templateData);

    if (html !== "") {
      editor.execCommand("mceInsertContent", false, html);
      const event = new CustomEvent<RspaceCompoundId>(
        "tinymce-chem-inserted",
        {
          detail: templateData.id,
        },
      );
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
        setDialogIsOpen(false);
        setExistingChemical("");
        void window.ketcher.setMolecule("");
      } catch {
        // Ignore failed insert attempts; save() already reports API errors.
      }
    })();
  };

  const handleClose = (): void => {
    setDialogIsOpen(false);
    setExistingChemical("");
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
        isOpen={dialogIsOpen}
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
  window.addEventListener("OPEN_KETCHER_DIALOG", () => {
    // todo: check if the root container already exists before createRoot()
    const wrapperDiv = window.top?.document.getElementById("tinymce-ketcher");

    if (wrapperDiv) {
      const root = createRoot(wrapperDiv);
      root.render(
        <ThemeProvider theme={theme}>
          <Analytics>
            <Alerts>
              <KetcherTinyMce />
            </Alerts>
          </Analytics>
        </ThemeProvider>,
      );
    }
  });
});

export default KetcherTinyMce;
