import type { Locator, Page } from "@playwright/test";

export class VideoEmbedDialogComponent {
  readonly root: Locator;
  readonly urlInput: Locator;
  readonly feedback: Locator;
  readonly insertButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Embed video" });
    this.urlInput = this.root.getByRole("textbox", { name: "Video URL" });
    this.feedback = this.root.locator("#rspace-video-url-feedback");
    this.insertButton = this.root.getByRole("button", { name: "Insert", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async embedFromUrl(url: string): Promise<void> {
    await this.urlInput.fill(url);
    await this.insertButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
