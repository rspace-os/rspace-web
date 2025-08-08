/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import {
  calculateUpdatedMolecules,
  calculateActualMoles,
  calculateYield,
} from "../calculations";
import { type StoichiometryMolecule } from "../../../hooks/api/useStoichiometry";

// Test data factories
const createMockMolecule = (
  overrides: Partial<StoichiometryMolecule> = {},
): StoichiometryMolecule => ({
  id: 1,
  rsChemElement: {
    id: 1,
    parentId: null,
    ecatChemFileId: null,
    dataImage: null,
    chemElements: "",
    smilesString: null,
    chemId: null,
    reactionId: null,
    rgroupId: null,
    metadata: null,
    chemElementsFormat: "",
    creationDate: Date.now(),
    imageFileProperty: null,
  },
  role: "reactant",
  formula: "H2O",
  name: "Water",
  smiles: "O",
  coefficient: 1,
  molecularWeight: 18.015,
  mass: null,
  moles: null,
  expectedAmount: null,
  actualAmount: null,
  actualYield: null,
  limitingReagent: false,
  notes: null,
  ...overrides,
});

const createMockReaction = (): ReadonlyArray<StoichiometryMolecule> => [
  createMockMolecule({
    id: 1,
    name: "Reactant A",
    role: "reactant",
    molecularWeight: 100,
    coefficient: 2,
    mass: 200,
    moles: 2,
  }),
  createMockMolecule({
    id: 2,
    name: "Reactant B",
    role: "reactant",
    molecularWeight: 50,
    coefficient: 1,
    mass: 50,
    moles: 1,
  }),
  createMockMolecule({
    id: 3,
    name: "Product C",
    role: "product",
    molecularWeight: 150,
    coefficient: 1,
    mass: null,
    moles: null,
  }),
];

