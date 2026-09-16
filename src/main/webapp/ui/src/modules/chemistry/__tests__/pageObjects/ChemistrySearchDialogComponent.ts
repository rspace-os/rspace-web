import type { Locator, Page } from "@playwright/test";
import { KetcherDialogComponent } from "./KetcherDialogComponent";

export type ChemicalSearchType = "EXACT" | "SUBSTRUCTURE";

export class ChemistrySearchDialogComponent {
  readonly draw: KetcherDialogComponent;
  readonly searchButton: Locator;
  readonly exactRadio: Locator;
  readonly substructureRadio: Locator;
  readonly multipleMoleculesWarning: Locator;
  readonly resultsDialog: Locator;
  readonly noResultsText: Locator;

  constructor(private readonly page: Page) {
    this.draw = new KetcherDialogComponent(page, "Chemistry Search");
    this.searchButton = this.draw.root.getByRole("button", { name: "Search" });
    this.exactRadio = this.draw.root.getByRole("radio", { name: "Exact" });
    this.substructureRadio = this.draw.root.getByRole("radio", { name: "Substructure" });
    this.multipleMoleculesWarning = this.page.getByRole("alert").filter({ hasText: "supports a single molecule" });
    this.resultsDialog = page.getByRole("dialog", { name: "Chemical Search", exact: true });
    this.noResultsText = this.resultsDialog.getByText("No Search Results");
  }

  async waitForOpen(): Promise<void> {
    await this.draw.waitForOpen();
  }

  async setSearchType(type: ChemicalSearchType): Promise<void> {
    await (type === "EXACT" ? this.exactRadio : this.substructureRadio).check();
  }

  async setMoleculeFromSmiles(smiles: string): Promise<void> {
    await this.draw.setMoleculeFromSmiles(smiles);
  }

  async search(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (r) => r.request().method() === "POST" && new URL(r.url()).pathname === "/chemical/search",
      ),
      this.searchButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /chemical/search failed: ${response.status()} ${response.statusText()}`);
    }
    await this.resultsDialog.waitFor({ state: "visible" });
    await this.resultsDialog.getByText("Loading...").waitFor({ state: "detached" });
  }

  async searchExpectingMultipleMoleculesWarning(): Promise<void> {
    await this.searchButton.click();
    await this.multipleMoleculesWarning.waitFor({ state: "visible" });
  }

  resultRow(recordName: string): Locator {
    return this.resultsDialog.getByRole("row", { name: recordName });
  }

  async resultCount(): Promise<number> {
    return this.resultsDialog
      .getByRole("row")
      .filter({ has: this.page.getByRole("gridcell") })
      .count();
  }

  async close(): Promise<void> {
    await this.resultsDialog.getByRole("button", { name: "Close" }).click();
    await this.resultsDialog.waitFor({ state: "detached" });
  }
}
