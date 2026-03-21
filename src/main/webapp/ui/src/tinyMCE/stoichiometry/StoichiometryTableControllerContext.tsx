import React from "react";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
export type StoichiometryTableController = {
  allMolecules: ReadonlyArray<EditableMolecule>;
  isGettingMoleculeInfo: boolean;
  addReagent: (
    smilesString: string,
    name: string,
    source: string,
  ) => Promise<void>;
  deleteReagent: (moleculeId: number) => void;
  pickInventoryLink: (
    moleculeId: number,
    inventoryItemId: number,
    inventoryItemGlobalId: string,
  ) => void;
  removeInventoryLink: (moleculeId: number) => void;
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
export function StoichiometryTableControllerProvider({
  value,
  children,
}: StoichiometryTableControllerProviderProps): React.ReactNode {
  return (
    <StoichiometryTableControllerContext.Provider value={value}>
      {children}
    </StoichiometryTableControllerContext.Provider>
  );
}
export function useStoichiometryTableController() {
  return React.useContext(StoichiometryTableControllerContext);
}