describe("calculateUpdatedMolecules", () => {
  describe("basic functionality", () => {
    test("returns unchanged molecules when edited molecule is not found", () => {
      const allMolecules = createMockReaction();
      const editedRow = createMockMolecule({ id: 999, name: "Non-existent" });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      expect(result).toEqual(allMolecules);
    });

    test("creates a new array without mutating the original", () => {
      const allMolecules = createMockReaction();
      const editedRow = createMockMolecule({
        id: 1,
        mass: 300,
        notes: "Updated note",
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      expect(result).not.toBe(allMolecules);
      expect(allMolecules[0].mass).toBe(200); // Original unchanged
      expect(result[0].mass).toBe(300); // New value applied
    });
  });

  describe("property updates", () => {
    test("updates mass, moles, notes, coefficient, and limitingReagent", () => {
      const allMolecules = createMockReaction();
      const editedRow = createMockMolecule({
        id: 1,
        mass: 300,
        moles: 3,
        notes: "Test note",
        coefficient: 3,
        limitingReagent: true,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      // When a molecule becomes the limiting reagent, its coefficient gets scaled to 1
      expect(updatedMolecule).toMatchObject({
        mass: 300,
        moles: 3,
        notes: "Test note",
        coefficient: 1, // Scaled from 3 to 1 since it's the limiting reagent
        limitingReagent: true,
      });
    });
  });

  describe("mass-mole conversions", () => {
    test("calculates moles from mass when mass is changed", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          molecularWeight: 100,
          mass: 200,
          moles: 2,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        molecularWeight: 100,
        mass: 300, // Changed mass
        moles: 2, // Original moles
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.moles).toBe(3); // 300 / 100 = 3
    });

    test("calculates mass from moles when moles is changed", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          molecularWeight: 100,
          mass: 200,
          moles: 2,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        molecularWeight: 100,
        mass: 200, // Original mass
        moles: 3, // Changed moles
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.mass).toBe(300); // 3 * 100 = 300
    });

    test("does not perform conversion when molecular weight is zero or null", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          molecularWeight: 0,
          mass: 200,
          moles: 2,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        molecularWeight: 0,
        mass: 300,
        moles: 2,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.moles).toBe(2); // Should remain unchanged
    });

    test("does not perform conversion when mass is zero or null", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          molecularWeight: 100,
          mass: 200,
          moles: 2,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        molecularWeight: 100,
        mass: 0, // Zero mass
        moles: 2,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.moles).toBe(2); // Should remain unchanged
    });

    test("does not perform conversion when moles is zero or null", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          molecularWeight: 100,
          mass: 200,
          moles: 2,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        molecularWeight: 100,
        mass: 200,
        moles: 0, // Zero moles
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.mass).toBe(200); // Should remain unchanged
    });

    test("rounds moles calculation to 6 decimal places", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          molecularWeight: 3,
          mass: 1,
          moles: 0.333333,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        molecularWeight: 3,
        mass: 1,
        moles: 0.333333,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.moles).toBe(0.333333); // 1 / 3 rounded to 6 decimal places
    });

    test("rounds mass calculation to 4 decimal places", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          molecularWeight: 3,
          mass: 1,
          moles: 0.333333,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        molecularWeight: 3,
        mass: 1,
        moles: 0.333333,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.mass).toBe(1.0); // 0.333333 * 3 rounded to 4 decimal places
    });
  });

  describe("limiting reagent selection", () => {
    test("sets limitingReagent to false for other reactants when one is selected", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 3,
          role: "product",
          limitingReagent: false,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 2,
        role: "reactant",
        limitingReagent: true,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      expect(result.find((m) => m.id === 1)?.limitingReagent).toBe(false);
      expect(result.find((m) => m.id === 2)?.limitingReagent).toBe(true);
      expect(result.find((m) => m.id === 3)?.limitingReagent).toBe(false); // Product unchanged
    });

    test("does not affect non-reactant molecules when limiting reagent is selected", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 2,
          role: "product",
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 3,
          role: "catalyst",
          limitingReagent: false,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        role: "reactant",
        limitingReagent: true,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      expect(result.find((m) => m.id === 1)?.limitingReagent).toBe(true);
      expect(result.find((m) => m.id === 2)?.limitingReagent).toBe(false);
      expect(result.find((m) => m.id === 3)?.limitingReagent).toBe(false);
    });
  });

  describe("stoichiometric calculations", () => {
    test("calculates theoretical amounts for all molecules based on limiting reagent", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 2,
          moles: 4,
          molecularWeight: 100,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 1,
          moles: 5,
          molecularWeight: 50,
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 3,
          role: "product",
          coefficient: 3,
          moles: 0,
          molecularWeight: 150,
          limitingReagent: false,
        }),
      ];
      const editedRow = allMolecules[0]; // No changes, just trigger calculation

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Limiting reagent (id: 1): 4 moles, coefficient 2
      // Ratio: 4/2 = 2

      // Reactant B (id: 2): coefficient 1, should be 2 * 1 = 2 moles
      expect(result.find((m) => m.id === 2)?.moles).toBe(2);
      expect(result.find((m) => m.id === 2)?.mass).toBe(100); // 2 * 50 = 100

      // Product C (id: 3): coefficient 3, should be 2 * 3 = 6 moles
      expect(result.find((m) => m.id === 3)?.moles).toBe(6);
      expect(result.find((m) => m.id === 3)?.mass).toBe(900); // 6 * 150 = 900
    });

    test("does not calculate stoichiometry when no limiting reagent is selected", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 2,
          moles: 4,
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 1,
          moles: 5,
          limitingReagent: false,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Values should remain unchanged
      expect(result.find((m) => m.id === 1)?.moles).toBe(4);
      expect(result.find((m) => m.id === 2)?.moles).toBe(5);
    });

    test("does not calculate stoichiometry when limiting reagent has zero moles", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 2,
          moles: 0,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 1,
          moles: 5,
          limitingReagent: false,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Second reactant should remain unchanged since limiting reagent has 0 moles
      expect(result.find((m) => m.id === 2)?.moles).toBe(5);
    });

    test("does not calculate stoichiometry when limiting reagent is not a reactant", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "product",
          coefficient: 2,
          moles: 4,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 1,
          moles: 5,
          limitingReagent: false,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Reactant should remain unchanged since limiting reagent is not a reactant
      expect(result.find((m) => m.id === 2)?.moles).toBe(5);
    });

    test("uses default coefficient of 1 when coefficient is not set", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 1,
          moles: 2,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "product",
          coefficient: 0, // Should default to 1
          moles: 0,
          molecularWeight: 100,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Product should have 2 moles (2/1 * 1 = 2)
      expect(result.find((m) => m.id === 2)?.moles).toBe(2);
    });

    test("does not calculate mass when molecular weight is not available", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 1,
          moles: 2,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "product",
          coefficient: 1,
          moles: 0,
          molecularWeight: 0,
          mass: 50,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Moles should be calculated but mass should remain unchanged
      expect(result.find((m) => m.id === 2)?.moles).toBe(2);
      expect(result.find((m) => m.id === 2)?.mass).toBe(50); // Original mass preserved
    });

    test("rounds stoichiometric calculations to proper decimal places", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 3,
          moles: 1,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "product",
          coefficient: 1,
          moles: 0,
          molecularWeight: 3,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Theoretical moles: (1/3) * 1 = 0.333333...
      expect(result.find((m) => m.id === 2)?.moles).toBe(0.333333); // Rounded to 6 decimals
      expect(result.find((m) => m.id === 2)?.mass).toBe(1.0); // Rounded to 4 decimals
    });
  });

  describe("complex scenarios", () => {
    test("handles mass change that triggers limiting reagent calculations", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 1,
          molecularWeight: 100,
          mass: 100,
          moles: 1,
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 2,
          molecularWeight: 50,
          mass: 200,
          moles: 4,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 3,
          role: "product",
          coefficient: 1,
          molecularWeight: 150,
          mass: null,
          moles: null,
        }),
      ];

      // Change mass of limiting reagent
      const editedRow = createMockMolecule({
        id: 2,
        role: "reactant",
        coefficient: 2,
        molecularWeight: 50,
        mass: 100, // Changed from 200 to 100
        moles: 4, // Original moles
        limitingReagent: true,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // First, mass should be converted to moles: 100/50 = 2 moles
      const limitingReagent = result.find((m) => m.id === 2);
      expect(limitingReagent?.moles).toBe(2);

      // Then stoichiometric calculations: 2/2 = 1 ratio
      // Reactant 1: 1 * 1 = 1 mole
      expect(result.find((m) => m.id === 1)?.moles).toBe(1);
      expect(result.find((m) => m.id === 1)?.mass).toBe(100); // 1 * 100

      // Product: 1 * 1 = 1 mole
      expect(result.find((m) => m.id === 3)?.moles).toBe(1);
      expect(result.find((m) => m.id === 3)?.mass).toBe(150); // 1 * 150
    });

    test("handles changing limiting reagent selection with subsequent calculations", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 1,
          moles: 10,
          molecularWeight: 100,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 2,
          moles: 5,
          molecularWeight: 50,
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 3,
          role: "product",
          coefficient: 1,
          moles: 0,
          molecularWeight: 150,
        }),
      ];

      // Change limiting reagent to second reactant
      const editedRow = createMockMolecule({
        id: 2,
        role: "reactant",
        coefficient: 2,
        moles: 5,
        molecularWeight: 50,
        limitingReagent: true,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Old limiting reagent should be false
      expect(result.find((m) => m.id === 1)?.limitingReagent).toBe(false);

      // New limiting reagent should be true
      expect(result.find((m) => m.id === 2)?.limitingReagent).toBe(true);

      // Stoichiometric calculations based on new limiting reagent (5 moles, coefficient 2)
      // Ratio: 5/2 = 2.5
      expect(result.find((m) => m.id === 1)?.moles).toBe(2.5); // 2.5 * 1
      expect(result.find((m) => m.id === 3)?.moles).toBe(2.5); // 2.5 * 1
    });

    test("scales all equivalent values when limiting reagent changes to ensure new limiting reagent has coefficient of 1", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 2,
          moles: 4,
          molecularWeight: 100,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 3,
          moles: 6,
          molecularWeight: 50,
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 3,
          role: "product",
          coefficient: 4,
          moles: 0,
          molecularWeight: 150,
        }),
      ];

      // Change limiting reagent to second reactant (coefficient 3)
      const editedRow = createMockMolecule({
        id: 2,
        role: "reactant",
        coefficient: 3,
        moles: 6,
        molecularWeight: 50,
        limitingReagent: true,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // All coefficients should be scaled by 1/3 so the new limiting reagent has coefficient of 1
      expect(result.find((m) => m.id === 1)?.coefficient).toBe(0.666667); // 2 * (1/3) = 2/3 rounded to 6 decimals
      expect(result.find((m) => m.id === 2)?.coefficient).toBe(1); // 3 * (1/3) = 1
      expect(result.find((m) => m.id === 3)?.coefficient).toBe(1.333333); // 4 * (1/3) = 4/3 rounded to 6 decimals
    });

    test("does not scale coefficients when new limiting reagent already has coefficient of 1", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 2,
          moles: 4,
          molecularWeight: 100,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          coefficient: 1, // Already coefficient of 1
          moles: 2,
          molecularWeight: 50,
          limitingReagent: false,
        }),
        createMockMolecule({
          id: 3,
          role: "product",
          coefficient: 3,
          moles: 0,
          molecularWeight: 150,
        }),
      ];

      // Change limiting reagent to second reactant (coefficient 1)
      const editedRow = createMockMolecule({
        id: 2,
        role: "reactant",
        coefficient: 1,
        moles: 2,
        molecularWeight: 50,
        limitingReagent: true,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Coefficients should remain unchanged since new limiting reagent already has coefficient of 1
      expect(result.find((m) => m.id === 1)?.coefficient).toBe(2);
      expect(result.find((m) => m.id === 2)?.coefficient).toBe(1);
      expect(result.find((m) => m.id === 3)?.coefficient).toBe(3);
    });
  });
});

