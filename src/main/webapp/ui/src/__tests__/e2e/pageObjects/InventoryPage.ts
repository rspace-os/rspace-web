import type { Locator, Page } from "@playwright/test";
import { FieldmarkDialogComponent } from "@/modules/fieldmark/__tests__/pageObjects/FieldmarkDialogComponent";
import { BasePage } from "./BasePage";

/**
 * The Inventory page at `/inventory`.
 *
 * Covers sidebar navigation and the Create menu. Opening specific dialogs
 * (e.g. Fieldmark import) returns the appropriate component instance.
 */
export class InventoryPage extends BasePage {
  readonly path = "/inventory";

  private readonly sidebar: Locator;
  readonly createButton: Locator;

  constructor(page: Page) {
    super(page);
    this.sidebar = page.locator('[aria-label="Inventory Sidebar Navigation"]');
    this.createButton = this.sidebar.getByRole("button", { name: "Create" });
  }

  async isLoaded(): Promise<void> {
    await this.sidebar.waitFor({ state: "visible" });
  }

  /**
   * Opens the Create menu and clicks the Fieldmark menu item.
   * Returns a FieldmarkDialogComponent ready to interact with.
   */
  async openFieldmarkImport(): Promise<FieldmarkDialogComponent> {
    await this.createButton.click();
    await this.page.getByRole("menuitem", { name: "Fieldmark" }).click();
    const dialog = new FieldmarkDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }
}
