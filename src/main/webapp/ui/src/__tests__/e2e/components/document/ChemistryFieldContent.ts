import type { FrameLocator, Locator, Page } from "@playwright/test";
import { KetcherDialogComponent } from "@/modules/chemistry/__tests__/pageObjects/KetcherDialogComponent";
import { StoichiometryDialogComponent } from "@/tinyMCE/stoichiometry/__tests__/pageObjects/StoichiometryDialogComponent";

export class ChemistryFieldContent {
  constructor(
    private readonly page: Page,
    private readonly frame: FrameLocator,
    private readonly container: Locator,
  ) {}

  get chemElement(): Locator {
    return this.frame.locator('img[src*="sourceType=CHEM"]');
  }

  get standaloneStoichiometryTableElement(): Locator {
    return this.frame.locator('[data-stoichiometry-table-only="true"]');
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
