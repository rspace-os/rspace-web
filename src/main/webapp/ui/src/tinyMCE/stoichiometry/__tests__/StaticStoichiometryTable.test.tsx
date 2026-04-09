import React from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StaticStoichiometryTable from "@/tinyMCE/stoichiometry/table/StaticStoichiometryTable";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

type StoichiometryQueryArgs = {
  stoichiometryId: number;
  revision: number;
  getToken: unknown;
};

type StoichiometryQueryData = {
  id: number;
  revision: number;
};

let lastUseGetStoichiometryQueryArgs: StoichiometryQueryArgs | null = null;
let lastToEditableMoleculesArg: StoichiometryQueryData | null = null;
let mockStoichiometryQueryData: StoichiometryQueryData = { id: 3, revision: 4 };
let mockEditableMolecules: EditableMolecule[] = [];
const stoichiometryTableGridSpy = vi.fn();

vi.mock("@/hooks/auth/useOauthToken", () => ({
  default: () => ({ getToken: vi.fn(() => Promise.resolve("token")) }),
}));

vi.mock("@/modules/stoichiometry/queries", () => ({
  useGetStoichiometryQuery: (args: StoichiometryQueryArgs) => {
    lastUseGetStoichiometryQueryArgs = args;
    return { data: mockStoichiometryQueryData };
  },
}));

vi.mock("@/tinyMCE/stoichiometry/editableMolecules", () => ({
  toEditableMolecules: (data: StoichiometryQueryData) => {
    lastToEditableMoleculesArg = data;
    return mockEditableMolecules;
  },
}));

vi.mock("@/tinyMCE/stoichiometry/table/StoichiometryTableGrid", () => ({
  default: (props: unknown) => {
    stoichiometryTableGridSpy(props);
    return <div data-testid="static-stoichiometry-grid" />;
  },
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

describe("StaticStoichiometryTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    lastUseGetStoichiometryQueryArgs = null;
    lastToEditableMoleculesArg = null;
    mockStoichiometryQueryData = { id: 3, revision: 4 };
    mockEditableMolecules = [makeMolecule()];
  });

  it("loads stoichiometry data and renders the grid in read-only mode", () => {
    render(
      <StaticStoichiometryTable
        stoichiometryId={3}
        stoichiometryRevision={4}
      />,
    );

    expect(screen.getByTestId("static-stoichiometry-grid")).toBeVisible();
    expect(lastUseGetStoichiometryQueryArgs).toMatchObject({
      stoichiometryId: 3,
      revision: 4,
    });
    expect(lastToEditableMoleculesArg).toEqual({ id: 3, revision: 4 });
    expect(stoichiometryTableGridSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        editable: false,
        allMolecules: [expect.objectContaining({ id: 1, name: "Cyclopentane" })],
      }),
    );
  });
});
