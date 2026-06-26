import type { Locator, Page } from "@playwright/test";

export type SearchType = "Name/CAS" | "SMILES";

/**
 * The "Import from PubChem" MUI dialog. Composed into DocumentEditorPage.
 *
 * Dialog root accessible name comes from the <h3> "Import from PubChem" via
 * aria-labelledby on the MUI Paper element — not the <h2> "PubChem" banner.
 *
 * Key disambiguation: the editor toolbar also has a "Cancel" button, so every
 * button locator here is scoped to `this.root` (the dialog), not `page`.
 *
 * The Search type listbox is a MUI Portal outside the dialog root. Its option
 * locators are page-scoped; see `setSearchType`.
 */
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

  /**
   * The search text input. Its accessible name (= placeholder) changes with
   * the selected search type:
   *   Name/CAS → "Enter a compound name or CAS number"
   *   SMILES   → "Enter a SMILES string"
   */
  searchInput(type: SearchType = "Name/CAS"): Locator {
    const namePattern = type === "SMILES" ? /Enter a SMILES/i : /Enter a compound/i;
    return this.root.getByRole("textbox", { name: namePattern });
  }

  /** Each result from a successful search is an ARIA region named after the compound. */
  resultCard(compoundName: string): Locator {
    return this.resultsRegion.getByRole("region", { name: compoundName });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.resultsRegion.getByText(/Enter a search term/).waitFor({ state: "visible" });
  }

  /**
   * Switches the search type. The listbox is a MUI Portal outside the dialog
   * root so the option locator is page-scoped.
   */
  async setSearchType(type: SearchType): Promise<void> {
    await this.searchTypeCombobox.click();
    await this.page.getByRole("option", { name: type }).click();
    await this.searchTypeCombobox.filter({ hasText: type }).waitFor({ state: "visible" });
  }

  /**
   * Fills the search input and clicks Search, waiting for the backend response
   * before resolving. Switches search type first if `type` is not "Name/CAS".
   */
  async search(term: string, type: SearchType = "Name/CAS"): Promise<void> {
    if (type !== "Name/CAS") {
      await this.setSearchType(type);
    }
    await this.searchInput(type).fill(term);
    await Promise.all([
      this.page.waitForResponse((r) => r.url().includes("/api/v1/pubchem/search") && r.status() === 200),
      this.searchButton.click(),
    ]);
    // Wait until the results region settles into one of three terminal states:
    // no-results message, initial-prompt (shouldn't happen after a search), or
    // at least one result card.
    await this.resultsRegion
      .getByText(/No compounds found|Enter a search term/)
      .or(this.resultsRegion.getByRole("region").first())
      .waitFor({ timeout: 15_000 });
  }

  async importCompound(compoundName: string): Promise<void> {
    // The card sits inside a MUI DialogContent with overflow:scroll, which blocks
    // Playwright's pointer-event hit-test. Scope to the CardActionArea button and
    // bypass the containment check with force:true.
    await this.resultCard(compoundName).getByRole("button").click({ force: true });
    await this.importSelectedButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async close(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
