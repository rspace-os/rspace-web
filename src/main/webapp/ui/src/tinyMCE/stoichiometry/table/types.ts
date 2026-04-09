import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import type { InventoryStockUpdateResult } from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateDialog";
import type { EditableMolecule } from "../types";

export type StoichiometryTableProps = {
  stoichiometryId: number;
  stoichiometryRevision: number;
  editable?: boolean;
  hasChanges?: boolean;
};

export type StoichiometryTableGridProps = {
  editable: boolean;
  allMolecules: ReadonlyArray<EditableMolecule>;
  hasChanges?: boolean;
  linkedInventoryQuantityInfoByGlobalId?: ReadonlyMap<
    string,
    InventoryQuantityQueryResult
  >;
  isGettingMoleculeInfo?: boolean;
  onAddReagent?: (
    smilesString: string,
    name: string,
    source: string,
  ) => Promise<void>;
  onUpdateInventoryStock?: (
    selectedMoleculeIds: number[],
  ) => Promise<InventoryStockUpdateResult>;
  onDeleteReagent?: (moleculeId: number) => void;
  onPickInventoryItem?: (
    moleculeId: number,
    inventoryItemId: number,
    inventoryItemGlobalId: string,
  ) => void;
  onRemoveInventoryLink?: (moleculeId: number) => void;
  onUndoRemoveInventoryLink?: (moleculeId: number) => void;
  onSelectLimitingReagent?: (molecule: EditableMolecule) => void;
  onProcessRowUpdate?: (
    newRow: EditableMolecule,
    oldRow: EditableMolecule,
  ) => EditableMolecule;
};
