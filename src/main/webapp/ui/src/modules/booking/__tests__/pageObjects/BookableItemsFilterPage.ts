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

  value(number: number): Locator {
    return this.page.getByRole("combobox", { name: `Value for filter ${number}` });
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

  async chooseField(number: number, name: string | RegExp): Promise<void> {
    await this.field(number).click();
    await this.page.getByRole("option", { name, exact: typeof name === "string" }).click();
  }

  async chooseOperator(number: number, name: string | RegExp): Promise<void> {
    await this.operator(number).click();
    await this.page.getByRole("option", { name, exact: typeof name === "string" }).click();
  }

  async chooseRelationshipValue(number: number, searchTerm: string, optionName: string | RegExp): Promise<void> {
    const value = this.value(number);
    await value.fill(searchTerm);
    await this.page.getByRole("option", { name: optionName }).click();
  }

  async chooseRuntimeField(number: number, name: string): Promise<void> {
    await this.page.getByRole("combobox", { name: `Field for filter ${number}` }).click();
    await this.page.getByRole("option", { name: /Custom field/ }).click();
    const search = this.page.getByRole("combobox", { name: new RegExp(`custom fields for filter ${number}`, "i") });
    await search.fill(name);
    await this.page.getByRole("option", { name: new RegExp(name) }).click();
  }

  async applyFilters(): Promise<void> {
    await this.filterPanel.getByRole("button", { name: "Apply filters" }).click();
    await this.filterPanel.waitFor({ state: "detached" });
  }

  row(name: string): Locator {
    return this.table.getByRole("row").filter({ hasText: name });
  }

  dataRows(): Locator {
    return this.table.getByRole("row").filter({ has: this.page.getByRole("cell") });
  }

  async resizeNarrow(): Promise<void> {
    await this.page.setViewportSize({ width: 390, height: 844 });
  }

  async relationshipChipsScrollable(): Promise<boolean> {
    // The chip wrapper has no ARIA role; its data slot is the stable control hook.
    const chips = this.page.locator('[data-slot="combobox-chips"]').first();
    return chips.evaluate(
      (element) => element.scrollHeight > element.clientHeight && element.classList.contains("overflow-y-auto"),
    );
  }
}
