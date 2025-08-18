import { type StoichiometryMolecule } from "../../hooks/api/useStoichiometry";

export function calculateMoles(
  mass: number | null,
  molecularWeight: number | null,
): number | null {
  if (mass === null || molecularWeight === null || molecularWeight <= 0) {
    return null;
  }
  return mass / molecularWeight;
}

/**
 * Calculates updated molecules based on stoichiometric relationships.
 *
 * CHEMISTRY BACKGROUND:
 * Stoichiometry is the calculation of quantities in chemical reactions.
 * - Reactants: chemicals that are consumed in the reaction
 * - Products: chemicals that are produced by the reaction
 * - Coefficients: numbers that show the ratio of molecules (e.g., 2H₂ + O₂ → 2H₂O means 2:1:2 ratio)
 * - Limiting reagent: the reactant that runs out first, determining how much product can be made
 * - Moles: a unit for counting molecules (like "dozen" but for chemistry)
 * - Molecular weight: how much one mole of a substance weighs
 *
 * This function handles:
 * - Storing changes to notes
 *
 * Note that this function assumes that only one property of one molecule has
 * been edited. This is imporant because some properties are interdependent,
 * such as mass, moles, and molecular weight.
 */
export function calculateUpdatedMolecules(
  allMolecules: ReadonlyArray<StoichiometryMolecule>,
  editedRow: StoichiometryMolecule,
): ReadonlyArray<StoichiometryMolecule> {
  /*
   * This function applies a single change to the edited molecule in the
   * allMolecules array. It takes a key and a new value, and returns a
   * new array where just the edited molecule has been updated with the new
   * value for the specified key, leaveing all other molecules unchanged.
   */
  function applyChange<Key extends keyof StoichiometryMolecule>(
    key: Key,
    newValue: StoichiometryMolecule[Key],
  ) {
    return allMolecules.map((molecule) =>
      molecule.id === editedRow.id
        ? { ...molecule, [key]: newValue }
        : molecule,
    );
  }

  const beforeMolecule =
    allMolecules[allMolecules.findIndex((m) => m.id === editedRow.id)];

  if (beforeMolecule.notes !== editedRow.notes) {
    return applyChange("notes", editedRow.notes);
  }

  return allMolecules;
}
