import { type Locator, page, userEvent } from "vitest/browser";

export class BookableItemsPage {
  get searchRecords(): Locator {
    return page.getByRole("textbox", { name: "Search Bookable Items" });
  }

  get filterButton(): Locator {
    return page.getByRole("button", { name: "Filters, none applied" });
  }

  get columnsButton(): Locator {
    return page.getByRole("button", { name: "Columns" });
  }

  get hiddenColumns(): Locator {
    return page.getByRole("group", { name: "Hidden" });
  }

  get hiddenInstrumentName(): Locator {
    return this.hiddenColumns.getByText("Bookable item \u2192 Instrument name", { exact: true });
  }

  get hiddenDeleted(): Locator {
    return this.hiddenColumns.getByText("Bookable item \u2192 Deleted", { exact: true });
  }

  get hiddenGlobalId(): Locator {
    return this.hiddenColumns.getByText("Bookable item \u2192 Global ID", { exact: true });
  }

  fieldOption(label: string): Locator {
    return page.getByRole("option", { name: label });
  }

  get instrumentNameHeader(): Locator {
    return page.getByRole("columnheader", { name: "Bookable item \u2192 Instrument name" });
  }

  get deletedHeader(): Locator {
    return page.getByRole("columnheader", { name: "Bookable item \u2192 Deleted" });
  }

  async openColumns(): Promise<void> {
    await userEvent.click(this.columnsButton);
  }

  async search(value: string): Promise<void> {
    await userEvent.fill(this.searchRecords, value);
  }

  get fieldSelect(): Locator {
    return page.getByRole("combobox", { name: "Field for filter 1" });
  }

  get customFieldSearch(): Locator {
    return page.getByRole("combobox", { name: "Search Bookable item custom fields for filter 1" });
  }

  get valueInput(): Locator {
    return page.getByRole("combobox", { name: "Value for filter 1" });
  }

  get valueTextbox(): Locator {
    return page.getByRole("textbox", { name: "Value for filter 1" });
  }

  get operatorSelect(): Locator {
    return page.getByRole("combobox", { name: "Operator for filter 1" });
  }

  async openFilters(): Promise<void> {
    await userEvent.click(this.filterButton);
    await userEvent.click(page.getByRole("button", { name: "Add filter" }));
  }

  async chooseField(label: string): Promise<void> {
    await userEvent.click(this.fieldSelect);
    await userEvent.fill(this.fieldSelect, label);
    await userEvent.click(page.getByRole("option", { name: label }));
  }

  async chooseOperator(label: string): Promise<void> {
    await userEvent.click(this.operatorSelect);
    await userEvent.click(page.getByRole("option", { name: label }));
  }

  async chooseCustomFieldSource(): Promise<void> {
    await this.chooseField("Custom field on Bookable item…");
  }

  async filterByInstrumentName(value: string): Promise<void> {
    await this.openFilters();
    await this.chooseField("Bookable item \u2192 Instrument name");
    await this.chooseOperator("contains");
    await userEvent.fill(page.getByRole("combobox", { name: "Value for filter 1" }), value);
    await userEvent.click(page.getByRole("button", { name: "Apply filters" }));
  }
}