describe("calculateActualMoles", () => {
  describe("valid inputs", () => {
    test("calculates actual moles from actual amount and molecular weight", () => {
      const result = calculateActualMoles({ actualAmount: 100, molecularWeight: 50 }); // 100g / 50 g/mol
      expect(result).toBe(2);
    });

    test("handles decimal inputs correctly", () => {
      const result = calculateActualMoles({ actualAmount: 18.015, molecularWeight: 18.015 }); // Water: 18.015g / 18.015 g/mol
      expect(result).toBe(1);
    });

    test("rounds result to 6 decimal places", () => {
      const result = calculateActualMoles({ actualAmount: 1, molecularWeight: 3 }); // 1g / 3 g/mol = 0.333333...
      expect(result).toBe(0.333333);
    });

    test("handles very small numbers", () => {
      const result = calculateActualMoles({ actualAmount: 0.001, molecularWeight: 100 });
      expect(result).toBe(0.00001); // 0.001 / 100 = 0.00001
    });

    test("handles very large numbers", () => {
      const result = calculateActualMoles({ actualAmount: 10000, molecularWeight: 1 });
      expect(result).toBe(10000); // 10000 / 1 = 10000
    });
  });

  describe("invalid inputs", () => {
    test("returns null when actualAmount is null", () => {
      const result = calculateActualMoles({ actualAmount: null, molecularWeight: 50 });
      expect(result).toBeNull();
    });

    test("returns null when molecularWeight is null", () => {
      const result = calculateActualMoles({ actualAmount: 100, molecularWeight: null });
      expect(result).toBeNull();
    });

    test("returns null when both are null", () => {
      const result = calculateActualMoles({ actualAmount: null, molecularWeight: null });
      expect(result).toBeNull();
    });

    test("returns null when actualAmount is zero", () => {
      const result = calculateActualMoles({ actualAmount: 0, molecularWeight: 50 });
      expect(result).toBeNull();
    });

    test("returns null when molecularWeight is zero", () => {
      const result = calculateActualMoles({ actualAmount: 100, molecularWeight: 0 });
      expect(result).toBeNull();
    });

    test("returns null when actualAmount is negative", () => {
      const result = calculateActualMoles({ actualAmount: -100, molecularWeight: 50 });
      expect(result).toBeNull();
    });

    test("returns null when molecularWeight is negative", () => {
      const result = calculateActualMoles({ actualAmount: 100, molecularWeight: -50 });
      expect(result).toBeNull();
    });
  });
});

