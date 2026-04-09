import { produce } from "immer";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import {
  convertFromGrams,
  convertToGrams,
  getQuantityUnitSymbol,
  isMassUnit,
} from "@/modules/inventory/utils";
import type { EditableMolecule } from "./types";

export type InventoryUpdateSelectionDisabledReason =
  | "missingInventoryLink"
  | "stockAlreadyDeducted"
  | "linkedStockUnavailable"
  | "nonMassInventoryQuantity"
  | "missingActualMass"
  | "insufficientStock";

export type InventoryUpdateEligibility = {
  disabledReason: InventoryUpdateSelectionDisabledReason | null;
  helperText: string | null;
  showInsufficientStockWarning: boolean;
  availableStockInGrams: number | null;
  stockDisplay: InventoryUpdateStockDisplay;
};

export type InventoryUpdateRemainingStatus =
  | "default"
  | "positive"
  | "zero"
  | "negative";

export type InventoryUpdateStockMetric = {
  rawValue: number | null;
  displayValue: string;
  unitLabel: string | null;
};

export type InventoryUpdateStockDisplay = {
  inStock: InventoryUpdateStockMetric;
  willUse: InventoryUpdateStockMetric;
  remaining: InventoryUpdateStockMetric;
  remainingStatus: InventoryUpdateRemainingStatus;
  warningText: string | null;
};

function formatInventoryUpdateMetricValue(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return "—";
  }

  const normalizedValue = Math.abs(value) < 0.0005 ? 0 : value;

  return normalizedValue.toLocaleString(undefined, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 3,
  });
}

function makeStockMetric(
  rawValue: number | null,
  unitLabel: string | null,
): InventoryUpdateStockMetric {
  return {
    rawValue,
    displayValue: formatInventoryUpdateMetricValue(rawValue),
    unitLabel,
  };
}

function makeEmptyStockDisplay(): InventoryUpdateStockDisplay {
  return {
    inStock: makeStockMetric(null, null),
    willUse: makeStockMetric(null, null),
    remaining: makeStockMetric(null, null),
    remainingStatus: "default",
    warningText: null,
  };
}

function hideProjectedStockMetrics(
  stockDisplay: InventoryUpdateStockDisplay,
): InventoryUpdateStockDisplay {
  return {
    inStock: stockDisplay.inStock,
    willUse: makeStockMetric(null, null),
    remaining: makeStockMetric(null, null),
    remainingStatus: "default",
    warningText: null,
  };
}

function buildInventoryUpdateStockDisplay(
  quantity: { numericValue: number; unitId: number },
  actualAmount: EditableMolecule["actualAmount"],
): InventoryUpdateStockDisplay {
  const unitLabel = getQuantityUnitSymbol(quantity.unitId);
  const inStock = makeStockMetric(quantity.numericValue, unitLabel);

  if (!isMassUnit(quantity.unitId)) {
    return {
      inStock,
      willUse: makeStockMetric(null, unitLabel),
      remaining: makeStockMetric(null, unitLabel),
      remainingStatus: "default",
      warningText: null,
    };
  }

  if (actualAmount === null || actualAmount === undefined) {
    return {
      inStock,
      willUse: makeStockMetric(null, unitLabel),
      remaining: makeStockMetric(null, unitLabel),
      remainingStatus: "default",
      warningText: null,
    };
  }

  const willUseValue = convertFromGrams(Number(actualAmount), quantity.unitId);
  if (willUseValue === null) {
    return {
      inStock,
      willUse: makeStockMetric(null, unitLabel),
      remaining: makeStockMetric(null, unitLabel),
      remainingStatus: "default",
      warningText: null,
    };
  }

  const rawRemainingValue = quantity.numericValue - willUseValue;
  const remainingValue = Math.abs(rawRemainingValue) < 0.0005 ? 0 : rawRemainingValue;
  const remainingStatus: InventoryUpdateRemainingStatus =
    remainingValue < 0 ? "negative" : remainingValue === 0 ? "zero" : "positive";

  return {
    inStock,
    willUse: makeStockMetric(willUseValue, unitLabel),
    remaining: makeStockMetric(remainingValue, unitLabel),
    remainingStatus,
    warningText: remainingStatus === "negative" ? "Insufficient Stock" : null,
  };
}

