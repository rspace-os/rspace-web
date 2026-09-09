import type { Locator, Page } from "@playwright/test";

export class NotebookEntryStrip {
  readonly entryCounter: Locator;
  readonly nextButton: Locator;
  readonly prevButton: Locator;
  readonly searchInput: Locator;
  readonly searchSubmit: Locator;

  constructor(private readonly page: Page) {
    this.entryCounter = page.locator("#notebookNameAndEntryNumber");
    this.nextButton = page.locator("#nextEntryButton");
    this.prevButton = page.locator("#prevEntryButton");
    this.searchInput = page.getByRole("textbox", { name: "Search Notebook..." });
    this.searchSubmit = page.getByRole("button", { name: "Search" });
  }

  entryThumbnail(name: string): Locator {
    return this.page.getByTitle(`Name: '${name}'`);
  }

  async clickEntry(name: string): Promise<void> {
    await this.entryThumbnail(name).click();
  }

  async next(): Promise<void> {
    await this.navigateAndWaitForEntryChange(this.nextButton);
  }

  async previous(): Promise<void> {
    await this.navigateAndWaitForEntryChange(this.prevButton);
  }

  private async navigateAndWaitForEntryChange(button: Locator): Promise<void> {
    const before = await this.entryCounter.innerText();
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/journal/ajax/retrieveEntry")),
      button.click(),
    ]);
    await this.entryCounter.filter({ hasNotText: before }).waitFor({ state: "visible" });
  }

  async getEntryCount(): Promise<{ current: number; total: number }> {
    const text = (await this.entryCounter.innerText()).trim();
    const parts = text.split(" ");
    const current = Number(parts[1]);
    const total = Number(parts[3]);
    if (!Number.isInteger(current) || !Number.isInteger(total)) {
      throw new Error(`getEntryCount: could not parse entry counter text "${text}"`);
    }
    return { current, total };
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/journal/ajax/quicksearch/")),
      this.searchSubmit.click(),
    ]);
  }
}
