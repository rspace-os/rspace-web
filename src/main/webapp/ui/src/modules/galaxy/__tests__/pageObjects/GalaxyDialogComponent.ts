import type { FrameLocator, Locator, Page } from "@playwright/test";

export class GalaxyDialogComponent {
  readonly root: Locator;
  readonly frame: FrameLocator;
  readonly uploadButton: Locator;
  readonly closeButton: Locator;
  readonly uploadingModal: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Use a Galaxy Workflow" });
    this.frame = this.root.frameLocator("iframe");
    this.uploadButton = page.getByRole("button", { name: "Upload To Galaxy" });
    this.closeButton = page.getByRole("button", { name: "Close" }).last();

    this.uploadingModal = this.frame.locator('[aria-label="Please wait, uploading data to Galaxy is in progress"]');
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectServer(alias: string): Promise<void> {
    await this.frame.getByRole("radio", { name: alias }).click();
  }

  async selectAllFiles(): Promise<void> {
    const rowCheckboxes = this.frame.getByRole("checkbox", { name: /^(select|unselect) row$/i });
    const count = await rowCheckboxes.count();
    for (let i = 0; i < count; i++) {
      await rowCheckboxes.nth(i).click();
    }
  }

  async selectServerAndAllFiles(alias: string): Promise<void> {
    await this.selectServer(alias);
    await this.selectAllFiles();
  }

  async upload(): Promise<void> {
    await this.uploadButton.click();
    // The progress modal can disappear before Playwright observes it as visible.
    await this.uploadingModal.waitFor({ state: "hidden", timeout: 60_000 });
  }

  async getResultText(): Promise<string> {
    return this.frame.locator("body").innerText();
  }

  async close(): Promise<void> {
    await this.closeButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
