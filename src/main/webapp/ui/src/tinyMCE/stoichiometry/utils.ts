import { produce } from "immer";
import type { EditableMolecule } from "./types";

export function calculateMoles(
  mass: EditableMolecule["mass"],
  molecularWeight: EditableMolecule["molecularWeight"],
): EditableMolecule["moles"] {
  if (mass === null || molecularWeight === null || molecularWeight <= 0) {
    return null;
  }
  return mass / molecularWeight;
}

export function hasDuplicateInventoryLink(
  molecules: ReadonlyArray<EditableMolecule>,
  moleculeId: number,
  inventoryItemGlobalId: string,
): boolean {
  return molecules.some(
    (molecule) =>
      molecule.id !== moleculeId &&
      molecule.inventoryLink?.inventoryItemGlobalId === inventoryItemGlobalId,
  );
}

function calculateActualYieldOrExcess(
  molecule: EditableMolecule,
  limitingReagentMoles: number,
): number | null {
  if (molecule.role === "PRODUCT") {
    // For products, calculate yield percentage based on theoretical yield from limiting reagent
    if (molecule.actualAmount === null || limitingReagentMoles <= 0) {
      return null;
    }
    const theoreticalMoles = limitingReagentMoles * molecule.coefficient;
    const theoreticalMass = theoreticalMoles * molecule.molecularWeight;
    if (theoreticalMass <= 0) {
      return null;
    }
    return molecule.actualAmount / theoreticalMass;
  } else if (
    (molecule.role === "REACTANT" || molecule.role === "AGENT") &&
    !molecule.limitingReagent
  ) {
    // For non-limiting reactants, calculate excess using molar ratio formula
    if (molecule.actualAmount === null || limitingReagentMoles <= 0) {
      return null;
    }
    return (
      (calculateMoles(molecule.actualAmount, molecule.molecularWeight) ?? 0) /
        molecule.coefficient /
        limitingReagentMoles -
      1
    );
  }
  return null;
}

function updateYieldAndExcess(
  molecules: ReadonlyArray<EditableMolecule>,
): ReadonlyArray<EditableMolecule> {
  const limitingReagent = molecules.find((m) => m.limitingReagent);
  if (!limitingReagent || limitingReagent.actualAmount === null) {
    return molecules;
  }

  const limitingReagentMoles =
    (calculateMoles(
      limitingReagent.actualAmount,
      limitingReagent.molecularWeight,
    ) ?? 0) / limitingReagent.coefficient;
  if (limitingReagentMoles <= 0) {
    return molecules;
  }

  return produce(molecules, (draftMolecules) => {
    for (const molecule of draftMolecules) {
      molecule.actualYield = calculateActualYieldOrExcess(
        molecule,
        limitingReagentMoles,
      );
    }
  });
}

function normaliseCoefficients(
  molecules: ReadonlyArray<EditableMolecule>,
  limitingReagent: EditableMolecule,
): ReadonlyArray<EditableMolecule> {
  const limitingCoefficient = limitingReagent.coefficient;

  return produce(molecules, (draftMolecules) => {
    for (const molecule of draftMolecules) {
      molecule.coefficient = molecule.coefficient / limitingCoefficient;
    }
  });
}

