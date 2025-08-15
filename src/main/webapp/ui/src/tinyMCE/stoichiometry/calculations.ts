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
  if (
    !actualAmount ||
    !molecularWeight ||
    actualAmount <= 0 ||
    molecularWeight <= 0
  ) {
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
  if (
    !actualAmount ||
    !theoreticalMass ||
    actualAmount <= 0 ||
    theoreticalMass <= 0
  ) {
    return null;
  }
  return Number(((actualAmount / theoreticalMass) * 100).toFixed(2));
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
 * 1. Converting between mass (grams) and moles using molecular weight
 * 2. Finding which reactant limits the reaction (limiting reagent)
 * 3. Calculating how much product can theoretically be made
 * 4. Computing yield (actual vs theoretical amounts) and excess reagents
 */
export function calculateUpdatedMolecules(
  allMolecules: ReadonlyArray<StoichiometryMolecule>,
  editedRow: StoichiometryMolecule,
): ReadonlyArray<StoichiometryMolecule> {
  // Create a copy of all molecules to avoid mutating the original data
  const updatedMolecules = allMolecules.map((molecule) => ({ ...molecule }));
  const editedMolecule = updatedMolecules.find((m) => m.id === editedRow.id);

  if (!editedMolecule) return updatedMolecules;

  // Update the edited molecule with new values from the user input
  editedMolecule.mass = editedRow.mass;
  editedMolecule.moles = editedRow.moles;
  editedMolecule.notes = editedRow.notes;
  editedMolecule.coefficient = editedRow.coefficient;
  editedMolecule.limitingReagent = editedRow.limitingReagent;
  editedMolecule.actualAmount = editedRow.actualAmount;

  // MASS-MOLE CONVERSION:
  // Mass and moles are related by molecular weight: mass = moles × molecular_weight
  // If user changed mass, calculate new moles. If user changed moles, calculate new mass.
  if (editedMolecule.molecularWeight && editedMolecule.molecularWeight > 0) {
    const originalMolecule = allMolecules.find((m) => m.id === editedRow.id);

    if (originalMolecule) {
      // If mass was changed, calculate corresponding moles
      if (
        editedMolecule.mass !== originalMolecule.mass &&
        editedMolecule.mass &&
        editedMolecule.mass > 0
      ) {
        editedMolecule.moles = Number(
          (editedMolecule.mass / editedMolecule.molecularWeight).toFixed(6),
        );
      }
      // If moles was changed, calculate corresponding mass
      else if (
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

  // LIMITING REAGENT SELECTION:
  // Only one reactant can be the limiting reagent (the one that runs out first).
  // When a new limiting reagent is selected, unmark all other reactants.
  if (editedMolecule.limitingReagent) {
    updatedMolecules.forEach((molecule) => {
      if (
        molecule.id !== editedMolecule.id &&
        molecule.role.toLowerCase() === "reactant"
      ) {
        molecule.limitingReagent = false;
      }
    });

    // COEFFICIENT NORMALIZATION:
    // Scale all coefficients so the limiting reagent has coefficient of 1.
    // This makes calculations easier - we can directly use the limiting reagent's moles
    // to calculate how much of everything else is needed/produced.
    const newLimitingReagentCoeff = editedMolecule.coefficient || 1;
    if (newLimitingReagentCoeff !== 1) {
      const scalingFactor = 1 / newLimitingReagentCoeff;
      updatedMolecules.forEach((molecule) => {
        const currentCoeff = molecule.coefficient || 1;
        molecule.coefficient = Number(
          (currentCoeff * scalingFactor).toFixed(6),
        );
      });
    }
  }

  // STOICHIOMETRIC CALCULATIONS:
  // Once we know the limiting reagent, we can calculate how much product will be made
  // and how much of other reactants will be consumed.
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

      // THEORETICAL CALCULATION:
      // Use the coefficient ratios to calculate how many moles of this molecule
      // should be involved based on the limiting reagent.
      // Formula: theoretical_moles = (limiting_moles / limiting_coeff) × this_molecule_coeff
      const theoreticalMoles = (limitingMoles / limitingCoeff) * coeff;

      // For PRODUCTS: Update with theoretical amounts (how much should be produced)
      // For REACTANTS: Keep original amounts to later calculate how much is excess
      if (molecule.role.toLowerCase() === "product") {
        molecule.moles = Number(theoreticalMoles.toFixed(6));
        if (molecule.molecularWeight) {
          molecule.mass = Number(
            (theoreticalMoles * molecule.molecularWeight).toFixed(4),
          );
        }
      }
    });
  }

  // YIELD AND EXCESS CALCULATIONS:
  // For products: yield = (actual amount produced / theoretical amount) × 100%
  // For non-limiting reactants: excess = (amount you have - amount needed) / amount needed × 100%
  updatedMolecules.forEach((molecule) => {
    if (limitingReagent) {
      if (
        molecule.role.toLowerCase() === "product" &&
        molecule.actualAmount &&
        molecule.mass
      ) {
        // PRODUCT YIELD: How efficient was the reaction?
        // 100% = perfect reaction, <100% = some product was lost or reaction incomplete
        molecule.actualYield = calculateYield({
          actualAmount: molecule.actualAmount,
          theoreticalMass: molecule.mass,
        });
      } else if (
        molecule.role.toLowerCase() === "reactant" &&
        !molecule.limitingReagent &&
        molecule.moles &&
        molecule.moles > 0
      ) {
        // EXCESS REACTANT CALCULATION:
        // How much more of this reactant do we have than we actually need?
        const limitingMoles = limitingReagent.moles || 0;
        const limitingCoeff = limitingReagent.coefficient || 1;
        const moleculeCoeff = molecule.coefficient || 1;

        // Calculate how much of this reactant is actually needed
        const requiredMoles = (limitingMoles / limitingCoeff) * moleculeCoeff;

        if (requiredMoles > 0) {
          // Excess percentage = ((what we have - what we need) / what we need) × 100
          // Positive = excess, negative = deficit (shouldn't happen if limiting reagent is correct)
          const excessPercent =
            ((molecule.moles - requiredMoles) / requiredMoles) * 100;
          molecule.actualYield = Number(excessPercent.toFixed(2));
        } else {
          molecule.actualYield = null;
        }
      } else {
        molecule.actualYield = null;
      }
    } else {
      // No limiting reagent selected, so no calculations possible
      molecule.actualYield = null;
    }
  });

  return updatedMolecules;
}
