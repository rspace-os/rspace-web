import { BasePage } from "./BasePage";

/**
 * Structured document in view mode.
 *
 * `path` is the base URL pattern — navigate to a specific document via the
 * workspace, not by calling `open()` directly (no fixed document ID here).
 *
 */
export class DocumentPage extends BasePage {
  readonly path = "/workspace/editor/structuredDocument";

  async isLoaded(): Promise<void> {
    await this.page.waitForURL(/\/workspace\/editor\/structuredDocument\//);
    await this.page
      .locator("#viewGreenStatus, #viewAmberStatus, #viewAmberStatusReadPermission, #viewRedStatus")
      .waitFor({ state: "visible" });
  }
}
