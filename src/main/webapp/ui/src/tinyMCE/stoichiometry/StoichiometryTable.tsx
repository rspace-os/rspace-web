import { useStoichiometryTableController } from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import StoichiometryTableLoadingDialog from "@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog";
import StaticStoichiometryTable from "./table/StaticStoichiometryTable";
import StoichiometryTableGrid from "./table/StoichiometryTableGrid";
import type { StoichiometryTableProps } from "./table/types";

const StoichiometryTable = ({
  stoichiometryId,
  stoichiometryRevision,
  editable = false,
  hasChanges = false,
  activeChemId = null,
}: StoichiometryTableProps) => {
  const tableController = useStoichiometryTableController();

  if (editable && tableController) {
    if (tableController.isGettingMoleculeInfo) {
      return <StoichiometryTableLoadingDialog />;
    }

    return (
      <StoichiometryTableGrid
        editable
        allMolecules={tableController.allMolecules}
        hasChanges={hasChanges}
        activeChemId={activeChemId}
        linkedInventoryQuantityInfoByGlobalId={tableController.linkedInventoryQuantityInfoByGlobalId}
        onAddReagent={tableController.addReagent}
        onUpdateInventoryStock={tableController.updateInventoryStock}
        onDeleteReagent={tableController.deleteReagent}
        onPickInventoryItem={tableController.pickInventoryLink}
        onRemoveInventoryLink={tableController.removeInventoryLink}
        onUndoRemoveInventoryLink={tableController.undoRemoveInventoryLink}
        onSelectLimitingReagent={tableController.selectLimitingReagent}
        onProcessRowUpdate={tableController.processRowUpdate}
      />
    );
  }

  return (
    <StaticStoichiometryTable
      stoichiometryId={stoichiometryId}
      stoichiometryRevision={stoichiometryRevision}
      editable={editable}
    />
  );
};

export default StoichiometryTable;
