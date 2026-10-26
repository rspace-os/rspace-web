import type { Locator } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class BookableItemsFilterPage extends BasePage {
  readonly path = "/booking/config/bookable-items";

  get heading(): Locator {
    return this.page.getByRole("heading", { level: 1, name: "Bookable Items" });
  }

  get table(): Locator {
    return this.page.getByRole("table");
  }

  get filtersButton(): Locator {
    return this.page.getByRole("button", { name: /^Filters/ });
  }

  get filterPanel(): Locator {
    return this.page.getByRole("region", { name: "Filter records" });
  }

  field(number: number): Locator {
    return this.page.getByRole("combobox", { name: `Field for filter ${number}` });
  }

  operator(number: number): Locator {
    return this.page.getByRole("combobox", { name: `Operator for filter ${number}` });
  }

  scalarValue(number: number): Locator {
    return this.page.getByRole("textbox", { name: `Value for filter ${number}` });
  }

  async open(): Promise<void> {
    await this.page.goto(this.path);
    await this.heading.waitFor();
    await this.table.waitFor();
  }

  async openFilters(): Promise<void> {
    await this.filtersButton.click();
    await this.filterPanel.waitFor();
  }

  async addFilter(): Promise<void> {
    await this.filterPanel.getByRole("button", { name: "Add filter" }).click();
  }

  private async chooseOption(locator: Locator, name: string | RegExp): Promise<void> {
    await locator.click();
    await this.page.getByRole("option", { name, exact: typeof name === "string" }).click();
  }

  async chooseField(number: number, name: string | RegExp): Promise<void> {
    await this.chooseOption(this.field(number), name);
  }

  async chooseOperator(number: number, name: string | RegExp): Promise<void> {
    await this.chooseOption(this.operator(number), name);
  }

  async chooseRuntimeField(number: number, name: string): Promise<void> {
    await this.field(number).click();
    await this.page.getByRole("option", { name: "Custom field…", exact: true }).click();
    const search = this.page.getByRole("combobox", {
      name: `Search Bookable item custom fields for filter ${number}`,
      exact: true,
    });
    await search.fill(name);
    await this.page.getByRole("option", { name, exact: false }).click();
  }

  async applyFilters(): Promise<void> {
    await this.filterPanel.getByRole("button", { name: "Apply filters" }).click();
    await this.filterPanel.waitFor({ state: "detached" });
  }

  row(name: string): Locator {
    return this.table.getByRole("row").filter({ hasText: name });
  }
}
