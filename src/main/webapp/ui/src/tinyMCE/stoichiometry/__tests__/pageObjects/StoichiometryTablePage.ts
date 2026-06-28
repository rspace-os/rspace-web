import { vi } from "vitest";
import { type Locator, page, userEvent } from "vitest/browser";

const LOADING_MOLECULE_INFO = "common:stoichiometry.dialog.loadingMoleculeInformation";
const EXPORT_TO_CSV = "common:stoichiometry.tableToolbar.exportToCsv";
const ADD_CHEMICAL = "common:stoichiometry.addReagent.addChemical";
const PUBCHEM_MENU_ITEM =
  /common:stoichiometry\.addReagent\.sources\.pubChem\.title.*common:stoichiometry\.addReagent\.sources\.pubChem\.subheader/i;
const GALLERY_MENU_ITEM =
  /common:stoichiometry\.addReagent\.sources\.gallery\.title.*common:stoichiometry\.addReagent\.sources\.gallery\.subheader/i;
const MANUAL_MENU_ITEM =
  /common:stoichiometry\.addReagent\.sources\.manual\.title.*common:stoichiometry\.addReagent\.sources\.manual\.subheader/i;
const PUBCHEM_DIALOG = "apps:tinyMce.pubchem.dialog.title";
const GALLERY_DIALOG = "common:appBar.sections.gallery.title";
const MANUAL_DIALOG = "common:stoichiometry.addReagent.title";
const PUBCHEM_NAME_CAS_PLACEHOLDER = "apps:tinyMce.pubchem.dialog.searchPlaceholders.nameCas";
const INSERT = "common:actions.insert";
const UPDATE_INVENTORY_STOCK = "common:stoichiometry.inventoryUpdate.updateInventoryStock";
const INVENTORY_UPDATE_DIALOG = "common:stoichiometry.inventoryUpdate.dialogTitle";
const YIELD_EXCESS = "common:stoichiometry.table.columns.yieldExcess";

/**
 * Retriable locator for a raw CSS selector scoped within `root`. CSS is a
 * workaround for the third-party MUI DataGrid: a cell's column identity lives
 * only in its `data-field`/`aria-colindex` attributes (its accessible name is
 * the cell *value*), which `getByRole` cannot filter on. Vitest has no public
 * CSS locator, so we call the provider's `css=` engine via `.locator()`
 * (protected on the type, present at runtime) instead of registering a global one.
 */
function cssWithin(root: Locator, selector: string): Locator {
  return (root as unknown as { locator(selector: string): Locator }).locator(`css=${selector}`);
}

function clickElement(locator: Locator): void {
  (locator.element() as HTMLElement).click();
}

