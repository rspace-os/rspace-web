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
    return this.hiddenColumns.getByText("Bookable item: Instrument name", { exact: true });
  }

  get hiddenDeleted(): Locator {
    return this.hiddenColumns.getByText("Bookable item: Deleted", { exact: true });
  }

  get instrumentNameHeader(): Locator {
    return page.getByRole("columnheader", { name: "Bookable item: Instrument name" });
  }

  get deletedHeader(): Locator {
    return page.getByRole("columnheader", { name: "Bookable item: Deleted" });
  }

  async openColumns(): Promise<void> {
    await userEvent.click(this.columnsButton);
  }

  async search(value: string): Promise<void> {
    await userEvent.fill(this.searchRecords, value);
  }

  async filterByInstrumentName(value: string): Promise<void> {
    await userEvent.click(this.filterButton);
    await userEvent.click(page.getByRole("button", { name: "Add filter" }));
    const field = page.getByRole("combobox", { name: "Field for filter 1" });
    await userEvent.selectOptions(field, "target.name");
    await userEvent.selectOptions(page.getByRole("combobox", { name: "Operator for filter 1" }), "contains");
    await userEvent.fill(page.getByRole("combobox", { name: "Value for filter 1" }), value);
    await userEvent.click(page.getByRole("button", { name: "Apply filters" }));
  }
}
