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

export class StoichiometryTableComponent {
  readonly grid: Locator;

  constructor(protected readonly root: Locator) {
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

  async getCompoundCount(): Promise<number> {
    return this.dataRows().count();
  }

  async hasCompound(name: string): Promise<boolean> {
    return (await this.rowByCompoundName(name).count()) > 0;
  }

  async getCellText(compoundName: string, columnHeader: string): Promise<string> {
    return (await this.cell(compoundName, columnHeader).innerText()).trim();
  }

  /** Edits a numeric cell (Mass, Moles, etc.) — MUI renders its edit control as a spinbutton. */
  async editCell(compoundName: string, columnHeader: string, value: string): Promise<void> {
    const cell = this.cell(compoundName, columnHeader);
    await cell.click();
    await this.root.page().keyboard.press("Enter");
    await cell.getByRole("spinbutton").fill(value);
    await this.root.page().keyboard.press("Enter");
  }

  limitingReagentRadio(name: string): Locator {
    return this.rowByCompoundName(name).getByRole("radio", { name: `Select ${name} as limiting reagent` });
  }

  async selectLimitingReagent(name: string): Promise<void> {
    await this.limitingReagentRadio(name).click();
  }
}
