import { describe, expect, it } from "vitest";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import {
  calculateMoles,
  calculateUpdatedMolecules,
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
        edited: { ...molecules[0], role: "PRODUCT" },
        message: "Modifying the role of a molecule is not supported",
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
