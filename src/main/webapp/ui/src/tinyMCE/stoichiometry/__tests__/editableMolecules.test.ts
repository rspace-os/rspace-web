import { describe, expect, it } from "vitest";
import type { StoichiometryResponse } from "@/modules/stoichiometry/schema";
import { toEditableMolecules } from "@/tinyMCE/stoichiometry/editableMolecules";

function makeStoichiometryResponse(
  molecules: StoichiometryResponse["molecules"],
): StoichiometryResponse {
  return {
    id: 3,
    revision: 1,
    molecules,
  };
}

describe("toEditableMolecules", () => {
  it("adds editing-only fields and preserves an existing limiting reactant", () => {
    const inventoryLink = {
      id: 501,
      inventoryItemGlobalId: "SS123",
      stockDeducted: false,
    };
    const result = toEditableMolecules(
      makeStoichiometryResponse([
        {
          id: 1,
          rsChemElement: null,
          inventoryLink,
          role: "REACTANT",
          formula: "H2",
          name: "Hydrogen",
          smiles: "[H][H]",
          coefficient: 1,
          molecularWeight: 2.016,
          mass: 1,
          actualAmount: 1,
          actualYield: null,
          limitingReagent: true,
          notes: null,
        },
        {
          id: 2,
          rsChemElement: null,
          inventoryLink: null,
          role: "PRODUCT",
          formula: "H2O",
          name: "Water",
          smiles: "O",
          coefficient: 1,
          molecularWeight: 18.015,
          mass: 9,
          actualAmount: 9,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        },
      ]),
    );

    expect(result[0]).toMatchObject({
      inventoryLink,
      savedInventoryLink: inventoryLink,
      deletedInventoryLink: null,
      moles: null,
      actualMoles: null,
      limitingReagent: true,
    });
    expect(result[1]).toMatchObject({
      savedInventoryLink: null,
      deletedInventoryLink: null,
      moles: null,
      actualMoles: null,
      limitingReagent: false,
    });
  });

  it("defaults the first reactant to the limiting reagent when none is set", () => {
    const result = toEditableMolecules(
      makeStoichiometryResponse([
        {
          id: 1,
          rsChemElement: null,
          inventoryLink: null,
          role: "REACTANT",
          formula: "A",
          name: "Reactant A",
          smiles: "A",
          coefficient: 1,
          molecularWeight: 10,
          mass: 10,
          actualAmount: 10,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        },
        {
          id: 2,
          rsChemElement: null,
          inventoryLink: null,
          role: "REACTANT",
          formula: "B",
          name: "Reactant B",
          smiles: "B",
          coefficient: 2,
          molecularWeight: 20,
          mass: 20,
          actualAmount: 20,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        },
      ]),
    );

    expect(result[0]?.limitingReagent).toBe(true);
    expect(result[1]?.limitingReagent).toBe(false);
  });

  it("leaves molecules unchanged when there are no reactants", () => {
    const result = toEditableMolecules(
      makeStoichiometryResponse([
        {
          id: 1,
          rsChemElement: null,
          inventoryLink: null,
          role: "AGENT",
          formula: "EtOH",
          name: "Ethanol",
          smiles: "CCO",
          coefficient: 1,
          molecularWeight: 46.07,
          mass: 5,
          actualAmount: 5,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        },
        {
          id: 2,
          rsChemElement: null,
          inventoryLink: null,
          role: "PRODUCT",
          formula: "P",
          name: "Product",
          smiles: "P",
          coefficient: 1,
          molecularWeight: 30,
          mass: 3,
          actualAmount: 3,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        },
      ]),
    );

    expect(result.map(({ limitingReagent }) => limitingReagent)).toEqual([
      false,
      false,
    ]);
    expect(result.every(({ moles, actualMoles }) => moles === null && actualMoles === null)).toBe(
      true,
    );
  });
});