describe("calculateYield", () => {
  describe("valid inputs", () => {
    test("calculates perfect yield (100%)", () => {
      const result = calculateYield({ actualAmount: 50, theoreticalMass: 50 }); // actual = theoretical
      expect(result).toBe(100);
    });

    test("calculates partial yield", () => {
      const result = calculateYield({ actualAmount: 75, theoreticalMass: 100 }); // 75% yield
      expect(result).toBe(75);
    });

    test("calculates low yield", () => {
      const result = calculateYield({ actualAmount: 25, theoreticalMass: 100 }); // 25% yield
      expect(result).toBe(25);
    });

    test("handles over 100% yield (theoretical possibility in some reactions)", () => {
      const result = calculateYield({ actualAmount: 120, theoreticalMass: 100 }); // 120% yield
      expect(result).toBe(120);
    });

    test("rounds result to 2 decimal places", () => {
      const result = calculateYield({ actualAmount: 33.333, theoreticalMass: 100 });
      expect(result).toBe(33.33);
    });

    test("handles decimal inputs correctly", () => {
      const result = calculateYield({ actualAmount: 18.015, theoreticalMass: 36.03 }); // 50% yield
      expect(result).toBe(50);
    });

    test("handles very small numbers", () => {
      const result = calculateYield({ actualAmount: 0.001, theoreticalMass: 0.1 }); // 1% yield
      expect(result).toBe(1);
    });

    test("handles very large numbers", () => {
      const result = calculateYield({ actualAmount: 5000, theoreticalMass: 10000 }); // 50% yield
      expect(result).toBe(50);
    });

    test("calculates precise fractional yields", () => {
      const result = calculateYield({ actualAmount: 1, theoreticalMass: 3 }); // 1/3 = 33.333...%
      expect(result).toBe(33.33);
    });
  });

  describe("invalid inputs", () => {
    test("returns null when actualAmount is null", () => {
      const result = calculateYield({ actualAmount: null, theoreticalMass: 100 });
      expect(result).toBeNull();
    });

    test("returns null when theoreticalMass is null", () => {
      const result = calculateYield({ actualAmount: 50, theoreticalMass: null });
      expect(result).toBeNull();
    });

    test("returns null when both are null", () => {
      const result = calculateYield({ actualAmount: null, theoreticalMass: null });
      expect(result).toBeNull();
    });

    test("returns null when actualAmount is zero", () => {
      const result = calculateYield({ actualAmount: 0, theoreticalMass: 100 });
      expect(result).toBeNull();
    });

    test("returns null when theoreticalMass is zero", () => {
      const result = calculateYield({ actualAmount: 50, theoreticalMass: 0 });
      expect(result).toBeNull();
    });

    test("returns null when actualAmount is negative", () => {
      const result = calculateYield({ actualAmount: -50, theoreticalMass: 100 });
      expect(result).toBeNull();
    });

    test("returns null when theoreticalMass is negative", () => {
      const result = calculateYield({ actualAmount: 50, theoreticalMass: -100 });
      expect(result).toBeNull();
    });
  });
});

