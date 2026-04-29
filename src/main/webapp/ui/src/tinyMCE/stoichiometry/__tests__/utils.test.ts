import { describe, expect, it } from "vitest";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import {
  calculateMoles,
  calculateUpdatedMolecules,
  getInventoryUpdateEligibility,
  hasDuplicateInventoryLink,
} from "@/tinyMCE/stoichiometry/utils";

const createRsChemElement = (
  id: number,
  smiles: string,
): NonNullable<EditableMolecule["rsChemElement"]> => ({
  id,
  parentId: null,
  ecatChemFileId: null,
  dataImage: null,
  chemElements: smiles,
  smilesString: smiles,
  chemId: null,
  reactionId: null,
  rgroupId: null,
  metadata: null,
  chemElementsFormat: "SMILES",
  creationDate: 1700000000000 + id,
  imageFileProperty: null,
});

const makeBaseMolecules = (): [EditableMolecule, EditableMolecule, EditableMolecule] => {
  const reactantA: EditableMolecule = {
    id: 1,
    rsChemElement: createRsChemElement(11, "A"),
    inventoryLink: null,
    savedInventoryLink: null,
    deletedInventoryLink: null,
    role: "REACTANT",
    formula: "A",
    name: "Reactant A",
    smiles: "A",
    coefficient: 1,
    molecularWeight: 10,
    mass: 10,
    moles: null,
    actualAmount: 10,
    actualMoles: null,
    actualYield: null,
    limitingReagent: true,
    notes: null,
  };

  const reactantB: EditableMolecule = {
    id: 2,
    rsChemElement: createRsChemElement(12, "B"),
    inventoryLink: null,
    savedInventoryLink: null,
    deletedInventoryLink: null,
    role: "REACTANT",
    formula: "B",
    name: "Reactant B",
    smiles: "B",
    coefficient: 2,
    molecularWeight: 20,
    mass: 40,
    moles: null,
    actualAmount: 50,
    actualMoles: null,
    actualYield: null,
    limitingReagent: false,
    notes: null,
  };

  const product: EditableMolecule = {
    id: 3,
    rsChemElement: createRsChemElement(13, "P"),
    inventoryLink: null,
    savedInventoryLink: null,
    deletedInventoryLink: null,
    role: "PRODUCT",
    formula: "P",
    name: "Product P",
    smiles: "P",
    coefficient: 1,
    molecularWeight: 30,
    mass: 30,
    moles: null,
    actualAmount: 15,
    actualMoles: null,
    actualYield: null,
    limitingReagent: false,
    notes: null,
  };

  return [reactantA, reactantB, product];
};

describe("calculateMoles", () => {
  it("calculates moles when mass and molecular weight are valid", () => {
    expect(calculateMoles(20, 10)).toBe(2);
  });

  it("returns null when mass is null", () => {
    expect(calculateMoles(null, 10)).toBeNull();
  });

  it("returns null when molecular weight is null or non-positive", () => {
    expect(calculateMoles(20, null as unknown as number)).toBeNull();
    expect(calculateMoles(20, 0)).toBeNull();
    expect(calculateMoles(20, -1)).toBeNull();
  });
});

