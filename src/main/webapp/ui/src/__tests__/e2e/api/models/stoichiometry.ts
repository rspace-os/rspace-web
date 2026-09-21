export type MoleculeRole = "MOLECULE" | "REACTANT" | "PRODUCT" | "AGENT";

export interface ApiStoichiometryInventoryLink {
  id: number;
  inventoryItemGlobalId: string;
  stockDeducted: boolean;
}

export interface ApiStoichiometryMolecule {
  id: number;
  rsChemElementId: number;
  inventoryLink: ApiStoichiometryInventoryLink | null;
  role: MoleculeRole;
  formula: string;
  name: string;
  smiles: string;
  coefficient: number;
  molecularWeight: number;
  mass: number | null;
  actualAmount: number | null;
  actualYield: number | null;
  limitingReagent: boolean;
  notes: string | null;
}

export interface ApiStoichiometry {
  id: number;
  parentReactionId: number | null;
  recordId: number;
  revision: number;
  molecules: ApiStoichiometryMolecule[];
}
