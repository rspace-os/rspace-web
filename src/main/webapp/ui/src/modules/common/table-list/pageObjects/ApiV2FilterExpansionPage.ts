import { type Locator, page, userEvent } from "vitest/browser";

export class ApiV2FilterExpansionPage {
  get table(): Locator {
    return page.getByRole("table");
  }

  get filterButton(): Locator {
    return page.getByRole("button", { name: /^Filters/ });
  }

  get filterPanel(): Locator {
    return page.getByRole("region", { name: "Filter records" });
  }

  get addFilterButton(): Locator {
    return this.filterPanel.getByRole("button", { name: "Add filter" });
  }

  field(number: number): Locator {
    return page.getByRole("combobox", { name: `Field for filter ${number}` });
  }

  operator(number: number): Locator {
    return page.getByRole("combobox", { name: `Operator for filter ${number}` });
  }

  value(number: number): Locator {
    return page.getByRole("combobox", { name: `Value for filter ${number}` });
  }

  scalarValue(number: number): Locator {
    return page.getByRole("textbox", { name: `Value for filter ${number}` });
  }

  runtimeFieldSearch(number: number): Locator {
    return page.getByRole("combobox", { name: new RegExp(`custom fields for filter ${number}`, "i") });
  }

  get applyFiltersButton(): Locator {
    return this.filterPanel.getByRole("button", { name: "Apply filters" });
  }

  get runtimeSearchStatus(): Locator {
    return page.getByText(
      /Searching…|Custom fields could not be searched\.|Type 2 characters to search|No matching custom field\./,
    );
  }

  async openFilters(): Promise<void> {
    await userEvent.click(this.filterButton);
    await this.filterPanel.findElement();
  }

  async addFilter(): Promise<void> {
    await userEvent.click(this.addFilterButton);
  }

  async chooseField(number: number, name: string | RegExp): Promise<void> {
    await userEvent.click(this.field(number));
    await userEvent.click(page.getByRole("option", { name, exact: typeof name === "string" }));
  }

  async chooseOperator(number: number, name: string | RegExp): Promise<void> {
    await userEvent.click(this.operator(number));
    await userEvent.click(page.getByRole("option", { name, exact: typeof name === "string" }));
  }

  async chooseOption(name: string | RegExp): Promise<void> {
    await userEvent.click(page.getByRole("option", { name }));
  }

  async searchRuntimeField(term: string): Promise<void> {
    await userEvent.fill(this.runtimeFieldSearch(1), term);
  }

  async applyFilters(): Promise<void> {
    await userEvent.click(this.applyFiltersButton);
  }

  relationChipsScrollable(): boolean {
    const element = document.querySelector<HTMLElement>('[data-slot="combobox-chips"]');
    return (
      element !== null && element.scrollHeight > element.clientHeight && element.classList.contains("overflow-y-auto")
    );
  }

  async selectRelationshipOptionWithKeyboard(name: string, number = 1, searchTerm = name): Promise<void> {
    await userEvent.fill(this.value(number), searchTerm);
    await page.getByRole("option", { name: new RegExp(`^${escapeRegExp(name)}(?:\\s|$)`) }).findElement();
    await userEvent.keyboard("{ArrowDown}{Enter}");
  }
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
