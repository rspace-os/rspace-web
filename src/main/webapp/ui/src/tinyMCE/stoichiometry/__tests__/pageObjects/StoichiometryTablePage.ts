import { gridClasses } from "@mui/x-data-grid";
import { vi } from "vitest";
import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Page object for the stoichiometry table, as mounted by the stories in
 * StoichiometryTable.story.tsx. Encapsulates the locators and user
 * interactions; assertions live in the tests themselves.
 *
 * Dynamic lookups use the DOM directly (the grid is always loaded first via
 * `waitForLoad`) and return stable `getByCSS` locators keyed on data-id /
 * data-field so content assertions remain retriable.
 */
export class StoichiometryTablePage {
  readonly table: Locator = page.getByRole("grid");

  /**
   * Waits until either the grid has rendered (and the "Loading molecule
   * information..." dialog has gone away) or the empty state is shown.
   */
  async waitForLoad(): Promise<void> {
    await vi.waitFor(
      () => {
        const table = document.querySelector('[role="grid"]');
        const loading = Array.from(document.querySelectorAll('[role="dialog"]')).some((dialog) =>
          dialog.textContent?.includes("Loading molecule information..."),
        );
        const noData = document.body.textContent?.includes("No stoichiometry data available");
        if (!(((table && !loading) || noData) ?? false)) {
          throw new Error("Stoichiometry table not loaded yet");
        }
      },
      { timeout: 10000, interval: 50 },
    );
  }

  get moleculeInfoLoadingDialog(): Locator {
    return page.getByRole("dialog").filter({ hasText: "Loading molecule information..." });
  }

  dataRows(): Locator {
    return this.table.getByCSS('[role="row"][data-id]');
  }

  cellByField(row: Locator, field: string): Locator {
    return row.getByCSS(`[role="gridcell"][data-field="${field}"]`);
  }

  columnField(headerText: string): string {
    const headers = this.table.getByRole("columnheader").elements();
    for (const header of headers) {
      if (header.textContent?.trim() === headerText) {
        const field = header.getAttribute("data-field");
        if (!field) {
          throw new Error(`Column field for header "${headerText}" not found`);
        }
        return field;
      }
    }
    throw new Error(`Column "${headerText}" not found`);
  }

  /** Returns a stable, retriable locator for the row matching a cell value. */
  rowByColumnValue(columnHeaderText: string, cellValue: string): Locator {
    const field = this.columnField(columnHeaderText);
    const rows = this.dataRows().elements();
    for (const row of rows) {
      const cell = row.querySelector(`[role="gridcell"][data-field="${field}"]`);
      if (cell?.textContent?.includes(cellValue)) {
        const id = row.getAttribute("data-id");
        if (!id) {
          throw new Error(`Row matching "${cellValue}" has no data-id`);
        }
        return this.table.getByCSS(`[role="row"][data-id="${id}"]`);
      }
    }
    throw new Error(`Row with value "${cellValue}" in column "${columnHeaderText}" not found`);
  }

  cell(compoundName: string, columnHeaderText: string): Locator {
    const field = this.columnField(columnHeaderText);
    const row = this.rowByColumnValue("Name", compoundName);
    return this.cellByField(row, field);
  }

  yieldCell(rowIndex: number): Locator {
    const field = this.columnField("Yield/Excess (%)");
    return this.cellByField(this.dataRows().nth(rowIndex), field);
  }

  async editCell({ row, column, value }: { row: number; column: string; value: string }): Promise<void> {
    const field = this.columnField(column);
    const targetCell = this.cellByField(this.dataRows().nth(row), field);

    await targetCell.click();
    await userEvent.keyboard("{Enter}");

    const input = page.getByCSS(`.${gridClasses["cell--editing"]} input, .${gridClasses["cell--editing"]} textarea`);
    await input.fill(value);
    await userEvent.keyboard("{Enter}");
  }

  limitingReagentRadio(name: string): Locator {
    return page.getByRole("radio", {
      name: new RegExp(`Select ${name} as limiting reagent`),
    });
  }

  async selectLimitingReagent(name: string): Promise<void> {
    await this.limitingReagentRadio(name).click();
  }

  get exportButton(): Locator {
    return page.getByRole("button", { name: /Export/ });
  }

  async openExportMenu(): Promise<void> {
    await this.exportButton.click();
  }

