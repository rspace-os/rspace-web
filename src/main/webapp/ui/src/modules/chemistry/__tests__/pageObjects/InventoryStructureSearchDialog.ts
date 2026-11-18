import type { Locator, Page } from "@playwright/test";
import type { ChemicalSearchType } from "./ChemistrySearchDialogComponent";
import { KetcherDialogComponent } from "./KetcherDialogComponent";

/** The structure editor opened by Inventory search's "Chemical" filter button. */
export class InventoryStructureSearchDialog {
  readonly draw: KetcherDialogComponent;
  readonly searchButton: Locator;
  private readonly exactRadio: Locator;
  private readonly substructureRadio: Locator;

  constructor(private readonly page: Page) {
    this.draw = new KetcherDialogComponent(page, "Search Inventory by chemical structure");
    this.searchButton = this.draw.root.getByRole("button", { name: "Search" });
    this.exactRadio = this.draw.root.getByRole("radio", { name: "Exact" });
    this.substructureRadio = this.draw.root.getByRole("radio", { name: "Substructure" });
  }

  async waitForOpen(): Promise<void> {
    await this.draw.waitForOpen();
  }

  async setSearchType(type: ChemicalSearchType): Promise<void> {
    await (type === "EXACT" ? this.exactRadio : this.substructureRadio).check();
  }

  /** Draws the structure on the canvas (set programmatically, as the other chemistry specs do). */
  async drawSmiles(smiles: string): Promise<void> {
    await this.draw.setMoleculeFromSmiles(smiles);
  }

  /** Pastes the structure as SMILES text through Ketcher's own paste dialog. */
  async pasteSmiles(smiles: string): Promise<void> {
    await this.draw.pasteStructureText(smiles);
  }

  /** Runs the structure search and waits until its results have arrived and the editor has closed. */
  async search(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse((r) => {
        const url = new URL(r.url());
        return (
          r.request().method() === "GET" &&
          url.pathname.endsWith("/api/inventory/v1/search") &&
          url.searchParams.has("chemQuery")
        );
      }),
      this.searchButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Inventory structure search failed: ${response.status()} ${response.statusText()}`);
    }
    await this.draw.root.waitFor({ state: "detached" });
  }
}
