import type { Locator, Page } from "@playwright/test";

export class FieldmarkDialogComponent {
  readonly root: Locator;
  readonly importButton: Locator;
  readonly closeButton: Locator;

  private readonly toastsRegion: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import from Fieldmark" });
    this.importButton = this.root.getByRole("button", { name: "Import" });
    this.closeButton = this.root.getByRole("button", { name: "Close" });
    this.toastsRegion = page.getByRole("region", {
      name: "alerts.",
      includeHidden: true,
    });
  }

  notebookRadio(name: string): Locator {
    return this.root.getByRole("radio", { name: `Select notebook: ${name}` });
  }

  get successToast(): Locator {
    return this.toastsRegion
      .getByRole("group", { name: "success alert" })
      .filter({ hasText: "Successfully imported notebook." });
  }

  get importingToast(): Locator {
    return this.toastsRegion.getByRole("group", { name: "notice alert" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });

    await this.root.getByRole("columnheader", { name: "Name" }).waitFor({ state: "visible" });
  }

  async selectNotebook(name: string): Promise<void> {
    await this.notebookRadio(name).click();
  }

  async clickImport(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse((r) => r.url().includes("/api/inventory/v1/import/fieldmark/notebook")),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(
        `POST /api/inventory/v1/import/fieldmark/notebook failed: ${response.status()} ${response.statusText()}`,
      );
    }
    await this.root.waitFor({ state: "detached" });
  }
}