/**
 * Page object for the stoichiometry table, as mounted by the stories in
 * StoichiometryTable.story.tsx. Encapsulates the locators and user
 * interactions; assertions live in the tests themselves.
 *
 * Lookups prefer semantic locators (rows by a contained cell's value, the edit
 * control by its `spinbutton` role); `cellByField` is the one CSS exception (see
 * `cssWithin`).
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
          dialog.textContent?.includes(LOADING_MOLECULE_INFO),
        );
        if (!(table && !loading)) {
          throw new Error("Stoichiometry table not loaded yet");
        }
      },
      { timeout: 10000, interval: 50 },
    );
  }

  get moleculeInfoLoadingDialog(): Locator {
    return page.getByRole("dialog").filter({ hasText: LOADING_MOLECULE_INFO });
  }

  /**
   * Data rows of the grid (excluding the column-header row). Data rows contain
   * `gridcell`s; the header row contains `columnheader`s, so filtering on the
   * presence of a gridcell selects the data rows without a CSS attribute query.
   */
  dataRows(): Locator {
    return this.table.getByRole("row").filter({ has: page.getByRole("gridcell") });
  }

  // Addressed by `data-field` (readable and robust to column virtualization,
  // unlike a positional `.nth(colindex)`). See `cssWithin` for why CSS is needed.
  cellByField(row: Locator, field: string): Locator {
    return cssWithin(row, `[role="gridcell"][data-field="${field}"]`);
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

  /**
   * Returns a stable, retriable locator for the data row containing a cell with
   * the given (unique) value, e.g. a molecule name. A cell's accessible name is
   * its rendered value, so we filter the data rows to the one holding a gridcell
   * with that name. (MUI exposes the row id only via `data-id`, never in the
   * accessibility tree, so the record-id route would have to be a CSS query.)
   */
  rowByCellValue(cellValue: string): Locator {
    return this.dataRows().filter({ has: page.getByRole("gridcell", { name: cellValue, exact: true }) });
  }

  cell(compoundName: string, columnHeaderText: string): Locator {
    const field = this.columnField(columnHeaderText);
    const row = this.rowByCellValue(compoundName);
    return this.cellByField(row, field);
  }

  yieldCell(rowIndex: number): Locator {
    const field = this.columnField(YIELD_EXCESS);
    return this.cellByField(this.dataRows().nth(rowIndex), field);
  }

  async editCell({ row, column, value }: { row: number; column: string; value: string }): Promise<void> {
    const field = this.columnField(column);
    const targetCell = this.cellByField(this.dataRows().nth(row), field);

    await targetCell.click();
    await userEvent.keyboard("{Enter}");

    // The editable stoichiometry columns are numeric, so MUI renders the edit
    // control as `<input type="number">` (role "spinbutton") inside the cell.
    const input = targetCell.getByRole("spinbutton");
    await input.fill(value);
    await userEvent.keyboard("{Enter}");
  }

  limitingReagentRadio(name: string): Locator {
    return this.rowByCellValue(name).getByRole("radio", {
      name: "common:stoichiometry.table.aria.selectLimitingReagent",
    });
  }

  async selectLimitingReagent(name: string): Promise<void> {
    await this.limitingReagentRadio(name).click();
  }

  get exportButton(): Locator {
    return page.getByRole("button", { name: "common:actions.export" });
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
      await page.getByRole("menuitem", { name: EXPORT_TO_CSV }).click();
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
    return page.getByRole("button", { name: ADD_CHEMICAL });
  }

  get pubChemMenuItem(): Locator {
    return page.getByRole("menuitem", {
      name: PUBCHEM_MENU_ITEM,
    });
  }

  get galleryMenuItem(): Locator {
    return page.getByRole("menuitem", {
      name: GALLERY_MENU_ITEM,
    });
  }

  get manualEntryMenuItem(): Locator {
    return page.getByRole("menuitem", {
      name: MANUAL_MENU_ITEM,
    });
  }

  async openAddChemicalMenu(): Promise<void> {
    clickElement(this.addChemicalButton);
  }

  get pubChemDialog(): Locator {
    return page.getByText(PUBCHEM_DIALOG);
  }

  get galleryDialog(): Locator {
    return page.getByRole("dialog", { name: GALLERY_DIALOG });
  }

  get manualSmilesDialog(): Locator {
    return page.getByRole("dialog", { name: MANUAL_DIALOG });
  }

  async searchPubChem(compound: string): Promise<void> {
    await page.getByPlaceholder(PUBCHEM_NAME_CAS_PLACEHOLDER).fill(compound);
    await page.getByRole("button", { name: "common:actions.search" }).click();
  }

  openPubChemSource(): void {
    clickElement(this.pubChemMenuItem);
  }

  openManualSource(): void {
    clickElement(this.manualEntryMenuItem);
  }

  openGallerySource(): void {
    clickElement(this.galleryMenuItem);
  }

  async insertPubChemResult(): Promise<void> {
    await page.getByRole("button", { name: INSERT }).click();
  }

  async enterManualSmiles({ smiles, name }: { smiles: string; name: string }): Promise<void> {
    await page.getByRole("textbox", { name: "common:stoichiometry.addReagent.name" }).fill(name);
    await page.getByRole("textbox", { name: "common:stoichiometry.addReagent.smilesString" }).fill(smiles);
  }

  async addManualReagent(): Promise<void> {
    await page.getByRole("button", { name: ADD_CHEMICAL }).click();
  }

  async selectGalleryFile(fileName: RegExp): Promise<void> {
    await page.getByRole("gridcell", { name: fileName }).click();
  }

  async addSelectedGalleryFiles(): Promise<void> {
    await page.getByRole("button", { name: "common:actions.add" }).click();
  }

  get updateInventoryStockButton(): Locator {
    return page.getByRole("button", { name: UPDATE_INVENTORY_STOCK });
  }

  async clickUpdateInventoryStock(): Promise<void> {
    await this.updateInventoryStockButton.click();
  }

  addInventoryLinkButton(molecule: string): Locator {
    return this.inventoryLinkCell(molecule).getByRole("button", {
      name: "common:stoichiometry.inventoryLink.addForMolecule",
    });
  }

  removeInventoryLinkButton(molecule: string): Locator {
    return this.inventoryLinkCell(molecule).getByRole("button", {
      name: "common:stoichiometry.inventoryLink.removeForMolecule",
    });
  }

  inventoryPickerDialog(molecule?: string): Locator {
    return page.getByRole("dialog", {
      name: molecule
        ? `common:stoichiometry.inventoryLink.pickerTitle`
        : "common:stoichiometry.inventoryLink.pickerTitle",
    });
  }

  async openInventoryPickerFor(molecule: string): Promise<void> {
    await this.addInventoryLinkButton(molecule).click();
  }

  async closeInventoryPicker(): Promise<void> {
    await page.getByRole("button", { name: "common:actions.cancel" }).click();
  }

  inventoryLinkCell(compoundName: string): Locator {
    return this.cell(compoundName, "common:stoichiometry.table.columns.inventoryLink");
  }

  insufficientStockIcon(container: Locator): Locator {
    return container.getByRole("img", { name: "common:stoichiometry.inventoryLink.insufficientStock" });
  }
}

