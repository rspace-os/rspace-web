import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Blob as NodeBlob } from "node:buffer";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useGridApiContext } from "@mui/x-data-grid";
import StoichiometryTableGrid from "@/tinyMCE/stoichiometry/table/StoichiometryTableGrid";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableToolbar", () => ({
  default: ({
    allMolecules = [],
  }: {
    allMolecules?: Array<{ id: number }>;
  }) => {
    const apiRef = useGridApiContext();

    return (
      <div role="toolbar">
        <button
          type="button"
          onClick={() => {
            apiRef.current?.exportDataAsCsv({
              allColumns: true,
              getRowsToExport: () => allMolecules.map((molecule) => molecule.id),
            });
          }}
        >
          Export CSV
        </button>
      </div>
    );
  },
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell", () => ({
  default: () => <div>Inventory link cell</div>,
}));

globalThis.Blob = NodeBlob as unknown as typeof Blob;

function makeMolecule(overrides: Partial<EditableMolecule> = {}): EditableMolecule {
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
    ...overrides,
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
    vi.restoreAllMocks();
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

  it("always renders the Type dropdown when the role is editable", () => {
    render(
      <StoichiometryTableGrid editable allMolecules={[makeMolecule()]} />,
    );

    expect(
      screen.getByRole("combobox", {
        name: "Select type for Cyclopentane",
      }),
    ).toBeVisible();
  });

  it("keeps the Type column non-editable when an active chemId is present", () => {
    render(
      <StoichiometryTableGrid
        editable
        allMolecules={[makeMolecule()]}
        activeChemId={123}
      />,
    );

    expect(
      screen.queryByRole("combobox", {
        name: "Select type for Cyclopentane",
      }),
    ).not.toBeInTheDocument();

    expect(screen.getByText("reactant")).toBeVisible();
  });

  it("updates the molecule role when a new type is selected", async () => {
    const user = userEvent.setup();
    const onProcessRowUpdate = vi.fn(
      (newRow: EditableMolecule) => newRow,
    );

    render(
      <StoichiometryTableGrid
        editable
        allMolecules={[makeMolecule()]}
        onProcessRowUpdate={onProcessRowUpdate}
      />,
    );

    await user.click(
      screen.getByRole("combobox", {
        name: "Select type for Cyclopentane",
      }),
    );
    await user.click(await screen.findByRole("option", { name: "Product" }));

    await waitFor(() => {
      expect(onProcessRowUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 1,
          role: "PRODUCT",
        }),
        expect.objectContaining({
          id: 1,
          role: "REACTANT",
        }),
      );
    });
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

  it("exports linked inventory items as global IDs instead of object strings", async () => {
    const user = userEvent.setup();
    let blob: Blob | undefined;

    const createObjectURLSpy = vi.spyOn(window.URL, "createObjectURL").mockImplementation(
      (object: Blob | MediaSource) => {
        blob = object as Blob;
        return "blob:stoichiometry-export";
      },
    );

    render(
      <StoichiometryTableGrid
        editable
        allMolecules={[
          makeMolecule({
            inventoryLink: {
              id: 101,
              inventoryItemGlobalId: "SS123",
              stockDeducted: false,
            },
          }),
        ]}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Export CSV" }));

    expect(createObjectURLSpy).toHaveBeenCalledOnce();
    expect(blob).toBeDefined();

    const csv = await blob!.text();

    expect(csv).toContain("Inventory Link");
    expect(csv).toContain("SS123");
    expect(csv).toContain("Reactant");
    expect(csv).not.toContain("[object Object]");
  });

  it("exports deleted inventory links using their previous global ID", async () => {
    const user = userEvent.setup();
    let blob: Blob | undefined;

    const createObjectURLSpy = vi.spyOn(window.URL, "createObjectURL").mockImplementation(
      (object: Blob | MediaSource) => {
        blob = object as Blob;
        return "blob:stoichiometry-export";
      },
    );

    render(
      <StoichiometryTableGrid
        editable
        allMolecules={[
          makeMolecule({
            inventoryLink: null,
            deletedInventoryLink: {
              id: 202,
              inventoryItemGlobalId: "SS999",
              stockDeducted: false,
            },
          }),
        ]}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Export CSV" }));

    expect(createObjectURLSpy).toHaveBeenCalledOnce();
    expect(blob).toBeDefined();
    await expect(blob!.text()).resolves.toContain("SS999");
  });
});



