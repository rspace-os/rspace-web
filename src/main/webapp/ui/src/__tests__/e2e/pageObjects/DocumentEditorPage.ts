import type { Locator, Page } from "@playwright/test";
import { PubchemDialogComponent } from "../components/pubchem/PubchemDialogComponent";
import { DocumentPage } from "./DocumentPage";

/**
 * Structured document in edit mode. Extends DocumentPage and overrides
 * `isLoaded()` to wait for `#editingStatus` (verified in status.tag).
 *
 */
export class DocumentEditorPage extends DocumentPage {
  readonly pubchemDialog: PubchemDialogComponent;

  constructor(page: Page) {
    super(page);
    this.pubchemDialog = new PubchemDialogComponent(page);
  }

  override async isLoaded(): Promise<void> {
    await this.page.waitForURL(/\/workspace\/editor\/structuredDocument\//);
    await this.page.locator("#editingStatus").waitFor({ state: "visible" });
  }

  get pubchemToolbarButton(): Locator {
    return this.page.getByRole("button", { name: "Insert PubChem Compound" });
  }

  async openPubchemDialog(): Promise<PubchemDialogComponent> {
    await this.pubchemToolbarButton.click();
    await this.pubchemDialog.waitForOpen();
    return this.pubchemDialog;
  }
}
