import type { Locator, Page } from "@playwright/test";

export class PidinstImportDialog {
  readonly root: Locator;
  private readonly searchInput: Locator;
  private readonly searchButton: Locator;
  private readonly importButton: Locator;
  private readonly closeButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import Instrument from PIDINST", exact: true });
    this.searchInput = this.root.getByRole("textbox", { name: "Search the registry" });
    this.searchButton = this.root.getByRole("button", { name: "Search", exact: true });
    this.importButton = this.root.getByRole("button", { name: "Import", exact: true });
    this.closeButton = this.root.getByRole("button", { name: "Close", exact: true });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async search(query: string): Promise<void> {
    const response = this.page.waitForResponse(
      (r) => r.request().method() === "GET" && new URL(r.url()).pathname === "/api/inventory/v1/pidinst/search",
    );
    await this.searchInput.fill(query);
    await this.searchButton.click();
    const searchResponse = await response;
    if (!searchResponse.ok()) {
      throw new Error(
        `PIDINST search for "${query}" failed: ${searchResponse.status()} ${searchResponse.statusText()}`,
      );
    }
  }

  resultRow(name: string): Locator {
    return this.root.getByRole("radio", { name: `Select record: ${name}` });
  }

  async selectResult(name: string): Promise<void> {
    await this.resultRow(name).click();
  }

  get preview(): Locator {
    return this.root.getByRole("region", { name: "Selected record" });
  }

  previewField(label: string): Locator {
    const term = this.page.getByRole("term").filter({ hasText: new RegExp(`^${label}$`) });
    return this.preview.locator("div").filter({ has: term }).getByRole("definition");
  }

  /** The preview's already-linked notice, naming the instrument that holds the PID. */
  alreadyLinkedBannerFor(globalId: string): Locator {
    return this.alreadyLinkedBanner.filter({ hasText: globalId });
  }

  get alreadyLinkedBanner(): Locator {
    return this.preview
      .getByRole("alert")
      .filter({ hasText: "This PID is already linked to an instrument in RSpace:" });
  }

  alreadyLinkedValidationWarning(globalId: string): Locator {
    return this.page.getByRole("alert").filter({ hasText: `This PID is already linked to instrument ${globalId}.` });
  }

  async clickImport(): Promise<void> {
    await this.importButton.click();
  }

  async dismissValidationWarning(globalId: string): Promise<void> {
    await this.page.keyboard.press("Escape");
    await this.alreadyLinkedValidationWarning(globalId).waitFor({ state: "hidden" });
  }

  async close(): Promise<void> {
    await this.closeButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
