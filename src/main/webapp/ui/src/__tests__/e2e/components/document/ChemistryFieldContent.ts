import { expect, type FrameLocator, type Locator, type Page } from "@playwright/test";
import { KetcherDialogComponent } from "@/modules/chemistry/__tests__/pageObjects/KetcherDialogComponent";
import { StoichiometryDialogComponent } from "@/modules/stoichiometry/__tests__/pageObjects/StoichiometryDialogComponent";

// Matches a chemistry image in both edit-mode (FrameLocator) and view-mode (plain Locator) field HTML.
export const CHEM_IMAGE_SELECTOR = 'img[src*="sourceType=CHEM"]';

export class ChemistryFieldContent {
  constructor(
    private readonly page: Page,
    private readonly frame: FrameLocator,
    private readonly container: Locator,
  ) {}

  get chemElement(): Locator {
    return this.frame.locator(CHEM_IMAGE_SELECTOR);
  }

  get standaloneStoichiometryTableElement(): Locator {
    return this.frame.locator('[data-stoichiometry-table-only="true"]');
  }

  /** Waits until a newly inserted standalone table has received its persisted table id. */
  async waitForStandaloneStoichiometryTable(): Promise<void> {
    await this.standaloneStoichiometryTableElement.waitFor({ state: "attached" });
    await expect(this.standaloneStoichiometryTableElement).toHaveAttribute("data-stoichiometry-table", /.+/);
  }

  async openKetcherEditDialog(): Promise<KetcherDialogComponent> {
    await this.chemElement.click();
    await this.page.getByRole("button", { name: "Edit structural formula in Ketcher" }).click();
    const dialog = new KetcherDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }

  async openKetcherViewer(): Promise<KetcherDialogComponent> {
    await this.chemElement.click();
    await this.page.getByRole("button", { name: "View structure in Ketcher" }).click();
    const dialog = new KetcherDialogComponent(this.page, "Ketcher Chemical Viewer (Read-Only)");
    await dialog.waitForOpen();
    return dialog;
  }

  async openStoichiometryDialog(): Promise<StoichiometryDialogComponent> {
    await this.chemElement.click();
    await this.page.getByRole("button", { name: "View stoichiometry" }).click();
    const dialog = new StoichiometryDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }

  async openStandaloneStoichiometryDialog(): Promise<StoichiometryDialogComponent> {
    await this.container.getByRole("button", { name: "Insert reaction table" }).click();
    const dialog = new StoichiometryDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }
}
