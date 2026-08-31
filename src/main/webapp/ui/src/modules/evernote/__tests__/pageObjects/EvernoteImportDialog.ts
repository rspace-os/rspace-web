import type { Locator, Page } from "@playwright/test";

export class EvernoteImportDialog {
  readonly root: Locator;
  readonly fileInput: Locator;
  readonly importButton: Locator;
  readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import from Evernote" });
    this.fileInput = this.root.getByLabel(/Please choose 1 or more Evernote XML files to import/);
    this.importButton = this.root.getByRole("button", { name: "Import", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.fileInput.waitFor({ state: "visible" });
  }

  async importFile(filePath: string): Promise<void> {
    await this.fileInput.setInputFiles(filePath);
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (r) => r.request().method() === "POST" && new URL(r.url()).pathname.includes("/ajax/createFromWord/"),
      ),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST .../ajax/createFromWord failed: ${response.status()} ${response.statusText()}`);
    }
  }
}
