import type { Locator, Page } from "@playwright/test";

/**
 * Entry navigation on the notebook view (`/notebookEditor/`) — entry
 * thumbnails, prev/next
 *
 */
export class NotebookEntryStrip {
  readonly entryCounter: Locator;
  readonly nextButton: Locator;
  readonly prevButton: Locator;
  readonly searchInput: Locator;
  readonly searchSubmit: Locator;

  constructor(private readonly page: Page) {
    this.entryCounter = page.getByText(/^Entry \d+ of \d+$/);
    this.nextButton = page.locator("#nextEntryButton");
    this.prevButton = page.locator("#prevEntryButton");
    this.searchInput = page.getByRole("textbox", { name: "Search Notebook..." });
    this.searchSubmit = page.getByRole("button", { name: "Search" });
  }

  /** An entry's thumbnail, by its exact name — `title="Name: '{name}'"` on the thumbnail. */
  entryThumbnail(name: string): Locator {
    return this.page.getByTitle(`Name: '${name}'`);
  }

  async clickEntry(name: string): Promise<void> {
    await this.entryThumbnail(name).click();
  }

  async next(): Promise<void> {
    await this.nextButton.click();
  }

  async previous(): Promise<void> {
    await this.prevButton.click();
  }

  async getEntryCount(): Promise<{ current: number; total: number }> {
    const text = await this.entryCounter.innerText();
    const match = text.match(/Entry (\d+) of (\d+)/);
    if (!match) {
      throw new Error(`getEntryCount: could not parse entry counter text "${text}"`);
    }
    return { current: Number(match[1]), total: Number(match[2]) };
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await this.searchSubmit.click();
  }
}