describe("calculateUpdatedMolecules - yield calculations", () => {
  describe("actualAmount updates", () => {
    test("updates actualAmount property", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          actualAmount: null,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        actualAmount: 75,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.actualAmount).toBe(75);
    });
  });

  describe("yield calculations", () => {
    test("calculates yield when both actualAmount and theoretical mass are present and molecule is not limiting reagent", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "product", // Not a limiting reagent (product)
          mass: 100, // theoretical mass
          actualAmount: 75, // actual amount
          actualYield: null,
          limitingReagent: false,
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        role: "product",
        mass: 100,
        actualAmount: 75,
        limitingReagent: false,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.actualYield).toBe(75); // 75/100 * 100 = 75%
    });

    test("sets yield to null when actualAmount is missing", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          mass: 100,
          actualAmount: null,
          actualYield: 50, // Previous value
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        mass: 100,
        actualAmount: null,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.actualYield).toBeNull();
    });

    test("sets yield to null when theoretical mass is missing", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          mass: null,
          actualAmount: 75,
          actualYield: 50, // Previous value
        }),
      ];
      const editedRow = createMockMolecule({
        id: 1,
        mass: null,
        actualAmount: 75,
      });

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.actualYield).toBeNull();
    });

    test("calculates yield for multiple molecules", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          mass: 100,
          actualAmount: 80,
        }),
        createMockMolecule({
          id: 2,
          mass: 200,
          actualAmount: 150,
        }),
        createMockMolecule({
          id: 3,
          mass: 50,
          actualAmount: null, // No actual amount
        }),
      ];
      const editedRow = allMolecules[0]; // Trigger calculation

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      expect(result.find((m) => m.id === 1)?.actualYield).toBe(80); // 80%
      expect(result.find((m) => m.id === 2)?.actualYield).toBe(75); // 75%
      expect(result.find((m) => m.id === 3)?.actualYield).toBeNull(); // No actual amount
    });

    test("recalculates yield after stoichiometric calculations update theoretical mass", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          coefficient: 1,
          moles: 2,
          molecularWeight: 100,
          mass: 200,
          limitingReagent: true,
        }),
        createMockMolecule({
          id: 2,
          role: "product",
          coefficient: 1,
          moles: 0,
          molecularWeight: 150,
          mass: null,
          actualAmount: 225, // Actual amount known
        }),
      ];
      const editedRow = allMolecules[0]; // Trigger stoichiometric calculation

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const product = result.find((m) => m.id === 2);

      // Stoichiometric calculation should set theoretical mass to 2 * 150 = 300
      expect(product?.mass).toBe(300);
      // Yield should be calculated as 225/300 * 100 = 75%
      expect(product?.actualYield).toBe(75);
    });

    test("handles edge case with zero theoretical mass", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          mass: 0,
          actualAmount: 75,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.actualYield).toBeNull();
    });

    test("handles edge case with zero actual amount", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          mass: 100,
          actualAmount: 0,
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.actualYield).toBeNull();
    });

    test("rounds yield to 2 decimal places", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          mass: 3,
          actualAmount: 1, // 1/3 = 33.333...%
        }),
      ];
      const editedRow = allMolecules[0];

      const result = calculateUpdatedMolecules(allMolecules, editedRow);
      const updatedMolecule = result.find((m) => m.id === 1);

      expect(updatedMolecule?.actualYield).toBe(33.33);
    });

    test("does not calculate yield for limiting reagent even with actualAmount and mass", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          mass: 100,
          actualAmount: 75,
          limitingReagent: true, // This is the limiting reagent
        }),
        createMockMolecule({
          id: 2,
          role: "product",
          mass: 150,
          actualAmount: 100,
          limitingReagent: false,
        }),
      ];
      const editedRow = allMolecules[0]; // Trigger calculation

      const result = calculateUpdatedMolecules(allMolecules, editedRow);

      // Limiting reagent should not have yield calculated
      expect(result.find((m) => m.id === 1)?.actualYield).toBeNull();
      // Product should have yield calculated
      expect(result.find((m) => m.id === 2)?.actualYield).toBe(66.67); // 100/150 * 100 = 66.67%
    });

    test("switches yield calculation when limiting reagent changes", () => {
      const allMolecules = [
        createMockMolecule({
          id: 1,
          role: "reactant",
          mass: 100,
          actualAmount: 75,
          limitingReagent: true, // Initially limiting reagent
        }),
        createMockMolecule({
          id: 2,
          role: "reactant",
          mass: 150,
          actualAmount: 100,
          limitingReagent: false,
        }),
      ];

      // First, check initial state
      let result = calculateUpdatedMolecules(allMolecules, allMolecules[0]);
      expect(result.find((m) => m.id === 1)?.actualYield).toBeNull(); // Limiting reagent has no yield
      expect(result.find((m) => m.id === 2)?.actualYield).toBe(66.67); // 100/150 * 100 = 66.67%

      // Now change limiting reagent to second molecule
      const editedRow = createMockMolecule({
        id: 2,
        role: "reactant",
        mass: 150,
        actualAmount: 100,
        limitingReagent: true, // Now this is the limiting reagent
      });

      result = calculateUpdatedMolecules(result, editedRow);

      // Now first molecule should have yield, second should not
      expect(result.find((m) => m.id === 1)?.actualYield).toBe(75); // 75/100 * 100 = 75%
      expect(result.find((m) => m.id === 2)?.actualYield).toBeNull(); // New limiting reagent has no yield
    });
  });
});