describe("getInventoryUpdateEligibility", () => {
  const linkedQuantityInfo = new Map<string, InventoryQuantityQueryResult>([
    [
      "SS101",
      {
        status: "available",
        quantity: {
          numericValue: 10,
          unitId: 7,
        },
      },
    ],
    [
      "SS102",
      {
        status: "available",
        quantity: {
          numericValue: 25,
          unitId: 3,
        },
      },
    ],
    [
      "SS103",
      {
        status: "error",
      },
    ],
    [
      "SS104",
      {
        status: "available",
        quantity: null,
      },
    ],
  ]);

  it("marks molecules without an inventory link as unselectable", () => {
    const [molecule] = makeBaseMolecules();

    expect(getInventoryUpdateEligibility(molecule, linkedQuantityInfo)).toMatchObject({
      disabledReason: "missingInventoryLink",
      helperText: "Link an inventory item before updating stock.",
      showInsufficientStockWarning: false,
      stockDisplay: {
        inStock: { displayValue: "—", unitLabel: null },
        willUse: { displayValue: "—", unitLabel: null },
        remaining: { displayValue: "—", unitLabel: null },
      },
    });
  });

  it("marks molecules with deducted stock as selectable but warns that stock was already deducted", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 100,
      inventoryItemGlobalId: "SS101",
      stockDeducted: true,
    };

    expect(
      getInventoryUpdateEligibility(molecule, linkedQuantityInfo),
    ).toMatchObject({
      disabledReason: null,
      helperText:
        "Stock has already been deducted for this molecule. To reduce the stock again, select this molecule.",
      showInsufficientStockWarning: false,
      stockDisplay: {
        inStock: { displayValue: "10.0", unitLabel: "g" },
        willUse: { displayValue: "10.0", unitLabel: "g" },
        remaining: { displayValue: "0.0", unitLabel: "g" },
        remainingStatus: "zero",
        warningText: null,
      },
    });
  });

  it("marks molecules with unavailable linked stock as unselectable", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 101,
      inventoryItemGlobalId: "SS103",
    };

    expect(getInventoryUpdateEligibility(molecule, linkedQuantityInfo)).toMatchObject({
      disabledReason: "linkedStockUnavailable",
      helperText:
        "Linked stock information is unavailable, so this molecule cannot be updated.",
      showInsufficientStockWarning: false,
      stockDisplay: {
        inStock: { displayValue: "—", unitLabel: null },
        willUse: { displayValue: "—", unitLabel: null },
        remaining: { displayValue: "—", unitLabel: null },
      },
    });
  });

  it("marks molecules with non-mass linked quantities as unselectable", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 102,
      inventoryItemGlobalId: "SS102",
    };

    expect(
      getInventoryUpdateEligibility(molecule, linkedQuantityInfo),
    ).toMatchObject({
      disabledReason: "nonMassInventoryQuantity",
      helperText:
        "Inventory stock updates are currently only supported for item quantities expressed in mass (e.g. grams). Volumetric quantities (e.g. mL) are not yet supported.",
      showInsufficientStockWarning: false,
      stockDisplay: {
        inStock: { displayValue: "25.0", unitLabel: "mL" },
        willUse: { displayValue: "—", unitLabel: "mL" },
        remaining: { displayValue: "—", unitLabel: "mL" },
        remainingStatus: "default",
        warningText: null,
      },
    });
  });

  it("marks molecules without actual mass as unselectable", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 102,
      inventoryItemGlobalId: "SS101",
    };
    molecule.actualAmount = null;

    expect(getInventoryUpdateEligibility(molecule, linkedQuantityInfo)).toMatchObject({
      disabledReason: "missingActualMass",
      helperText: "Define actual mass before updating linked inventory stock.",
      showInsufficientStockWarning: false,
      stockDisplay: {
        inStock: { displayValue: "10.0", unitLabel: "g" },
        willUse: { displayValue: "—", unitLabel: "g" },
        remaining: { displayValue: "—", unitLabel: "g" },
        remainingStatus: "default",
        warningText: null,
      },
    });
  });

  it("marks molecules with insufficient stock as unselectable and exposes stock metrics in the inventory unit", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 103,
      inventoryItemGlobalId: "SS101",
    };
    molecule.actualAmount = 11;

    expect(getInventoryUpdateEligibility(molecule, linkedQuantityInfo)).toMatchObject({
      disabledReason: "insufficientStock",
      helperText: null,
      showInsufficientStockWarning: true,
      stockDisplay: {
        inStock: { displayValue: "10.0", unitLabel: "g" },
        willUse: { displayValue: "11.0", unitLabel: "g" },
        remaining: { displayValue: "-1.0", unitLabel: "g" },
        remainingStatus: "negative",
        warningText: "Insufficient Stock",
      },
    });
  });

  it("allows molecules with available mass stock to remain selectable", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 104,
      inventoryItemGlobalId: "SS101",
    };
    molecule.actualAmount = 5;

    expect(getInventoryUpdateEligibility(molecule, linkedQuantityInfo)).toMatchObject({
      disabledReason: null,
      helperText: null,
      showInsufficientStockWarning: false,
      stockDisplay: {
        inStock: { displayValue: "10.0", unitLabel: "g" },
        willUse: { displayValue: "5.0", unitLabel: "g" },
        remaining: { displayValue: "5.0", unitLabel: "g" },
        remainingStatus: "positive",
        warningText: null,
      },
    });
  });

  it("harmonises stock display into the inventory mass unit", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 106,
      inventoryItemGlobalId: "SS105",
    };
    molecule.actualAmount = 0.5;

    const result = getInventoryUpdateEligibility(
      molecule,
      new Map<string, InventoryQuantityQueryResult>([
        [
          "SS105",
          {
            status: "available",
            quantity: {
              numericValue: 2000,
              unitId: 6,
            },
          },
        ],
      ]),
    );

    expect(result.stockDisplay).toMatchObject({
      inStock: { displayValue: "2,000.0", unitLabel: "mg" },
      willUse: { displayValue: "500.0", unitLabel: "mg" },
      remaining: { displayValue: "1,500.0", unitLabel: "mg" },
      remainingStatus: "positive",
      warningText: null,
    });
  });

  it("highlights exact depletion with a zero remaining status", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 107,
      inventoryItemGlobalId: "SS101",
    };

    const result = getInventoryUpdateEligibility(molecule, linkedQuantityInfo);

    expect(result.stockDisplay).toMatchObject({
      remaining: { displayValue: "0.0", unitLabel: "g" },
      remainingStatus: "zero",
      warningText: null,
    });
  });

  it("treats missing quantity data as unavailable linked stock", () => {
    const [molecule] = makeBaseMolecules();
    molecule.inventoryLink = {
      id: 105,
      inventoryItemGlobalId: "SS104",
    };

    expect(getInventoryUpdateEligibility(molecule, linkedQuantityInfo)).toMatchObject({
      disabledReason: "linkedStockUnavailable",
      helperText:
        "Linked stock information is unavailable, so this molecule cannot be updated.",
      showInsufficientStockWarning: false,
      stockDisplay: {
        inStock: { displayValue: "—", unitLabel: null },
        willUse: { displayValue: "—", unitLabel: null },
        remaining: { displayValue: "—", unitLabel: null },
      },
    });
  });
});

