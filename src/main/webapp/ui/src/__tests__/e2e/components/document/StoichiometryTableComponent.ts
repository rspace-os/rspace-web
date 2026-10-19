import type { Locator } from "@playwright/test";

const COLUMN_FIELD: Record<string, string> = {
  Name: "name",
  "Inventory Link": "inventoryLink",
  Type: "role",
  "Limiting Reagent": "limitingReagent",
  Equivalent: "coefficient",
  "Molecular Weight (g/mol)": "molecularWeight",
  "Mass (g)": "mass",
  "Moles (mol)": "moles",
  "Actual Mass (g)": "actualAmount",
  "Actual Moles (mol)": "actualMoles",
  "Yield/Excess (%)": "actualYield",
  Notes: "notes",
};

/** The read-only reaction table rendered in a document field's view mode. */
export class StoichiometryTableComponent {
  readonly grid: Locator;

  constructor(root: Locator) {
    this.grid = root.getByRole("grid");
  }

  /** Data rows (excludes the column-header row, which carries `columnheader`s, not `gridcell`s). */
  dataRows(): Locator {
    return this.grid.getByRole("row").filter({ has: this.grid.page().getByRole("gridcell") });
  }

  private columnField(headerText: string): string {
    const field = COLUMN_FIELD[headerText];
    if (!field) {
      throw new Error(`Unknown stoichiometry column header "${headerText}"`);
    }
    return field;
  }

  rowByCompoundName(name: string): Locator {
    return this.dataRows().filter({ has: this.grid.page().getByRole("gridcell", { name, exact: true }) });
  }

  cell(compoundName: string, columnHeader: string): Locator {
    const field = this.columnField(columnHeader);
    return this.rowByCompoundName(compoundName).locator(`[role="gridcell"][data-field="${field}"]`);
  }

  async getCellText(compoundName: string, columnHeader: string): Promise<string> {
    return (await this.cell(compoundName, columnHeader).innerText()).trim();
  }
}