/**
 * Page object for the "Update Inventory Stock" dialog opened from the
 * stoichiometry table toolbar.
 */
export class InventoryUpdateDialogPage {
  readonly dialog: Locator = page.getByRole("dialog", {
    name: INVENTORY_UPDATE_DIALOG,
  });

  columnHeader(name: string): Locator {
    return this.dialog.getByRole("columnheader", { name, exact: true });
  }

  checkbox(moleculeName: string): Locator {
    return this.dialog.getByRole("checkbox", { name: moleculeName });
  }

  /**
   * The molecule's main row. Each molecule row carries an `<h3>` heading with
   * the molecule name (the optional helper sub-row does not), so filtering rows
   * by the presence of that heading selects the molecule row semantically.
   */
  moleculeRow(moleculeName: string): Locator {
    return this.dialog.getByRole("row").filter({ has: page.getByRole("heading", { name: moleculeName, exact: true }) });
  }

  /**
   * A metric cell ("In Stock" / "Will Use" / "Remaining") for a molecule.
   *
   * The cells are plain `<td>`s (`role="cell"`) whose column identity is only
   * conveyed by the table's column headers, not by any per-cell ARIA. We locate
   * the cell by its position: find the header index for `metricName`, then take
   * the cell at the same position in the molecule row, accounting for any
   * leading row cells (e.g. the checkbox column) that have no matching header.
   */
  metric(moleculeName: string, metricName: string): Locator {
    const headers = this.dialog.getByRole("columnheader").elements();
    const headerIndex = headers.findIndex((h) => h.textContent?.trim() === metricName);
    if (headerIndex < 0) {
      throw new Error(`Column header "${metricName}" not found`);
    }
    const row = this.moleculeRow(moleculeName);
    const cellOffset = row.getByRole("cell").elements().length - headers.length;
    return row.getByRole("cell").nth(headerIndex + cellOffset);
  }
}
