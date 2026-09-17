import type { Locator, Page } from "@playwright/test";
import { GalleryPickerComponent } from "@/__tests__/e2e/components/shared/GalleryPickerComponent";
import { PickInventoryItemDialogComponent } from "./PickInventoryItemDialogComponent";
import { StockUpdateDialogComponent } from "./StockUpdateDialogComponent";

export type StoichiometryColumnField =
  | "name"
  | "role"
  | "limitingReagent"
  | "coefficient"
  | "molecularWeight"
  | "mass"
  | "moles"
  | "actualAmount"
  | "actualMoles"
  | "actualYield"
  | "notes";

/** The "Reaction Table" dialog opened via a chem field's "View stoichiometry" context-toolbar button. */
export class StoichiometryDialogComponent {
  readonly root: Locator;
  readonly calculateButton: Locator;
  readonly grid: Locator;
  readonly addChemicalButton: Locator;
  readonly saveChangesButton: Locator;
  readonly updateInventoryStockButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Reaction Table" });
    this.calculateButton = this.root.getByRole("button", { name: "Calculate Stoichiometry" });
    this.grid = this.root.getByRole("grid");
    this.addChemicalButton = this.root.getByRole("button", { name: "Add Chemical", exact: true });
    this.saveChangesButton = this.root.getByRole("button", { name: "Save Changes", exact: true });
    this.updateInventoryStockButton = this.root.getByRole("button", { name: "Update Inventory Stock", exact: true });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.root.getByText("Loading stoichiometry table...").waitFor({ state: "hidden" });
  }

  async calculate(): Promise<void> {
    await this.calculateButton.click();
    await this.grid.waitFor({ state: "visible" });
  }

  async saveChanges(): Promise<void> {
    await Promise.all([
      this.page.waitForResponse(
        (res) => res.request().method() === "PUT" && new URL(res.url()).pathname.endsWith("/stoichiometry"),
      ),
      this.saveChangesButton.click(),
    ]);
  }

  async openUpdateInventoryStockDialog(): Promise<StockUpdateDialogComponent> {
    await this.updateInventoryStockButton.click();
    const dialog = new StockUpdateDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }

  row(compoundName: string): Locator {
    return this.grid.getByRole("row").filter({
      has: this.page.getByRole("gridcell", { name: compoundName, exact: true }),
    });
  }

  async hasCompound(compoundName: string): Promise<boolean> {
    return (await this.row(compoundName).count()) > 0;
  }

  /** MUI DataGrid cells expose no semantic per-column role; `data-field` is a stable, DataGrid-owned attribute. */
  private cell(compoundName: string, field: StoichiometryColumnField): Locator {
    return this.row(compoundName).locator(`[data-field="${field}"]`);
  }

  async getCellText(compoundName: string, field: StoichiometryColumnField): Promise<string> {
    return (await this.cell(compoundName, field).innerText()).trim();
  }

  async editCell(compoundName: string, field: StoichiometryColumnField, value: string): Promise<void> {
    const cell = this.cell(compoundName, field);
    await cell.dblclick();
    const input = cell.getByRole("textbox").or(cell.locator("input"));
    await input.fill(value);
    await input.press("Enter");
  }

  private async openAddChemicalMenu(): Promise<void> {
    await this.addChemicalButton.click();
    await this.page.getByRole("menu", { name: "add chemical menu" }).waitFor({ state: "visible" });
  }

  async addFromPubChem(compoundName: string): Promise<void> {
    await this.openAddChemicalMenu();
    await this.page.getByRole("menuitem", { name: "PubChem" }).click();
    const dialog = this.page.getByRole("dialog", { name: "Insert from PubChem" });
    await dialog.waitFor({ state: "visible" });
    await dialog.getByRole("textbox", { name: "Enter a compound" }).fill(compoundName);
    await dialog.getByRole("button", { name: "Search", exact: true }).click();
    const result = dialog.getByRole("region", { name: compoundName });
    await result.waitFor({ state: "visible" });
    await result.getByRole("checkbox").check();
    await dialog.getByRole("button", { name: "Insert", exact: true }).click();
    await dialog.waitFor({ state: "detached" });
  }

  async addFromGallery(filePath: string, fileName: string): Promise<void> {
    await this.openAddChemicalMenu();
    await this.page.getByRole("menuitem", { name: "Gallery" }).click();
    const picker = new GalleryPickerComponent(this.page);
    await picker.waitForOpen();
    await picker.goToSection("Chemistry");
    await picker.uploadFile(filePath, fileName);
    await picker.selectItem(fileName);
    await picker.add();
  }

  async addManually(name: string, smiles: string): Promise<void> {
    await this.openAddChemicalMenu();
    await this.page.getByRole("menuitem", { name: "Manually" }).click();
    const dialog = this.page.getByRole("dialog", { name: "Add New Chemical" });
    await dialog.waitFor({ state: "visible" });
    await dialog.getByRole("textbox", { name: "Name" }).fill(name);
    await dialog.getByRole("textbox", { name: "SMILES String" }).fill(smiles);
    await dialog.getByRole("button", { name: "Add Chemical", exact: true }).click();
    await dialog.waitFor({ state: "detached" });
  }

  async openAddInventoryLinkDialog(compoundName: string): Promise<PickInventoryItemDialogComponent> {
    await this.row(compoundName)
      .getByRole("button", { name: `Add inventory link for ${compoundName}` })
      .click();
    const dialog = new PickInventoryItemDialogComponent(this.page, compoundName);
    await dialog.waitForOpen();
    return dialog;
  }

  async removeInventoryLink(compoundName: string): Promise<void> {
    await this.row(compoundName)
      .getByRole("button", { name: `Remove inventory link for ${compoundName}` })
      .click();
  }

  async hasInventoryLink(compoundName: string): Promise<boolean> {
    return (
      (await this.row(compoundName)
        .getByRole("button", { name: `Remove inventory link for ${compoundName}` })
        .count()) > 0
    );
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Close", exact: true }).click();
    await this.root.waitFor({ state: "detached" });
  }
}
