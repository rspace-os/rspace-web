import { type StoichiometryMolecule } from "../../hooks/api/useStoichiometry";

export type EditableMolecule = StoichiometryMolecule & {
  moles: number | null;
  actualMoles: number | null;
};

export function calculateMoles(
  mass: EditableMolecule["mass"],
  molecularWeight: EditableMolecule["molecularWeight"],
): EditableMolecule["moles"] {
  if (mass === null || molecularWeight === null || molecularWeight <= 0) {
    return null;
  }
  return mass / molecularWeight;
}

function normaliseCoefficients(
  molecules: ReadonlyArray<EditableMolecule>,
  limitingReagent: EditableMolecule,
): ReadonlyArray<EditableMolecule> {
  /*
   * This function normalises the coefficients of the molecules based on the
   * limiting reagent. It ensures that all coefficients are relative to the
   * limiting reagent's coefficient.
   */
  const limitingCoefficient = limitingReagent.coefficient;

  return molecules.map((molecule) => ({
    ...molecule,
    coefficient: molecule.coefficient / limitingCoefficient,
  }));
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
 * - Update mass when mass is changed
 * - Update mass when moles are changed
 * - Update actual amount when actual amount is changed
 * - Update actual amount when actual moles are changed
 * - Update limiting reagent when it is changed, normalising coefficients
 * - Update coefficients when they are changed, normalising coefficients
 *
 * Note that this function assumes that only one property of one molecule has
 * been edited. This is imporant because some properties are interdependent,
 * such as mass, moles, and molecular weight.
 */
export function calculateUpdatedMolecules(
  allMolecules: ReadonlyArray<EditableMolecule>,
  editedRow: EditableMolecule,
): ReadonlyArray<EditableMolecule> {
  /*
   * This function applies a single change to the edited molecule in the
   * allMolecules array. It takes a key and a new value, and returns a
   * new array where just the edited molecule has been updated with the new
   * value for the specified key, leaveing all other molecules unchanged.
   */
  function applyChanges(
    newProperties: Partial<EditableMolecule>,
    molecules = allMolecules,
  ) {
    return molecules.map((molecule) =>
      molecule.id === editedRow.id
        ? { ...molecule, ...newProperties }
        : molecule,
    );
  }

  const beforeMolecule =
    allMolecules[allMolecules.findIndex((m) => m.id === editedRow.id)];

  if (beforeMolecule.id !== editedRow.id)
    throw new Error(
      "ID is an intrinsic property of the chemical and cannot be modified",
    );
  if (beforeMolecule.name !== editedRow.name)
    throw new Error(
      "Name is an intrinsic property of the chemical and cannot be modified",
    );
  if (beforeMolecule.molecularWeight !== editedRow.molecularWeight)
    throw new Error(
      "Molecula weight is an intrinsic property of the chemical and cannot be modified",
    );
  if (beforeMolecule.formula !== editedRow.formula)
    throw new Error(
      "Chemical formula is an intrinsic property of the chemical and cannot be modified",
    );
  if (beforeMolecule.smiles !== editedRow.smiles)
    throw new Error(
      "The SMILES representation is an intrinsic property of the chemical and cannot be modified",
    );

  if (beforeMolecule.role !== editedRow.role)
    throw new Error("Modifying the role of a molecule is not supported");
  if (beforeMolecule.rsChemElement !== editedRow.rsChemElement)
    throw new Error(
      "Modifying the rsChemElement of a molecule is not supported",
    );

  if (beforeMolecule.notes !== editedRow.notes) {
    return applyChanges({
      notes: editedRow.notes,
    });
  }

  if (beforeMolecule.mass !== editedRow.mass) {
    return applyChanges({
      mass: editedRow.mass,
    });
  }

  if (editedRow.moles !== null) {
    return applyChanges({
      mass:
        editedRow.moles === null
          ? null
          : editedRow.moles * beforeMolecule.molecularWeight,
    });
  }

  if (beforeMolecule.actualAmount !== editedRow.actualAmount) {
    return applyChanges({
      actualAmount: editedRow.actualAmount,
    });
  }

  if (editedRow.actualMoles !== null) {
    return applyChanges({
      actualAmount:
        editedRow.actualMoles === null
          ? null
          : editedRow.actualMoles * beforeMolecule.molecularWeight,
    });
  }

  if (beforeMolecule.limitingReagent !== editedRow.limitingReagent) {
    const updatedMolecules = applyChanges(
      {
        limitingReagent: editedRow.limitingReagent,
      },
      allMolecules.map((m) => ({ ...m, limitingReagent: false })),
    );
    const newLimitingReagent = updatedMolecules.find((m) => m.limitingReagent);
    if (!newLimitingReagent) {
      throw new Error("No limiting reagent found after update");
    }
    return normaliseCoefficients(updatedMolecules, newLimitingReagent);
  }

  if (beforeMolecule.coefficient !== editedRow.coefficient) {
    const updatedMolecules = applyChanges({
      coefficient: editedRow.coefficient,
    });
    const newLimitingReagent = updatedMolecules.find((m) => m.limitingReagent);
    if (!newLimitingReagent) {
      throw new Error("No limiting reagent found after update");
    }
    return normaliseCoefficients(updatedMolecules, newLimitingReagent);
  }

  return allMolecules;
}
