import { type StoichiometryMolecule } from "../../hooks/api/useStoichiometry";

/**
 * Calculates actual moles from actual amount (mass in grams) and molecular weight.
 */
export function calculateActualMoles({
  actualAmount,
  molecularWeight,
}: {
  actualAmount: number | null;
  molecularWeight: number | null;
}): number | null {
  if (!actualAmount || !molecularWeight || actualAmount <= 0 || molecularWeight <= 0) {
    return null;
  }
  return Number((actualAmount / molecularWeight).toFixed(6));
}

/**
 * Calculates yield percentage based on actual mass vs theoretical mass.
 */
export function calculateYield({
  actualAmount,
  theoreticalMass,
}: {
  actualAmount: number | null;
  theoreticalMass: number | null;
}): number | null {
  if (!actualAmount || !theoreticalMass || actualAmount <= 0 || theoreticalMass <= 0) {
    return null;
  }
  return Number(((actualAmount / theoreticalMass) * 100).toFixed(2));
}

/**
 * Calculates updated molecules based on stoichiometric relationships.
 * Handles mass-mole conversions, limiting reagent selection, and
 * stoichiometric calculations for all molecules in a reaction.
 */
export function calculateUpdatedMolecules(
  allMolecules: ReadonlyArray<StoichiometryMolecule>,
  editedRow: StoichiometryMolecule,
): ReadonlyArray<StoichiometryMolecule> {
  const updatedMolecules = allMolecules.map((molecule) => ({ ...molecule }));
  const editedMolecule = updatedMolecules.find((m) => m.id === editedRow.id);

  if (!editedMolecule) return updatedMolecules;

  editedMolecule.mass = editedRow.mass;
  editedMolecule.moles = editedRow.moles;
  editedMolecule.notes = editedRow.notes;
  editedMolecule.coefficient = editedRow.coefficient;
  editedMolecule.limitingReagent = editedRow.limitingReagent;
  editedMolecule.actualAmount = editedRow.actualAmount;

  if (editedMolecule.molecularWeight && editedMolecule.molecularWeight > 0) {
    const originalMolecule = allMolecules.find((m) => m.id === editedRow.id);

    if (originalMolecule) {
      if (
        editedMolecule.mass !== originalMolecule.mass &&
        editedMolecule.mass &&
        editedMolecule.mass > 0
      ) {
        editedMolecule.moles = Number(
          (editedMolecule.mass / editedMolecule.molecularWeight).toFixed(6),
        );
      } else if (
        editedMolecule.moles !== originalMolecule.moles &&
        editedMolecule.moles &&
        editedMolecule.moles > 0
      ) {
        editedMolecule.mass = Number(
          (editedMolecule.moles * editedMolecule.molecularWeight).toFixed(4),
        );
      }
    }
  }

  // Handle limiting reagent selection
  if (editedMolecule.limitingReagent) {
    updatedMolecules.forEach((molecule) => {
      if (
        molecule.id !== editedMolecule.id &&
        molecule.role.toLowerCase() === "reactant"
      ) {
        molecule.limitingReagent = false;
      }
    });
  }

  // Perform stoichiometric calculations if there's a limiting reagent
  const limitingReagent = updatedMolecules.find(
    (m) =>
      m.limitingReagent &&
      m.role.toLowerCase() === "reactant" &&
      m.moles &&
      m.moles > 0,
  );

  if (limitingReagent) {
    const limitingMoles = limitingReagent.moles || 0;
    const limitingCoeff = limitingReagent.coefficient || 1;

    updatedMolecules.forEach((molecule) => {
      if (molecule.id === limitingReagent.id) return;

      const coeff = molecule.coefficient || 1;
      // Calculate theoretical moles based on stoichiometry
      const theoreticalMoles = (limitingMoles / limitingCoeff) * coeff;

      molecule.moles = Number(theoreticalMoles.toFixed(6));
      if (molecule.molecularWeight) {
        molecule.mass = Number(
          (theoreticalMoles * molecule.molecularWeight).toFixed(4),
        );
      }
    });
  }

  // Calculate yield for all molecules that have actualAmount
  updatedMolecules.forEach((molecule) => {
    if (molecule.actualAmount && molecule.mass) {
      molecule.actualYield = calculateYield({
        actualAmount: molecule.actualAmount,
        theoreticalMass: molecule.mass,
      });
    } else {
      molecule.actualYield = null;
    }
  });

  return updatedMolecules;
}