  /**
   * Triggers the "Export to CSV" action and returns the generated CSV text.
   *
   * Vitest browser mode has no Playwright-style download interception, so we
   * capture the Blob that MUI's `exportDataAsCsv` hands to
   * `URL.createObjectURL` and read its text, suppressing the anchor click that
   * would otherwise trigger a real browser download.
   */
  async exportToCsv(): Promise<string> {
    await this.openExportMenu();

    const blobs: Blob[] = [];
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;
    const originalClick = HTMLAnchorElement.prototype.click;

    URL.createObjectURL = (obj: Blob | MediaSource) => {
      if (obj instanceof Blob) blobs.push(obj);
      return "blob:mock-csv";
    };
    URL.revokeObjectURL = () => {};
    HTMLAnchorElement.prototype.click = function noop() {};

    try {
      await page.getByRole("menuitem", { name: /Export to CSV/ }).click();
    } finally {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
      HTMLAnchorElement.prototype.click = originalClick;
    }

    const blob = blobs[blobs.length - 1];
    if (!blob) {
      throw new Error("No CSV blob was produced by the export");
    }
    return blob.text();
  }

  get addChemicalButton(): Locator {
    return page.getByRole("button", { name: "Add Chemical" });
  }

  get pubChemMenuItem(): Locator {
    return page.getByRole("menuitem", {
      name: /PubChem.*Import compound from PubChem/i,
    });
  }

  get galleryMenuItem(): Locator {
    return page.getByRole("menuitem", {
      name: /Gallery.*Import compound from Gallery/i,
    });
  }

  get manualEntryMenuItem(): Locator {
    return page.getByRole("menuitem", {
      name: /Manually.*Manually enter SMILES/i,
    });
  }

  async openAddChemicalMenu(): Promise<void> {
    await this.addChemicalButton.click();
  }

  get pubChemDialog(): Locator {
    return page.getByRole("dialog", { name: /Insert from PubChem/i });
  }

  get galleryDialog(): Locator {
    return page.getByRole("dialog", { name: /Gallery Picker/i });
  }

  get manualSmilesDialog(): Locator {
    return page.getByRole("dialog", { name: /Add New Chemical/i });
  }

  async searchPubChem(compound: string): Promise<void> {
    await page.getByRole("textbox", { name: /Enter a compound name/i }).fill(compound);
    await page.getByRole("button", { name: /search/i }).click();
  }

  async insertPubChemResult(): Promise<void> {
    await page.getByRole("button", { name: "Insert" }).click();
  }

  async enterManualSmiles({ smiles, name }: { smiles: string; name: string }): Promise<void> {
    await page.getByRole("textbox", { name: /name/i }).fill(name);
    await page.getByRole("textbox", { name: /smiles/i }).fill(smiles);
  }

  async addManualReagent(): Promise<void> {
    await page.getByRole("button", { name: /add chemical/i }).click();
  }

  async selectGalleryFile(fileName: RegExp): Promise<void> {
    await page.getByRole("gridcell", { name: fileName }).click();
  }

  async addSelectedGalleryFiles(): Promise<void> {
    await page.getByRole("button", { name: /add/i }).click();
  }

  get updateInventoryStockButton(): Locator {
    return page.getByRole("button", { name: "Update Inventory Stock" });
  }

  async clickUpdateInventoryStock(): Promise<void> {
    await this.updateInventoryStockButton.click();
  }

  addInventoryLinkButton(molecule: string): Locator {
    return page.getByLabelText(`Add inventory link for ${molecule}`);
  }

  removeInventoryLinkButton(molecule: string): Locator {
    return page.getByLabelText(`Remove inventory link for ${molecule}`);
  }

  inventoryPickerDialog(molecule?: string): Locator {
    return page.getByRole("dialog", {
      name: molecule ? `Pick inventory item for ${molecule}` : /Pick inventory item for/i,
    });
  }

  async openInventoryPickerFor(molecule: string): Promise<void> {
    await this.addInventoryLinkButton(molecule).click();
  }

  async closeInventoryPicker(): Promise<void> {
    await page.getByRole("button", { name: "Cancel" }).click();
  }

  inventoryLinkCell(compoundName: string): Locator {
    return this.cell(compoundName, "Inventory Link");
  }

  insufficientStockIcon(container: Locator): Locator {
    return container.getByCSS('svg[aria-label="Insufficient Stock"]');
  }
}

/**
 * Page object for the "Update Inventory Stock" dialog opened from the
 * stoichiometry table toolbar.
 */
export class InventoryUpdateDialogPage {
  readonly dialog: Locator = page.getByRole("dialog", {
    name: /Update Inventory Stock/i,
  });

  columnHeader(name: string): Locator {
    return this.dialog.getByRole("columnheader", { name, exact: true });
  }

  checkbox(moleculeName: string): Locator {
    return this.dialog.getByRole("checkbox", { name: moleculeName });
  }

  moleculeRow(moleculeName: string): Locator {
    return this.dialog.getByCSS(`[data-row-type="molecule"][data-molecule-name="${moleculeName}"]`);
  }

  metric(moleculeName: string, metricName: string): Locator {
    return this.moleculeRow(moleculeName).getByCSS(`[data-column="${metricName}"]`);
  }
}
