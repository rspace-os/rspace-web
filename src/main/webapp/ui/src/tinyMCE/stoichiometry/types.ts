import type {
  InventoryLink,
  StoichiometryMolecule,
} from "@/modules/stoichiometry/schema";

export type EditableMolecule = StoichiometryMolecule & {
  moles: number | null;
  actualMoles: number | null;
  savedInventoryLink: InventoryLink | null;
  deletedInventoryLink: InventoryLink | null;
};
