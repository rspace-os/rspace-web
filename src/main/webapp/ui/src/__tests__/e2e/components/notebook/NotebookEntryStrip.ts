import { expect, type Locator, type Page } from "@playwright/test";

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
    const currentName = this.page.locator("#recordNameInHeader");
    if ((await currentName.textContent()) === name) {
      await this.waitForEntryContent(name);
      return;
    }
    await this.navigateAndWaitForEntry(this.entryThumbnail(name), { name });
  }

  async next(): Promise<void> {
    const { current } = await this.getEntryCount();
    await this.navigateAndWaitForEntry(this.nextButton, { position: current });
  }

  async previous(): Promise<void> {
    const { current } = await this.getEntryCount();
    await this.navigateAndWaitForEntry(this.prevButton, { position: current - 2 });
  }

  private async navigateAndWaitForEntry(
    target: Locator,
    expected: { name: string } | { position: number },
  ): Promise<void> {
    const { total } = await this.getEntryCount();
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => {
        const path = new URL(res.url()).pathname;
        return path.startsWith("/journal/ajax/retrieveEntry/") || path.startsWith("/journal/ajax/retrieveEntryById/");
      }),
      target.click(),
    ]);
    expect(response.ok(), "Loading the requested notebook entry succeeds").toBe(true);
    const entry = (await response.json()) as { id: number; name: string; position: number };
    if ("name" in expected) expect(entry.name).toBe(expected.name);
    else expect(entry.position).toBe(expected.position);
    // The counter is temporarily emptied before journal.js renders the new entry.
    // Wait for the complete position and identity rather than accepting that loading state.
    await expect(this.entryCounter).toHaveText(`Entry ${entry.position + 1} of ${total}`);
    await this.waitForEntryContent(entry.name);
    await expect(this.page.getByRole("link", { name: `SD${entry.id}`, exact: true })).toHaveAttribute(
      "href",
      `/globalId/SD${entry.id}`,
    );
  }

  private async waitForEntryContent(name: string): Promise<void> {
    await expect(this.page.locator("#recordNameInHeader")).toHaveText(name);
    await expect(this.page.locator("#journalPageLoading")).toBeHidden();
    await this.getEntryCount();
  }

  async getEntryCount(): Promise<{ current: number; total: number }> {
    let result: { current: number; total: number } | null = null;
    await expect
      .poll(
        async () => {
          const text = (await this.entryCounter.innerText()).trim();
          const parts = text.split(" ");
          const current = Number(parts[1]);
          const total = Number(parts[3]);
          const valid =
            Number.isInteger(current) &&
            Number.isInteger(total) &&
            current > 0 &&
            current <= total &&
            text === `Entry ${current} of ${total}`;
          result = valid ? { current, total } : null;
          return valid ? true : text;
        },
        { message: "Notebook displays a complete entry counter" },
      )
      .toBe(true);
    if (result === null) throw new Error("Notebook entry counter did not become ready.");
    return result;
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/journal/ajax/quicksearch/")),
      this.searchSubmit.click(),
    ]);
  }
}
