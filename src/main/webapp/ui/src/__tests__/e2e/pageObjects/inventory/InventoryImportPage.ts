import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export type InventoryImportRecordType = "CONTAINERS" | "SAMPLES" | "SUBSAMPLES";

export class InventoryImportPage extends BasePage {
  readonly path = "/inventory/import";

  private readonly main: Locator;
  readonly importButton: Locator;

  readonly blockedWarning: Locator;

  constructor(page: Page) {
    super(page);
    this.main = page.getByRole("main", { name: "Import main content" });
    this.importButton = this.main.getByRole("button", { name: "Import " });
    this.blockedWarning = this.main.getByText("csv documents cannot be imported");
  }

  async isLoaded(): Promise<void> {
    await this.main.getByRole("heading", { level: 3, name: "Upload CSV File" }).waitFor({ state: "visible" });
  }

  async selectTab(tab: InventoryImportRecordType): Promise<void> {
    const label = tab.charAt(0) + tab.slice(1).toLowerCase();
    await this.main.getByRole("tab", { name: label, exact: true }).click();
  }

  async uploadCsv(filePath: string): Promise<void> {
    const fileChooserPromise = this.page.waitForEvent("filechooser");
    await this.main.getByRole("button", { name: "CSV File" }).click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles(filePath);
  }

  private columnCheckbox(columnName: string): Locator {
    return this.main.getByRole("checkbox", { name: `Select mapping for ${columnName}`, exact: true });
  }

  async setColumnChecked(columnName: string, checked: boolean): Promise<void> {
    const checkbox = this.columnCheckbox(columnName);
    if ((await checkbox.isChecked()) !== checked) {
      await checkbox.click();
    }
  }

  async clickImport(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.request().method() === "POST" && new URL(res.url()).pathname.endsWith("/import/importFiles"),
      ),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Inventory CSV import failed: ${response.status()} ${response.statusText()}`);
    }
  }
}