function getInventoryUpdateDisabledReasonText(
  reason: InventoryUpdateSelectionDisabledReason,
): string {
  return {
    missingInventoryLink: "Link an inventory item before updating stock.",
    stockAlreadyDeducted: "Stock has already been deducted for this molecule.",
    linkedStockUnavailable:
      "Linked stock information is unavailable, so this molecule cannot be updated.",
    nonMassInventoryQuantity:
      "Deducting inventory stock for inventory items with non-gram units is currently not supported.",
    missingActualMass:
      "Define actual mass before updating linked inventory stock.",
    insufficientStock:
      "There is insufficient linked stock for this molecule's actual mass.",
  }[reason];
}

export function getInventoryUpdateEligibility(
  molecule: EditableMolecule,
  linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<
    string,
    InventoryQuantityQueryResult
  > = new Map<string, InventoryQuantityQueryResult>(),
): InventoryUpdateEligibility {
  const inventoryItemGlobalId = molecule.inventoryLink?.inventoryItemGlobalId;
  if (!inventoryItemGlobalId) {
    return {
      disabledReason: "missingInventoryLink",
      helperText: getInventoryUpdateDisabledReasonText("missingInventoryLink"),
      showInsufficientStockWarning: false,
      availableStockInGrams: null,
      stockDisplay: makeEmptyStockDisplay(),
    };
  }

  const linkedInventoryQuantityInfo = linkedInventoryQuantityInfoByGlobalId.get(
    inventoryItemGlobalId,
  );
  if (
    !linkedInventoryQuantityInfo ||
    linkedInventoryQuantityInfo.status !== "available" ||
    !linkedInventoryQuantityInfo.quantity
  ) {
    return {
      disabledReason: "linkedStockUnavailable",
      helperText: getInventoryUpdateDisabledReasonText("linkedStockUnavailable"),
      showInsufficientStockWarning: false,
      availableStockInGrams: null,
      stockDisplay: makeEmptyStockDisplay(),
    };
  }

  const { quantity } = linkedInventoryQuantityInfo;
  const stockDisplay = buildInventoryUpdateStockDisplay(quantity, molecule.actualAmount);
  const stockDeducted = molecule.inventoryLink?.stockDeducted === true;
  const visibleStockDisplay = stockDeducted
    ? hideProjectedStockMetrics(stockDisplay)
    : stockDisplay;

  const availableStockInGrams = convertToGrams(quantity.numericValue, quantity.unitId);
  const showInsufficientStockWarning =
    !stockDeducted && stockDisplay.remainingStatus === "negative";
  const disabledReason: InventoryUpdateSelectionDisabledReason | null =
    stockDeducted
      ? "stockAlreadyDeducted"
      : !isMassUnit(quantity.unitId)
        ? "nonMassInventoryQuantity"
        : molecule.actualAmount === null || molecule.actualAmount === undefined
          ? "missingActualMass"
        : availableStockInGrams === null
          ? "linkedStockUnavailable"
          : showInsufficientStockWarning
            ? "insufficientStock"
            : null;

  return {
    disabledReason,
    helperText:
      disabledReason === null || disabledReason === "insufficientStock"
        ? null
        : getInventoryUpdateDisabledReasonText(disabledReason),
    showInsufficientStockWarning,
    availableStockInGrams,
    stockDisplay: visibleStockDisplay,
  };
}

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
      (molecule.inventoryLink?.inventoryItemGlobalId === inventoryItemGlobalId ||
        molecule.deletedInventoryLink?.inventoryItemGlobalId ===
          inventoryItemGlobalId),
  );
}

function calculateProductYield(
  {
    actualAmount,
    coefficient,
    molecularWeight,
  }: Pick<EditableMolecule, "actualAmount"> & {
    coefficient: number;
    molecularWeight: number;
  },
  limitingReagentMoles: number,
): number | null {
  if (actualAmount === null || limitingReagentMoles <= 0) {
    return null;
  }

  const theoreticalMoles = limitingReagentMoles * coefficient;
  const theoreticalMass = theoreticalMoles * molecularWeight;

  if (theoreticalMass <= 0) {
    return null;
  }

  return actualAmount / theoreticalMass;
}

