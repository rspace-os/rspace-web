import React, { useEffect, useState } from "react";

import KetcherDialog from "../../components/Ketcher/KetcherDialog";
import { createRoot } from "react-dom/client";
import { blobToBase64 } from "../../util/files";
import axios from "@/common/axios";
import AnalyticsContext from "../../stores/contexts/Analytics";
import Analytics from "../../components/Analytics";
import { ThemeProvider } from "@mui/material/styles";
import theme from "../../theme";
import { IsInvalid, IsValid } from "@/components/ValidatingSubmitButton";

export const KetcherTinyMce = () => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [existingChemical, setExistingChemical] = useState("");
  const [dialogIsOpen, setDialogIsOpen] = useState(true);
  const [isValid, setIsValid] = useState(IsValid());
  let ketcherObj;

  useEffect(() => {
    const editor = tinymce.activeEditor;
    const selectedNode = editor.selection.getNode();
    if ($(selectedNode).hasClass("chem")) {
      const chemElemId = selectedNode.getAttribute("id");
      const revision = selectedNode.getAttribute("data-rsrevision");
      const data = { chemId: chemElemId, revision };

      axios
        .get("/chemical/ajax/loadChemElements", {
          params: data,
        })
        .then((response) => {
          if (response.data !== null) {
            setExistingChemical(response.data.data.chemElements);
          }
        })
        .catch(() => {
          tinymceDialogUtils.showErrorAlert(
            "Loading chemical elements failed."
          );
        });
    }
  }, []);

  const saveFullSizeImage = (passedSmiles, newChemElemId) => {
    ketcherObj.generateImage(passedSmiles).then((res) => {
      blobToBase64(res).then((imgString) => {
        const data = {
          chemId: newChemElemId,
          imageBase64: imgString,
        };
        axios
          .post("/chemical/ajax/saveChemImage", null, { params: data })
          .catch(() => {
            console.warn("Saving chemical preview failed.");
          });
      });
    });
  };

  const insertChemicalIntoDoc = (newChemElemId) => {
    const editor = tinymce.activeEditor;
    const fullWidth = 500;
    const fullHeight = 500;
    const previewWidth = 250;
    const previewHeight = 250;
    const fieldId = editor.id.replace(/^\D+/g, "");
    const milliseconds = new Date().getTime();
    const json = {
      id: newChemElemId,
      ecatChemFileId: null,
      sourceParentId: fieldId,
      width: previewWidth,
      height: previewHeight,
      fullwidth: fullWidth,
      fullheight: fullHeight,
      fieldId,
      tstamp: milliseconds,
    };
    const url = "/fieldTemplates/ajax/chemElementLink";
    axios.get(url).then((htmlTemplate) => {
      const html = Mustache.render(htmlTemplate.data, json);
      if (html !== "") {
        editor.execCommand("mceInsertContent", false, html);
        const event = new CustomEvent("tinymce-chem-inserted", {
          detail: json.id,
        });
        document.dispatchEvent(event);
        trackEvent("user:add:chemistry_object:document", { from: "ketcher" });
      }
      editor.windowManager.close();
    });
  };

  const saveChemicalAndInsert = (chemical) => {
    const data = {
      chemElements: chemical,
      chemElementsFormat: "ket",
      fieldId: tinymce.activeEditor.id.replace(/^\D+/g, ""),
      chemId: "",
      imageBase64: "",
    };
    axios
      .post("/chemical/save", data)
      .then((result) => {
        if (result !== null) {
          const newChemElemId = result.data.id;
          saveFullSizeImage(chemical, newChemElemId);
          insertChemicalIntoDoc(newChemElemId);
        }
      })
      .catch((err) => {
        tinymceDialogUtils.showErrorAlert("Saving chemical elements failed.");
      });
  };

  const handleInsert = (ketcher) => {
    ketcherObj = ketcher;
    ketcherObj.getKet().then(
      (chemical) => { 
        saveChemicalAndInsert(chemical);
        setDialogIsOpen(false);
        setExistingChemical("");
        void window.ketcher.setMolecule("");
      },
      () => {}
    );
  };

  const handleClose = () => {
    setDialogIsOpen(false);
    setExistingChemical("");
  };

  const validate = (ketcher) => {
    if (!ketcher) {
      setIsValid(IsValid());
      return;
    }
    ketcher.getKet().then((ketData) => {
      const molecules = Object.keys(JSON.parse(ketData)).filter((key) =>
        key.startsWith("mol")
      );
      if (molecules.length === 0) {
        setIsValid(
          IsInvalid(
            "Please draw, paste, or open a molecule to insert into the document"
          )
        );
        return;
      }
      setIsValid(IsValid());
    });
  };

  return $(tinymce.activeEditor.selection.getNode()).hasClass("chem") &&
    existingChemical === "" ? null : (
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
  );
};

document.addEventListener("DOMContentLoaded", () => {
  window.addEventListener("OPEN_KETCHER_DIALOG", () => {
    // todo: check if the root container already exists before createRoot()
    const wrapperDiv = top.document.getElementById("tinymce-ketcher");

    if (wrapperDiv) {
      const root = createRoot(wrapperDiv);
      root.render(
        <ThemeProvider theme={theme}>
          <Analytics>
            <KetcherTinyMce />
          </Analytics>
        </ThemeProvider>
      );
    }
  });
});

export default KetcherTinyMce;
