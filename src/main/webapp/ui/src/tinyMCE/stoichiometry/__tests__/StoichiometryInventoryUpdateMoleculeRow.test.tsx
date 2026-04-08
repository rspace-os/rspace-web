import React from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import StoichiometryInventoryUpdateMoleculeRow from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateMoleculeRow";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import type { InventoryUpdateStockDisplay } from "@/tinyMCE/stoichiometry/utils";

vi.mock("@/components/GlobalId", () => ({
  default: ({ record }: { record: { globalId: string } }) => <span>{record.globalId}</span>,
}));

vi.mock("@/stores/models/LinkableRecordFromGlobalId", () => ({
  default: class MockLinkableRecordFromGlobalId {
    globalId: string;

    constructor(globalId: string) {
      this.globalId = globalId;
    }
  },
}));

const molecule: EditableMolecule = {
  id: 12,
  rsChemElement: null,
  inventoryLink: {
    id: 1200,
    inventoryItemGlobalId: "SS1200",
    stockDeducted: false,
  },
  role: "REACTANT",
  formula: "C2H6O",
  name: "Ethanol",
  smiles: "CCO",
  coefficient: 1,
  molecularWeight: 46.07,
  mass: 5,
  moles: null,
  actualAmount: 5,
  actualMoles: null,
  actualYield: null,
  limitingReagent: false,
  notes: null,
};

