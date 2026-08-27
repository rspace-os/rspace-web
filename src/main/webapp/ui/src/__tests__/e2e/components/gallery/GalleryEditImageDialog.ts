import type { Locator, Page } from "@playwright/test";

export class GalleryEditImageDialog {
  readonly root: Locator;
  readonly rotateClockwiseButton: Locator;
  readonly rotateCounterClockwiseButton: Locator;
  readonly cancelButton: Locator;
  readonly saveAsNewImageButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Edit Image" });
    this.rotateClockwiseButton = this.root.getByRole("button", { name: "rotate clockwise" });
    this.rotateCounterClockwiseButton = this.root.getByRole("button", { name: "rotate counter clockwise" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.saveAsNewImageButton = this.root.getByRole("button", { name: "Save as new image" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async saveAsNewImage(): Promise<void> {
    await this.saveAsNewImageButton.click();
    await this.root.waitFor({ state: "hidden" });
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