function applyMassByRatio(
  molecules: ReadonlyArray<EditableMolecule>,
  ratio: number | null,
) {
  if (ratio === null) {
    return molecules;
  }

  return produce(molecules, (draftMolecules) => {
    for (const molecule of draftMolecules) {
      molecule.mass = molecule.coefficient * ratio * molecule.molecularWeight;
    }
  });
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
 * - Update moles when mass is changed
 * - Update mass when moles are changed
 * - Update actual moles when actual amount is changed
 * - Update actual amount when actual moles are changed
 * - Update limiting reagent when it is changed, normalising coefficients
 * - Update coefficients when they are changed, normalising coefficients
 * - Update yield/excess calculations for all molecules
 *
 * Note that this function assumes that only one property of one molecule has
 * been edited. This is important because some properties are interdependent,
 * such as mass, moles, and molecular weight.
 */
export function calculateUpdatedMolecules(
  allMolecules: ReadonlyArray<EditableMolecule>,
  editedRow: EditableMolecule,
): ReadonlyArray<EditableMolecule> {
  function applyChanges(
    newProperties: Partial<EditableMolecule>,
    molecules = allMolecules,
  ) {
    return produce(molecules, (draftMolecules) => {
      const editedMolecule = draftMolecules.find((m) => m.id === editedRow.id);
      if (!editedMolecule) {
        return;
      }
      Object.assign(editedMolecule, newProperties);
    });
  }

  const beforeMolecule = allMolecules.find((m) => m.id === editedRow.id);
  if (!beforeMolecule) {
    throw new Error("Edited molecule not found");
  }

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
      "Molecular weight is an intrinsic property of the chemical and cannot be modified",
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
    return updateYieldAndExcess(
      applyChanges({
        notes: editedRow.notes,
      }),
    );
  }

  if (beforeMolecule.mass !== editedRow.mass) {
    if (editedRow.limitingReagent) {
      const limitingReagent = allMolecules.find((m) => m.limitingReagent);
      if (!limitingReagent) throw new Error("No limiting reagent defined");
      const limitingReagentMoles = calculateMoles(
        editedRow.mass,
        limitingReagent.molecularWeight,
      );
      const ratio =
        limitingReagentMoles === null
          ? null
          : limitingReagentMoles / limitingReagent.coefficient;
      return updateYieldAndExcess(
        applyChanges(
          {
            mass: editedRow.mass,
          },
          applyMassByRatio(allMolecules, ratio),
        ),
      );
    } else {
      return updateYieldAndExcess(
        applyChanges({
          mass: editedRow.mass,
        }),
      );
    }
  }

  if (editedRow.moles !== null) {
    if (editedRow.limitingReagent) {
      const limitingReagent = allMolecules.find((m) => m.limitingReagent);
      if (!limitingReagent) throw new Error("No limiting reagent defined");
      const limitingReagentMoles = editedRow.moles;
      const ratio =
        limitingReagentMoles === null
          ? null
          : limitingReagentMoles / limitingReagent.coefficient;
      return updateYieldAndExcess(
        applyChanges(
          {
            mass: editedRow.moles * beforeMolecule.molecularWeight,
          },
          applyMassByRatio(allMolecules, ratio),
        ),
      );
    } else {
      return updateYieldAndExcess(
        applyChanges({
          mass: editedRow.moles * beforeMolecule.molecularWeight,
        }),
      );
    }
  }

  if (beforeMolecule.actualAmount !== editedRow.actualAmount) {
    return updateYieldAndExcess(
      applyChanges({
        actualAmount: editedRow.actualAmount,
      }),
    );
  }

  if (editedRow.actualMoles !== null) {
    return updateYieldAndExcess(
      applyChanges({
        actualAmount:
          editedRow.actualMoles === null
            ? null
            : editedRow.actualMoles * beforeMolecule.molecularWeight,
      }),
    );
  }

  if (beforeMolecule.limitingReagent !== editedRow.limitingReagent) {
    const updatedMolecules = applyChanges(
      {
        limitingReagent: editedRow.limitingReagent,
      },
      produce(allMolecules, (draftMolecules) => {
        for (const molecule of draftMolecules) {
          molecule.limitingReagent = false;
        }
      }),
    );
    const newLimitingReagent = updatedMolecules.find((m) => m.limitingReagent);
    if (!newLimitingReagent) {
      throw new Error("No limiting reagent found after update");
    }
    return updateYieldAndExcess(
      normaliseCoefficients(updatedMolecules, newLimitingReagent),
    );
  }

  if (beforeMolecule.coefficient !== editedRow.coefficient) {
    const changeInCoefficient =
      editedRow.coefficient / beforeMolecule.coefficient;
    const updatedMolecules = applyChanges({
      coefficient: editedRow.coefficient,
      mass:
        beforeMolecule.mass === null
          ? null
          : beforeMolecule.mass * changeInCoefficient,
    });
    const newLimitingReagent = updatedMolecules.find((m) => m.limitingReagent);
    if (!newLimitingReagent) {
      throw new Error("No limiting reagent found after update");
    }
    return updateYieldAndExcess(
      normaliseCoefficients(updatedMolecules, newLimitingReagent),
    );
  }

  return allMolecules;
}