describe("StoichiometryInventoryUpdateMoleculeRow", () => {
  const renderInTable = (
    ui: React.ReactElement<typeof StoichiometryInventoryUpdateMoleculeRow>,
  ) =>
    render(
      <Table aria-label="Inventory update test table">
        <TableHead>
          <TableRow>
            <TableCell aria-label="Select molecule" />
            <TableCell>Molecule</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>In Stock</TableCell>
            <TableCell>Will Use</TableCell>
            <TableCell>Remaining</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>{ui}</TableBody>
      </Table>,
    );

  const getMoleculeRow = (name: string) => {
    const row = document.querySelector(
      `[data-row-type="molecule"][data-molecule-name="${name}"]`,
    );

    if (!(row instanceof HTMLElement)) {
      throw new Error(`Molecule row not found: ${name}`);
    }

    return row;
  };

  const getMetric = (moleculeName: string, name: string) => {
    const metric = getMoleculeRow(moleculeName).querySelector(
      `[data-column="${name}"]`,
    );

    if (!(metric instanceof HTMLElement)) {
      throw new Error(`Metric column not found: ${moleculeName} / ${name}`);
    }

    return metric;
  };

  it("renders in-stock, will-use and remaining metrics with equal card sections", () => {
    const stockDisplay: InventoryUpdateStockDisplay = {
      inStock: {
        rawValue: 10,
        displayValue: "10.0",
        unitLabel: "g",
      },
      willUse: {
        rawValue: 5,
        displayValue: "5.0",
        unitLabel: "g",
      },
      remaining: {
        rawValue: 5,
        displayValue: "5.0",
        unitLabel: "g",
      },
      remainingStatus: "positive",
      warningText: null,
    };

    renderInTable(
      <StoichiometryInventoryUpdateMoleculeRow
        molecule={molecule}
        selected={true}
        disabled={false}
        helperText={null}
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(
      screen.getByRole("table", { name: "Inventory update test table" }),
    ).toBeVisible();
    expect(screen.getByText("In Stock")).toBeVisible();
    expect(screen.getByText("Will Use")).toBeVisible();
    expect(screen.getByText("Remaining")).toBeVisible();
    expect(screen.getByText("Molecule")).toBeVisible();
    expect(screen.getByText("Type")).toBeVisible();
    expect(
      screen.getByRole("checkbox", { name: "Ethanol" }),
    ).toHaveAccessibleName("Ethanol");
    expect(
      within(getMetric("Ethanol", "Molecule")).getByText("Ethanol"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Type")).getByText("reactant"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "In Stock")).getByText("10.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Will Use")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Remaining")).getByText("5.0 g"),
    ).toBeVisible();
  });

  it("only toggles when the checkbox is clicked", () => {
    const onToggle = vi.fn();
    const stockDisplay: InventoryUpdateStockDisplay = {
      inStock: {
        rawValue: 10,
        displayValue: "10.0",
        unitLabel: "g",
      },
      willUse: {
        rawValue: 5,
        displayValue: "5.0",
        unitLabel: "g",
      },
      remaining: {
        rawValue: 5,
        displayValue: "5.0",
        unitLabel: "g",
      },
      remainingStatus: "positive",
      warningText: null,
    };

    renderInTable(
      <StoichiometryInventoryUpdateMoleculeRow
        molecule={molecule}
        selected={false}
        disabled={false}
        helperText={null}
        stockDisplay={stockDisplay}
        onToggle={onToggle}
      />,
    );

    fireEvent.click(screen.getByText("Ethanol"));
    expect(onToggle).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("checkbox", { name: "Ethanol" }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("shows a remaining warning and negative status when stock is insufficient", () => {
    const stockDisplay: InventoryUpdateStockDisplay = {
      inStock: {
        rawValue: 4,
        displayValue: "4.0",
        unitLabel: "g",
      },
      willUse: {
        rawValue: 5,
        displayValue: "5.0",
        unitLabel: "g",
      },
      remaining: {
        rawValue: -1,
        displayValue: "-1.0",
        unitLabel: "g",
      },
      remainingStatus: "negative",
      warningText: "Insufficient Stock",
    };

    renderInTable(
      <StoichiometryInventoryUpdateMoleculeRow
        molecule={molecule}
        selected={false}
        disabled={true}
        helperText={null}
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(screen.getByRole("checkbox", { name: "Ethanol" })).toBeDisabled();
    expect(
      screen
        .getByRole("checkbox", { name: "Ethanol" })
        .closest("[data-dimmed]"),
    ).toHaveAttribute("data-dimmed", "true");
    expect(getMetric("Ethanol", "Remaining")).toHaveAttribute(
      "data-status",
      "negative",
    );
    expect(
      within(getMetric("Ethanol", "In Stock")).getByText("4.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Will Use")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Remaining")).getByText("-1.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Remaining")).getByText("Insufficient Stock"),
    ).toBeVisible();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders generic helper text separately from stock metrics", () => {
    const stockDisplay: InventoryUpdateStockDisplay = {
      inStock: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      willUse: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      remaining: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      remainingStatus: "default",
      warningText: null,
    };

    renderInTable(
      <StoichiometryInventoryUpdateMoleculeRow
        molecule={{ ...molecule, inventoryLink: null }}
        selected={false}
        disabled={true}
        helperText="Link an inventory item before updating stock."
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(
      screen.getByText("Link an inventory item before updating stock."),
    ).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Link an inventory item before updating stock.",
    );
    expect(
      within(getMetric("Ethanol", "In Stock")).getByText("—"),
    ).toBeVisible();
    expect(screen.queryByText("Insufficient Stock")).not.toBeInTheDocument();
  });

  it("disables the card when a linked inventory item has no quantity defined", () => {
    const stockDisplay: InventoryUpdateStockDisplay = {
      inStock: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      willUse: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      remaining: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      remainingStatus: "default",
      warningText: null,
    };

    renderInTable(
      <StoichiometryInventoryUpdateMoleculeRow
        molecule={molecule}
        selected={false}
        disabled={false}
        helperText={null}
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(screen.getByRole("checkbox", { name: "Ethanol" })).toBeDisabled();
    expect(
      screen
        .getByRole("checkbox", { name: "Ethanol" })
        .closest("[data-dimmed]"),
    ).toHaveAttribute("data-dimmed", "true");
    expect(
      within(getMetric("Ethanol", "In Stock")).getByText("—"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Will Use")).getByText("—"),
    ).toBeVisible();
  });

  it("disables the card when actual mass is not defined", () => {
    const stockDisplay: InventoryUpdateStockDisplay = {
      inStock: {
        rawValue: 10,
        displayValue: "10.0",
        unitLabel: "g",
      },
      willUse: {
        rawValue: null,
        displayValue: "—",
        unitLabel: "g",
      },
      remaining: {
        rawValue: null,
        displayValue: "—",
        unitLabel: "g",
      },
      remainingStatus: "default",
      warningText: null,
    };

    renderInTable(
      <StoichiometryInventoryUpdateMoleculeRow
        molecule={{ ...molecule, actualAmount: null }}
        selected={false}
        disabled={false}
        helperText="Define actual mass before updating linked inventory stock."
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(screen.getByRole("checkbox", { name: "Ethanol" })).toBeDisabled();
    expect(
      screen.getByText(
        "Define actual mass before updating linked inventory stock.",
      ),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "In Stock")).getByText("10.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Will Use")).getByText("— g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Remaining")).getByText("— g"),
    ).toBeVisible();
  });

  it("hides projected Will Use and Remaining metrics when stock was already deducted", () => {
    const stockDisplay: InventoryUpdateStockDisplay = {
      inStock: {
        rawValue: 10,
        displayValue: "10.0",
        unitLabel: "g",
      },
      willUse: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      remaining: {
        rawValue: null,
        displayValue: "—",
        unitLabel: null,
      },
      remainingStatus: "default",
      warningText: null,
    };

    renderInTable(
      <StoichiometryInventoryUpdateMoleculeRow
        molecule={{
          ...molecule,
          inventoryLink: {
            ...molecule.inventoryLink!,
            stockDeducted: true,
          },
        }}
        selected={false}
        disabled={true}
        helperText="Stock has already been deducted for this molecule."
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(
      within(getMetric("Ethanol", "In Stock")).getByText("10.0 g"),
    ).toBeVisible();
    expect(within(getMetric("Ethanol", "Will Use")).getByText("—")).toBeVisible();
    expect(
      within(getMetric("Ethanol", "Remaining")).getByText("—"),
    ).toBeVisible();
    expect(
      screen.getByText("Stock has already been deducted for this molecule."),
    ).toBeVisible();
  });
});