function calculateNonLimitingReagentExcess(
  {
    actualAmount,
    coefficient,
    molecularWeight,
  }: Pick<EditableMolecule, "actualAmount"> & {
    coefficient: number;
    molecularWeight: number;
  },
  limitingReagentMoles: number,
): number | null {
  if (actualAmount === null || limitingReagentMoles <= 0) {
    return null;
  }

  const actualMoles = calculateMoles(actualAmount, molecularWeight);

  if (actualMoles === null) {
    return null;
  }

  return actualMoles / coefficient / limitingReagentMoles - 1;
}

function calculateActualYieldOrExcess(
  molecule: EditableMolecule,
  limitingReagentMoles: number,
): number | null {
  if (molecule.coefficient === null || molecule.molecularWeight === null) {
    throw new Error(
      "Cannot calculate yield or excess for molecule with missing coefficient or molecular weight",
    );
  }

  if (molecule.role === "PRODUCT") {
    return calculateProductYield(
      {
        actualAmount: molecule.actualAmount,
        coefficient: molecule.coefficient,
        molecularWeight: molecule.molecularWeight,
      },
      limitingReagentMoles,
    );
  }

  if (
    (molecule.role === "REACTANT" || molecule.role === "AGENT") &&
    !molecule.limitingReagent
  ) {
    return calculateNonLimitingReagentExcess(
      {
        actualAmount: molecule.actualAmount,
        coefficient: molecule.coefficient,
        molecularWeight: molecule.molecularWeight,
      },
      limitingReagentMoles,
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

  if (limitingReagent.coefficient === null) {
    throw new Error("Limiting reagent must have a valid coefficient");
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
  if (!limitingCoefficient) {
    throw new Error("Limiting reagent must have a valid coefficient");
  }

  if (molecules.find((m) => m.coefficient === null)) {
    throw new Error("All molecules must have a valid coefficient");
  }

  return produce(molecules, (draftMolecules) => {
    for (const molecule of draftMolecules) {
      molecule.coefficient = molecule.coefficient as number / limitingCoefficient;
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

  if (molecules.find((m) => m.coefficient === null || m.molecularWeight === null)) {
    throw new Error(
      "All molecules must have valid coefficient and molecular weight to apply mass by ratio",
    );
  }

  return produce(molecules, (draftMolecules) => {
    for (const molecule of draftMolecules) {
      const { coefficient, molecularWeight } = molecule;
      // @ts-expect-error These are pre-checked above
      molecule.mass = coefficient * ratio * molecularWeight;
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
      if (limitingReagent.coefficient === null) {
        throw new Error("Limiting reagent coefficient is undefined");
      }
      const limitingReagentMoles = calculateMoles(
        editedRow.mass,
        limitingReagent.molecularWeight,
      );
      const ratio =
        limitingReagentMoles === null
          ? null
          : 
            limitingReagentMoles / limitingReagent.coefficient;
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
      if (limitingReagent.coefficient === null) {
        throw new Error("Limiting reagent coefficient weight is undefined");
      }
      const limitingReagentMoles = editedRow.moles;
      const ratio =
        limitingReagentMoles === null
          ? null
          : 
            limitingReagentMoles / limitingReagent.coefficient;

      if (beforeMolecule.molecularWeight === null) {
        throw new Error("Molecular weight is undefined");
      }

      return updateYieldAndExcess(
        applyChanges(
          {
            
            mass: editedRow.moles * beforeMolecule.molecularWeight,
          },
          applyMassByRatio(allMolecules, ratio),
        ),
      );
    } else {
      if (beforeMolecule.molecularWeight === null) {
        throw new Error("Molecular weight is undefined");
      }

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
    if (beforeMolecule.molecularWeight === null) {
      throw new Error("Molecular weight is undefined");
    }

    return updateYieldAndExcess(
      applyChanges({
        actualAmount: editedRow.actualMoles * beforeMolecule.molecularWeight,
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
    if (beforeMolecule.coefficient === null || editedRow.coefficient === null) {
      throw new Error("Molecule coefficient is undefined");
    }
    const changeInCoefficient = editedRow.coefficient / beforeMolecule.coefficient;
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
