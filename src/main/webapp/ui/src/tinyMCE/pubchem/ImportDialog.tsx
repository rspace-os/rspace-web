import React from "react";
import useChemicalImport, {
  type ChemicalCompound,
} from "@/hooks/api/useChemicalImport";
import { type Editor } from ".";
import AnalyticsContext from "@/stores/contexts/Analytics";
import CompoundSearchDialog from "./CompoundSearchDialog";


/**
 * This dialog is opened by the TinyMCE plugin, allowing the users to browse
 * chemistry files on PubChem and importing into their document.
 */
export default function ImportDialog({
  open,
  onClose,
  editor,
}: {
  open: boolean;
  onClose: () => void;
  editor: Editor;
}): React.ReactNode {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { saveSmilesString, formatAsHtml } = useChemicalImport();
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  React.useEffect(() => {
    if (open) trackEvent("user:open:pubchem_import:document");
  }, [open, trackEvent]);

  function handleCompoundsSelected(compounds: ChemicalCompound[]) {
    setIsSubmitting(true);
    const fieldId = editor.id.replace(/^\D+/g, "");

    Promise.all(
      compounds.map((compound) =>
        saveSmilesString({
          name: compound.name,
          smiles: compound.smiles,
          fieldId,
          metadata: {
            "Pubchem CID": compound.pubchemId,
            CAS: compound.cas || "",
            "PubChem URL": compound.pubchemUrl,
          },
        })
      )
    ).then((data) => {
      setIsSubmitting(false);
      data.forEach(({ id, chemFileId }) => {
        formatAsHtml({ id, fieldId, chemFileId }).then((html) => {
          editor.execCommand("mceInsertContent", false, html);
        });
      });
      trackEvent("user:add:chemistry_object:document", { from: "pubchem" });
    });
  }

  return (
    <CompoundSearchDialog
      open={open}
      onClose={onClose}
      onCompoundsSelected={handleCompoundsSelected}
      title="Import from PubChem"
      submitButtonText={isSubmitting ? "Importing..." : "Import Selected"}
      showPubChemInfo={true}
      allowMultipleSelection={true}
    />
  );
}
