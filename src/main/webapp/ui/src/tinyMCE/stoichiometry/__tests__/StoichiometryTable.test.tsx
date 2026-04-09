import React from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StoichiometryTable from "@/tinyMCE/stoichiometry/StoichiometryTable";
import {
  StoichiometryTableControllerProvider,
  type StoichiometryTableController,
} from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

const stoichiometryTableGridSpy = vi.fn();
const staticStoichiometryTableSpy = vi.fn();

vi.mock("@/tinyMCE/stoichiometry/table/StoichiometryTableGrid", () => ({
  default: (props: unknown) => {
    stoichiometryTableGridSpy(props);
    return <div role="grid" aria-label="Stoichiometry table grid" />;
  },
}));

vi.mock("@/tinyMCE/stoichiometry/table/StaticStoichiometryTable", () => ({
  default: (props: unknown) => {
    staticStoichiometryTableSpy(props);
    return <div>Static stoichiometry table</div>;
  },
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog", () => ({
  default: () => (
    <div role="dialog" aria-label="Loading molecule information">
      Loading molecule information...
    </div>
  ),
}));

function makeMolecule(): EditableMolecule {
  return {
    id: 1,
    rsChemElement: null,
    inventoryLink: null,
    savedInventoryLink: null,
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
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders a loading dialog while editable molecule info is being fetched", () => {
    const controller: StoichiometryTableController = {
      allMolecules: [makeMolecule()],
      linkedInventoryQuantityInfoByGlobalId: new Map(),
      isGettingMoleculeInfo: true,
      addReagent: vi.fn(() => Promise.resolve()),
      deleteReagent: vi.fn(),
      updateInventoryStock: vi.fn(() => Promise.resolve({ results: [] })),
      pickInventoryLink: vi.fn(),
      removeInventoryLink: vi.fn(),
      undoRemoveInventoryLink: vi.fn(),
      selectLimitingReagent: vi.fn(),
      processRowUpdate: vi.fn((newRow: EditableMolecule) => newRow),
    };

    render(
      <StoichiometryTableControllerProvider value={controller}>
        <StoichiometryTable editable stoichiometryId={3} stoichiometryRevision={1} />
      </StoichiometryTableControllerProvider>,
    );

    expect(
      screen.getByRole("dialog", { name: "Loading molecule information" }),
    ).toBeVisible();
    expect(stoichiometryTableGridSpy).not.toHaveBeenCalled();
  });

  it("delegates read-only rendering to StaticStoichiometryTable", () => {
    render(<StoichiometryTable stoichiometryId={3} stoichiometryRevision={1} />);

    expect(screen.getByText("Static stoichiometry table")).toBeVisible();
    expect(staticStoichiometryTableSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        stoichiometryId: 3,
        stoichiometryRevision: 1,
        editable: false,
      }),
    );
  });
});