describe("calculateUpdatedMolecules", () => {
  it("returns the original array when no supported fields changed", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1] };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result).toBe(molecules);
  });

  it("updates notes and recalculates yield/excess", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], notes: "updated note" };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[1].notes).toBe("updated note");
    expect(result[1].actualYield).toBeCloseTo(0.25);
    expect(result[2].actualYield).toBeCloseTo(0.5);
    expect(result[0].actualYield).toBeNull();
  });

  it("updates non-limiting reagent mass without changing other masses", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], mass: 60 };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[1].mass).toBe(60);
    expect(result[0].mass).toBe(10);
    expect(result[2].mass).toBe(30);
  });

  it("updates all masses when limiting reagent mass changes", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[0], mass: 20 };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[0].mass).toBeCloseTo(20);
    expect(result[1].mass).toBeCloseTo(80);
    expect(result[2].mass).toBeCloseTo(60);
  });

  it("keeps other masses unchanged when limiting reagent mass is set to null", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[0], mass: null };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[0].mass).toBeNull();
    expect(result[1].mass).toBe(40);
    expect(result[2].mass).toBe(30);
    expect(result[1].actualYield).toBeCloseTo(0.25);
    expect(result[2].actualYield).toBeCloseTo(0.5);
  });

  it("updates non-limiting reagent mass when moles are edited", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], moles: 4 };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[1].mass).toBe(80);
    expect(result[0].mass).toBe(10);
    expect(result[2].mass).toBe(30);
  });

  it("updates all masses when limiting reagent moles are edited", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[0], moles: 3 };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[0].mass).toBeCloseTo(30);
    expect(result[1].mass).toBeCloseTo(120);
    expect(result[2].mass).toBeCloseTo(90);
  });

  it("updates actual amount and recalculates yield/excess", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], actualAmount: 60 };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[1].actualAmount).toBe(60);
    expect(result[1].actualYield).toBeCloseTo(0.5);
    expect(result[2].actualYield).toBeCloseTo(0.5);
  });

  it("clears yield or excess for a molecule when its actual amount is set to null", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], actualAmount: null };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[1].actualAmount).toBeNull();
    expect(result[1].actualYield).toBeNull();
    expect(result[2].actualYield).toBeCloseTo(0.5);
  });

  it("leaves all yields null when the limiting reagent actual amount is set to null", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[0], actualAmount: null };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[0].actualAmount).toBeNull();
    expect(result[0].actualYield).toBeNull();
    expect(result[1].actualYield).toBeNull();
    expect(result[2].actualYield).toBeNull();
  });

  it("updates actual amount from actual moles and recalculates yield/excess", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], actualMoles: 4 };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[1].actualAmount).toBe(80);
    expect(result[1].actualYield).toBeCloseTo(1);
  });

  it("normalises coefficients when limiting reagent changes", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], limitingReagent: true };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[0].limitingReagent).toBe(false);
    expect(result[1].limitingReagent).toBe(true);
    expect(result[2].limitingReagent).toBe(false);
    expect(result[0].coefficient).toBeCloseTo(0.5);
    expect(result[1].coefficient).toBeCloseTo(1);
    expect(result[2].coefficient).toBeCloseTo(0.5);
  });

  it("updates the role when role changes are explicitly allowed", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[2], role: "AGENT" as const };

    const result = calculateUpdatedMolecules(molecules, editedRow, {
      allowRoleChange: true,
    });

    expect(result[2].role).toBe("AGENT");
    expect(result[2].limitingReagent).toBe(false);
  });

  it("clears the limiting reagent flag and dependent yield values when a limiting reactant changes role", () => {
    const molecules = makeBaseMolecules();
    const seededMolecules = calculateUpdatedMolecules(molecules, {
      ...molecules[1],
      notes: "seed yield calculations",
    });

    const result = calculateUpdatedMolecules(
      seededMolecules,
      { ...seededMolecules[0], role: "PRODUCT" as const },
      { allowRoleChange: true },
    );

    expect(result[0].role).toBe("PRODUCT");
    expect(result[0].limitingReagent).toBe(false);
    expect(result[0].actualYield).toBeNull();
    expect(result[1].actualYield).toBeNull();
    expect(result[2].actualYield).toBeNull();
  });

  it("scales edited coefficient and mass", () => {
    const molecules = makeBaseMolecules();
    const editedRow = { ...molecules[1], coefficient: 4 };

    const result = calculateUpdatedMolecules(molecules, editedRow);

    expect(result[1].coefficient).toBe(4);
    expect(result[1].mass).toBe(80);
    expect(result[1].actualYield).toBeCloseTo(-0.375);
  });

  it("throws when intrinsic molecule properties are modified", () => {
    const molecules = makeBaseMolecules();
    const cases: Array<{
      edited: EditableMolecule;
      message: string;
    }> = [
      {
        edited: { ...molecules[0], name: "Renamed" },
        message:
          "Name is an intrinsic property of the chemical and cannot be modified",
      },
      {
        edited: { ...molecules[0], molecularWeight: 11 },
        message:
          "Molecular weight is an intrinsic property of the chemical and cannot be modified",
      },
      {
        edited: { ...molecules[0], formula: "A2" },
        message:
          "Chemical formula is an intrinsic property of the chemical and cannot be modified",
      },
      {
        edited: { ...molecules[0], smiles: "AX" },
        message:
          "The SMILES representation is an intrinsic property of the chemical and cannot be modified",
      },
      {
        edited: {
          ...molecules[0],
          rsChemElement: {
            ...(molecules[0].rsChemElement as NonNullable<
              EditableMolecule["rsChemElement"]
            >),
          },
        },
        message: "Modifying the rsChemElement of a molecule is not supported",
      },
    ];

    for (const entry of cases) {
      expect(() => calculateUpdatedMolecules(molecules, entry.edited)).toThrow(
        entry.message,
      );
    }
  });

  it("throws when role changes are not allowed", () => {
    const molecules = makeBaseMolecules();

    expect(() =>
      calculateUpdatedMolecules(molecules, { ...molecules[0], role: "PRODUCT" }),
    ).toThrow("Modifying the role of a molecule is not supported");
  });

  it("throws when no limiting reagent exists and coefficient is edited", () => {
    const molecules = makeBaseMolecules().map((m) => ({
      ...m,
      limitingReagent: false,
    })) as [EditableMolecule, EditableMolecule, EditableMolecule];
    const editedRow = { ...molecules[1], coefficient: 4 };

    expect(() => calculateUpdatedMolecules(molecules, editedRow)).toThrow(
      "No limiting reagent found after update",
    );
  });

  it("throws when no limiting reagent exists and limiting mass update is attempted", () => {
    const molecules = makeBaseMolecules().map((m) => ({
      ...m,
      limitingReagent: false,
    })) as [EditableMolecule, EditableMolecule, EditableMolecule];
    const editedRow = { ...molecules[0], limitingReagent: true, mass: 20 };

    expect(() => calculateUpdatedMolecules(molecules, editedRow)).toThrow(
      "No limiting reagent defined",
    );
  });

  it("throws when moles are edited for a molecule with null molecular weight", () => {
    const molecules = makeBaseMolecules();
    molecules[1] = { ...molecules[1], molecularWeight: null };
    const editedRow = { ...molecules[1], moles: 4 };

    expect(() => calculateUpdatedMolecules(molecules, editedRow)).toThrow(
      "Molecular weight is undefined",
    );
  });

  it("throws when actual moles are edited for a molecule with null molecular weight", () => {
    const molecules = makeBaseMolecules();
    molecules[1] = { ...molecules[1], molecularWeight: null };
    const editedRow = { ...molecules[1], actualMoles: 4 };

    expect(() => calculateUpdatedMolecules(molecules, editedRow)).toThrow(
      "Molecular weight is undefined",
    );
  });

  it("throws when the edited coefficient was previously null", () => {
    const molecules = makeBaseMolecules();
    molecules[1] = { ...molecules[1], coefficient: null };
    const editedRow = { ...molecules[1], coefficient: 4 };

    expect(() => calculateUpdatedMolecules(molecules, editedRow)).toThrow(
      "Molecule coefficient is undefined",
    );
  });

  it("throws when changing limiting reagent and any molecule coefficient is null", () => {
    const molecules = makeBaseMolecules();
    molecules[2] = { ...molecules[2], coefficient: null };
    const editedRow = { ...molecules[1], limitingReagent: true };

    expect(() => calculateUpdatedMolecules(molecules, editedRow)).toThrow(
      "All molecules must have a valid coefficient",
    );
  });
});

describe("hasDuplicateInventoryLink", () => {
  it("returns true when the same inventory item is already linked by another row", () => {
    const molecules = makeBaseMolecules();
    molecules[0].inventoryLink = {
      id: 1,
      inventoryItemGlobalId: "SA123",
      stoichiometryMoleculeId: molecules[0].id,
      quantity: { numericValue: 1, unitId: 1 },
    };

    expect(hasDuplicateInventoryLink(molecules, molecules[1].id, "SA123")).toBe(
      true,
    );
  });

  it("returns false when the link belongs to the same row", () => {
    const molecules = makeBaseMolecules();
    molecules[0].inventoryLink = {
      id: 1,
      inventoryItemGlobalId: "SA123",
      stoichiometryMoleculeId: molecules[0].id,
      quantity: { numericValue: 1, unitId: 1 },
    };

    expect(hasDuplicateInventoryLink(molecules, molecules[0].id, "SA123")).toBe(
      false,
    );
  });

  it("returns false when the inventory item has not been linked yet", () => {
    const molecules = makeBaseMolecules();

    expect(hasDuplicateInventoryLink(molecules, molecules[0].id, "SA999")).toBe(
      false,
    );
  });
});
