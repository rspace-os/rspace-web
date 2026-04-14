import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StoichiometryTableGrid from "@/tinyMCE/stoichiometry/table/StoichiometryTableGrid";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableToolbar", () => ({
  default: () => <div role="toolbar">Stoichiometry toolbar</div>,
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

function makeAgentMolecule(): EditableMolecule {
  return {
    ...makeMolecule(),
    id: 2,
    role: "AGENT",
    name: "Water",
    smiles: "O",
  };
}

function makeProductMolecule(): EditableMolecule {
  return {
    ...makeMolecule(),
    id: 3,
    role: "PRODUCT",
    name: "Cyclohexanone",
    smiles: "O=C1CCCCC1",
  };
}

describe("StoichiometryTableGrid", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the table shell and built-in empty state when there are no molecules", async () => {
    render(<StoichiometryTableGrid editable={false} allMolecules={[]} />);

    const grid = await screen.findByRole("grid");
    expect(grid).toBeVisible();
    expect(screen.getByText("No rows")).toBeVisible();
    expect(screen.getByRole("columnheader", { name: "Name" })).toBeVisible();
    expect(screen.getByRole("columnheader", { name: "Type" })).toBeVisible();
  });

  it("opens the Type dropdown on single click when there is no active chemId", async () => {
    const user = userEvent.setup();

    render(
      <StoichiometryTableGrid editable allMolecules={[makeMolecule()]} />,
    );

    await user.click(
      screen.getByRole("button", { name: "Edit type for Cyclopentane" }),
    );

    await waitFor(() => {
      expect(
        screen.getByRole("combobox", {
          name: "Select type for Cyclopentane",
        }),
      ).toBeVisible();
    });
  });

  it("does not open the Type dropdown on single click when an active chemId is present", () => {
    render(
      <StoichiometryTableGrid
        editable
        allMolecules={[makeMolecule()]}
        activeChemId={123}
      />,
    );

    expect(
      screen.queryByRole("button", { name: "Edit type for Cyclopentane" }),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole("combobox", {
        name: "Select type for Cyclopentane",
      }),
    ).not.toBeInTheDocument();
  });

  it.each([
    {
      label: "reactant",
      molecule: makeMolecule(),
      buttonName: "Delete reagent Cyclopentane",
    },
    {
      label: "product",
      molecule: makeProductMolecule(),
      buttonName: "Delete reagent Cyclohexanone",
    },
    {
      label: "agent",
      molecule: makeAgentMolecule(),
      buttonName: "Delete reagent Water",
    },
  ])(
    "shows an enabled delete button for $label rows when there is no active chemId",
    ({ molecule, buttonName }) => {
      render(<StoichiometryTableGrid editable allMolecules={[molecule]} />);

      expect(screen.getByRole("button", { name: buttonName })).toBeVisible();
      expect(screen.getByRole("button", { name: buttonName })).toBeEnabled();
    },
  );

  it.each([
    {
      label: "reactant",
      molecule: makeMolecule(),
      buttonName: "Delete reagent Cyclopentane",
    },
    {
      label: "product",
      molecule: makeProductMolecule(),
      buttonName: "Delete reagent Cyclohexanone",
    },
  ])(
    "shows a disabled delete button for $label rows when an active chemId is present",
    ({ molecule, buttonName }) => {
      render(
        <StoichiometryTableGrid
          editable
          allMolecules={[molecule]}
          activeChemId={123}
        />,
      );

      expect(screen.getByRole("button", { name: buttonName })).toBeDisabled();
    },
  );

  it.each([
    {
      label: "agent",
      molecule: makeAgentMolecule(),
      buttonName: "Delete reagent Water",
    },
  ])(
    "shows an enabled delete button for $label rows when an active chemId is present",
    ({ molecule, buttonName }) => {
      render(
        <StoichiometryTableGrid
          editable
          allMolecules={[molecule]}
          activeChemId={123}
        />,
      );

      expect(screen.getByRole("button", { name: buttonName })).toBeEnabled();
    },
  );
});



