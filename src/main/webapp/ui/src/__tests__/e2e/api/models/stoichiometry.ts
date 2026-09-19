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

export interface ApiStoichiometryMoleculeUpdate {
  id: number;
  role: MoleculeRole;
  smiles: string;
  name: string;
  formula: string;
  molecularWeight: number;
  coefficient: number;
  mass: number | null;
  actualAmount: number | null;
  actualYield: number | null;
  limitingReagent: boolean;
  notes: string | null;
  inventoryLink: { inventoryItemGlobalId: string } | null;
}

export interface ApiStoichiometryUpdateRequest {
  id: number;
  molecules: ApiStoichiometryMoleculeUpdate[];
}

/** Maps a GET response molecule into the shape the PUT endpoint expects, preserving every field unchanged. */
export function toMoleculeUpdate(molecule: ApiStoichiometryMolecule): ApiStoichiometryMoleculeUpdate {
  return {
    id: molecule.id,
    role: molecule.role,
    smiles: molecule.smiles,
    name: molecule.name,
    formula: molecule.formula,
    molecularWeight: molecule.molecularWeight,
    coefficient: molecule.coefficient,
    mass: molecule.mass,
    actualAmount: molecule.actualAmount,
    actualYield: molecule.actualYield,
    limitingReagent: molecule.limitingReagent,
    notes: molecule.notes,
    inventoryLink: molecule.inventoryLink
      ? { inventoryItemGlobalId: molecule.inventoryLink.inventoryItemGlobalId }
      : null,
  };
}

export interface ApiStockDeductionRequest {
  stoichiometryId: number;
  linkIds: number[];
  updateFieldHtml?: boolean;
}

export interface ApiStockDeductionIndividualResult {
  linkId: number;
  success: boolean;
  errorMessage: string | null;
}

export interface ApiStockDeductionResult {
  results: ApiStockDeductionIndividualResult[];
  stoichiometryId: number;
  revisionNumber: number;
}
