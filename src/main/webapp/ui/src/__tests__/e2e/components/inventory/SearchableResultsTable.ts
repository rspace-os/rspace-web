import type { Locator } from "@playwright/test";

export class SearchableResultsTable {
  constructor(private readonly root: Locator) {}

  private get nextPageButton(): Locator {
    return this.root.getByRole("button", { name: "Go to next page" });
  }

  row(name: string, options: { exact?: boolean } = {}): Locator {
    if (options.exact) {
      return this.root.getByRole("row").filter({
        has: this.root.page().getByRole("cell", { name, exact: true }),
      });
    }
    return this.root.getByRole("row", { name });
  }

  async findRow(name: string, options: { exact?: boolean } = {}): Promise<Locator> {
    const row = this.row(name, options);
    for (;;) {
      const appeared = await row
        .first()
        .waitFor({ state: "visible", timeout: 3_000 })
        .then(() => true)
        .catch(() => false);
      if (appeared) return row;
      if (!(await this.nextPageButton.isEnabled().catch(() => false))) {
        throw new Error(`Row "${name}" not found and no further pages remain.`);
      }
      await this.nextPageButton.click();
    }
  }

  async open(name: string, options: { exact?: boolean } = {}): Promise<void> {
    const row = await this.findRow(name, options);
    await row.getByRole("cell", { name, exact: options.exact }).click();
  }

  async select(name: string): Promise<void> {
    const row = await this.findRow(name);
    await row.getByRole("radio", { name: "Select " }).click();
  }

  async check(name: string): Promise<void> {
    const row = await this.findRow(name);
    await row.getByRole("checkbox", { name: "Select " }).check();
  }
}
