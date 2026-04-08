import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";
import {
  StoichiometryTableControllerProvider,
  type StoichiometryTableController,
} from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

const dataGridPropsSpy = vi.hoisted(() => vi.fn());

vi.mock("@mui/x-data-grid", () => ({
  DataGrid: (props: unknown) => {
    dataGridPropsSpy(props);
    return <div data-testid="stoichiometry-grid" />;
  },
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableToolbar", () => ({
  default: () => null,
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog", () => ({
  default: () => null,
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell", () => ({
  default: () => null,
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableRoleChip", () => ({
  default: () => null,
}));

function makeMolecule(): EditableMolecule {
  return {
    id: 1,
    rsChemElement: null,
    inventoryLink: {
      id: 501,
      inventoryItemGlobalId: "SS124",
      stockDeducted: false,
    },
    savedInventoryLink: {
      id: 501,
      inventoryItemGlobalId: "SS124",
      stockDeducted: false,
    },
    deletedInventoryLink: null,
    role: "REACTANT",
    formula: null,
    name: "Cyclopentane",
    smiles: "C1CCCCC1",
    coefficient: 1,
    molecularWeight: 84.16,
    mass: 5,
    moles: null,
    actualAmount: 5,
    actualMoles: null,
    actualYield: null,
    limitingReagent: false,
    notes: null,
  };
}

describe("StoichiometryTable", () => {
  it("passes loaded inventory quantity data to the toolbar slot props", () => {
    dataGridPropsSpy.mockReset();

    const quantityMap = new Map<string, InventoryQuantityQueryResult>([
      [
        "SS124",
        {
          status: "available",
          quantity: {
            numericValue: 10,
            unitId: 7,
          },
        },
      ],
    ]);
    const updateInventoryStock = vi.fn();
    const controller: StoichiometryTableController = {
      allMolecules: [makeMolecule()],
      linkedInventoryQuantityInfoByGlobalId: quantityMap,
      isGettingMoleculeInfo: false,
      addReagent: vi.fn(async () => {}),
      deleteReagent: vi.fn(),
      updateInventoryStock,
      pickInventoryLink: vi.fn(),
      removeInventoryLink: vi.fn(),
      undoRemoveInventoryLink: vi.fn(),
      selectLimitingReagent: vi.fn(),
      processRowUpdate: vi.fn((newRow: EditableMolecule) => newRow),
    };

    render(
      <StoichiometryTableControllerProvider value={controller}>
        <StoichiometryTable
          editable
          stoichiometryId={3}
          stoichiometryRevision={1}
          hasChanges
        />
      </StoichiometryTableControllerProvider>,
    );

    const props = dataGridPropsSpy.mock.calls[0]?.[0] as {
      slotProps?: {
        toolbar?: {
          hasChanges?: boolean;
          linkedInventoryQuantityInfoByGlobalId?: ReadonlyMap<
            string,
            InventoryQuantityQueryResult
          >;
          onUpdateInventoryStock?: unknown;
        };
      };
    };

    expect(props.slotProps?.toolbar?.hasChanges).toBe(true);
    expect(props.slotProps?.toolbar?.onUpdateInventoryStock).toBe(
      updateInventoryStock,
    );
    expect(
      props.slotProps?.toolbar?.linkedInventoryQuantityInfoByGlobalId,
    ).toBe(quantityMap);
  });
});


