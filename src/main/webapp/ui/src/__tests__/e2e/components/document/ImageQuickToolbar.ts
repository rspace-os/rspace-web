import type { Locator, Page } from "@playwright/test";
import { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";

export class ImageQuickToolbar {
  readonly imageDetailsButton: Locator;
  readonly downloadButton: Locator;
  readonly resizeButton: Locator;
  readonly sketchButton: Locator;

  constructor(private readonly page: Page) {
    this.imageDetailsButton = page.getByRole("button", { name: "Image details" });
    this.downloadButton = page.getByRole("button", { name: "Download as image" });
    this.resizeButton = page.getByRole("button", { name: "Resize image" });
    this.sketchButton = page.getByRole("button", { name: "Sketch tool" });
  }

  async waitForOpen(): Promise<void> {
    await this.imageDetailsButton.waitFor({ state: "visible" });
  }

  async openImageDetails(): Promise<RecordInfoDialog> {
    await this.imageDetailsButton.click();
    const dialog = new RecordInfoDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }
}
