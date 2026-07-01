import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { fireEvent, render, screen, within } from "@testing-library/react";
import type React from "react";
import { describe, expect, it, vi } from "vitest";
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
  savedInventoryLink: {
    id: 1200,
    inventoryItemGlobalId: "SS1200",
    stockDeducted: false,
  },
  deletedInventoryLink: null,
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
  const renderInTable = (ui: React.ReactElement<typeof StoichiometryInventoryUpdateMoleculeRow>) =>
    render(
      <Table aria-label="Inventory update test table">
        <TableHead>
          <TableRow>
            <TableCell aria-label="common:stoichiometry.inventoryUpdate.selectMoleculeLabel" />
            <TableCell>{"common:stoichiometry.inventoryUpdate.molecule"}</TableCell>
            <TableCell>{"common:stoichiometry.inventoryUpdate.inStock"}</TableCell>
            <TableCell>{"common:stoichiometry.inventoryUpdate.willUse"}</TableCell>
            <TableCell>{"common:stoichiometry.inventoryUpdate.remaining"}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>{ui}</TableBody>
      </Table>,
    );

  const columnIndexes = {
    Actions: 0,
    "common:stoichiometry.inventoryUpdate.molecule": 1,
    "common:stoichiometry.inventoryUpdate.inStock": 2,
    "common:stoichiometry.inventoryUpdate.willUse": 3,
    "common:stoichiometry.inventoryUpdate.remaining": 4,
  } as const;

  const getMoleculeRow = (name: string) => {
    const row = screen
      .getAllByRole("row")
      .find((candidate) => within(candidate).queryByRole("checkbox", { name }) !== null);

    if (!row) {
      throw new Error(`Molecule row not found: ${name}`);
    }

    return row;
  };

  const getMetric = (moleculeName: string, name: keyof typeof columnIndexes) => {
    const metric = within(getMoleculeRow(moleculeName)).getAllByRole("cell")[columnIndexes[name]];

    if (!metric) {
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

    expect(screen.getByRole("table", { name: "Inventory update test table" })).toBeVisible();
    expect(screen.getByText("common:stoichiometry.inventoryUpdate.inStock")).toBeVisible();
    expect(screen.getByText("common:stoichiometry.inventoryUpdate.willUse")).toBeVisible();
    expect(screen.getByText("common:stoichiometry.inventoryUpdate.remaining")).toBeVisible();
    expect(screen.getByText("common:stoichiometry.inventoryUpdate.molecule")).toBeVisible();
    expect(screen.getByRole("checkbox", { name: "Ethanol" })).toHaveAccessibleName("Ethanol");
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.molecule")).getByText("Ethanol"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.molecule")).getByText(
        "common:stoichiometry.table.roles.reactant",
      ),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.inStock")).getByText("10.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.willUse")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.remaining")).getByText("5.0 g"),
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

  it("still shows will-use and remaining metrics when stock was already deducted", () => {
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
        molecule={{
          ...molecule,
          inventoryLink: {
            id: 1200,
            inventoryItemGlobalId: "SS1200",
            stockDeducted: true,
          },
        }}
        selected={false}
        disabled={false}
        helperText="common:stoichiometry.inventoryUpdate.stockDeductedWarning"
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.willUse")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.remaining")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.molecule")).getByText(
        "common:stoichiometry.inventoryLink.stockDeducted",
      ),
    ).toBeVisible();
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
      warningText: "common:stoichiometry.inventoryLink.insufficientStock",
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
    expect(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.remaining")).toHaveAttribute(
      "data-status",
      "negative",
    );
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.inStock")).getByText("4.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.willUse")).getByText("5.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.remaining")).getByText("-1.0 g"),
    ).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.remaining")).getByText(
        "common:stoichiometry.inventoryLink.insufficientStock",
      ),
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
        helperText="common:stoichiometry.inventoryUpdate.linkRequired"
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(screen.getByText("common:stoichiometry.inventoryUpdate.linkRequired")).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent("common:stoichiometry.inventoryUpdate.linkRequired");
    expect(within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.inStock")).getByText("—")).toBeVisible();
    expect(screen.queryByText("common:stoichiometry.inventoryLink.insufficientStock")).not.toBeInTheDocument();
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
        disabled={true}
        helperText={null}
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(screen.getByRole("checkbox", { name: "Ethanol" })).toBeDisabled();
    expect(within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.inStock")).getByText("—")).toBeVisible();
    expect(within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.willUse")).getByText("—")).toBeVisible();
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
        disabled={true}
        helperText="common:stoichiometry.inventoryUpdate.missingActualMass"
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(screen.getByRole("checkbox", { name: "Ethanol" })).toBeDisabled();
    expect(screen.getByText("common:stoichiometry.inventoryUpdate.missingActualMass")).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.inStock")).getByText("10.0 g"),
    ).toBeVisible();
    expect(within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.willUse")).getByText("— g")).toBeVisible();
    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.remaining")).getByText("— g"),
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
            // biome-ignore lint/style/noNonNullAssertion: initial biome migration
            ...molecule.inventoryLink!,
            stockDeducted: true,
          },
        }}
        selected={false}
        disabled={true}
        helperText="common:stoichiometry.inventoryUpdate.stockDeductedWarning"
        stockDisplay={stockDisplay}
        onToggle={() => {}}
      />,
    );

    expect(
      within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.inStock")).getByText("10.0 g"),
    ).toBeVisible();
    expect(within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.willUse")).getByText("—")).toBeVisible();
    expect(within(getMetric("Ethanol", "common:stoichiometry.inventoryUpdate.remaining")).getByText("—")).toBeVisible();
    expect(screen.getByText("common:stoichiometry.inventoryUpdate.stockDeductedWarning")).toBeVisible();
  });
});
