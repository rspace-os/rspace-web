import type { Locator, Page } from "@playwright/test";

export type SearchType = "Name/CAS" | "SMILES";

export class PubchemDialogComponent {
  readonly root: Locator;
  readonly searchTypeCombobox: Locator;
  readonly searchButton: Locator;
  readonly resultsRegion: Locator;
  readonly cancelButton: Locator;
  readonly importSelectedButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import from PubChem" });
    this.searchTypeCombobox = this.root.getByRole("combobox", {
      name: "Search type",
    });
    this.searchButton = this.root.getByRole("button", { name: "Search" });
    this.resultsRegion = this.root.getByRole("region", {
      name: "Search Results",
    });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.importSelectedButton = this.root.getByRole("button", {
      name: "Import Selected",
    });
  }

  searchInput(type: SearchType = "Name/CAS"): Locator {
    const name = type === "SMILES" ? "Enter a SMILES" : "Enter a compound";
    return this.root.getByRole("textbox", { name });
  }

  resultCard(compoundName: string): Locator {
    return this.resultsRegion.getByRole("region", { name: compoundName });
  }

  resultCardField(compoundName: string, value: string): Locator {
    return this.resultCard(compoundName).getByRole("definition").filter({ hasText: value });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.resultsRegion.getByText("Enter a search term").waitFor({ state: "visible" });
  }

  async setSearchType(type: SearchType): Promise<void> {
    await this.searchTypeCombobox.click();
    await this.page.getByRole("option", { name: type }).click();
    await this.searchTypeCombobox.filter({ hasText: type }).waitFor({ state: "visible" });
  }

  async search(term: string, type: SearchType = "Name/CAS"): Promise<void> {
    if (type !== "Name/CAS") {
      await this.setSearchType(type);
    }
    await this.searchInput(type).fill(term);
    const [response] = await Promise.all([
      this.page.waitForResponse((r) => r.url().includes("/api/v1/pubchem/search")),
      this.searchButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /api/v1/pubchem/search failed: ${response.status()} ${response.statusText()}`);
    }

    await this.resultsRegion
      .getByText(/No compounds found|Enter a search term/)
      .or(this.resultsRegion.getByRole("region").first())
      .waitFor({ timeout: 15_000 });
  }

  async importCompound(compoundName: string): Promise<void> {
    await this.resultCard(compoundName).getByRole("checkbox", { name: "Select compound" }).check();
    await this.importSelectedButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async close(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
