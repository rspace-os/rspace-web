import Backdrop from "@mui/material/Backdrop";
import CircularProgress from "@mui/material/CircularProgress";
import type { Ketcher } from "ketcher-core";
import React from "react";
import { useTranslation } from "react-i18next";
import {
  StructureSearchTypeControl,
  useSingleMoleculeValidation,
} from "../../../components/Ketcher/StructureSearchControls";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import type { ChemicalFilter, ChemSearchType } from "../../../stores/definitions/Search";

const KetcherDialog = React.lazy(() => import("../../../components/Ketcher/KetcherDialog"));

type ChemicalStructureDialogArgs = {
  open: boolean;
  /** The current structure filter, loaded into the editor for editing. */
  initialFilter: ChemicalFilter | null;
  onSubmit: (filter: ChemicalFilter) => void;
  onClose: () => void;
};

/**
 * Draw or paste a structure to filter Inventory by. Uses the same editor,
 * match types and single-molecule rule as the Workspace chemical search.
 */
export default function ChemicalStructureDialog({
  open,
  initialFilter,
  onSubmit,
  onClose,
}: ChemicalStructureDialogArgs): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [searchType, setSearchType] = React.useState<ChemSearchType>(initialFilter?.chemSearchType ?? "SUBSTRUCTURE");
  const { validationResult, validate } = useSingleMoleculeValidation();

  React.useEffect(() => {
    if (open) setSearchType(initialFilter?.chemSearchType ?? "SUBSTRUCTURE");
  }, [open, initialFilter]);

  if (!open) return null;

  const handleInsert = (ketcher: Ketcher) => {
    void ketcher.getSmiles().then((smiles) => {
      trackEvent("user:search:chemistry", { from: "inventory", searchType });
      onSubmit({ chemQuery: smiles, chemSearchType: searchType });
      void ketcher.setMolecule("");
    });
  };

  return (
    <React.Suspense
      fallback={
        <Backdrop open sx={{ color: "#fff", zIndex: 3 }}>
          <CircularProgress color="inherit" />
        </Backdrop>
      }
    >
      <KetcherDialog
        isOpen
        title={t("search.controls.chemical.dialogTitle")}
        handleInsert={handleInsert}
        actionBtnText={t("common:actions.search")}
        handleClose={onClose}
        existingChem={initialFilter?.chemQuery}
        validationResult={validationResult}
        instructionText={t("common:ketcher.structureSearch.drawInstruction")}
        onChange={() => {
          validate(window.ketcher);
        }}
        additionalControls={<StructureSearchTypeControl value={searchType} onChange={setSearchType} />}
      />
    </React.Suspense>
  );
}
