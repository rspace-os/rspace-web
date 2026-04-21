import React from "react";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import type { InventoryStockUpdateResult } from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateDialog";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
export type StoichiometryTableController = {
  allMolecules: ReadonlyArray<EditableMolecule>;
  linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<
    string,
    InventoryQuantityQueryResult
  >;
  isGettingMoleculeInfo: boolean;
  addReagent: (
    smilesString: string,
    name: string,
    source: string,
  ) => Promise<void>;
  deleteReagent: (moleculeId: number) => void;
  updateInventoryStock: (
    selectedMoleculeIds: number[],
  ) => Promise<InventoryStockUpdateResult>;
  pickInventoryLink: (
    moleculeId: number,
    inventoryItemId: number,
    inventoryItemGlobalId: string,
  ) => void;
  removeInventoryLink: (moleculeId: number) => void;
  undoRemoveInventoryLink: (moleculeId: number) => void;
  selectLimitingReagent: (molecule: EditableMolecule) => void;
  processRowUpdate: (
    newRow: EditableMolecule,
    oldRow: EditableMolecule,
  ) => EditableMolecule;
};
const StoichiometryTableControllerContext =
  React.createContext<StoichiometryTableController | null>(null);
type StoichiometryTableControllerProviderProps = {
  value: StoichiometryTableController;
  children: React.ReactNode;
};
export const StoichiometryTableControllerProvider = ({
  value,
  children,
}: StoichiometryTableControllerProviderProps) => {
  return (
    <StoichiometryTableControllerContext.Provider value={value}>
      {children}
    </StoichiometryTableControllerContext.Provider>
  );
};
export function useStoichiometryTableController() {
  return React.useContext(StoichiometryTableControllerContext);
}
