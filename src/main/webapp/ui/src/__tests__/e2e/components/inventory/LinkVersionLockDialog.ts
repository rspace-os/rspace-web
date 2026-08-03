import { expect, type Locator, type Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";

export class LinkVersionLockDialog {
  readonly root: Locator;
  private readonly confirmButton: Locator;
  private readonly cancelButton: Locator;

  constructor(page: Page, targetGlobalId: string) {
    this.root = page.getByRole("dialog", { name: `Version history for ${targetGlobalId}` });
    this.confirmButton = this.root.getByRole("button", { name: "Lock to selected version" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectLatest(): Promise<void> {
    const radio = this.root.getByRole("radio", { name: "Latest", exact: true });
    await radio.click();
    await expect(radio).toBeChecked();
  }

  async selectVersion(version: number): Promise<void> {
    const radio = this.root.getByRole("radio", { name: `Version ${version}`, exact: true });
    await radio.click();
    await expect(radio).toBeChecked();
  }

  async lockToSelectedVersion(): Promise<void> {
    await clickAndWaitDetached(this.confirmButton, this.root);
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
