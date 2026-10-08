import { type Locator, page, userEvent } from "vitest/browser";

function css(selector: string): Locator {
  const root = page.elementLocator(document.body) as unknown as { locator: (value: string) => Locator };
  return root.locator(`css=${selector}`);
}

export class TableListResponsivePage {
  get table(): Locator {
    return css('[data-slot="table"][aria-label="Research records table"]');
  }

  get cards(): Locator {
    return css('[data-slot="table-list-card-view"][aria-label="Research records cards"]');
  }

  get search(): Locator {
    return page.getByRole("textbox", { name: "Search Research records" });
  }

  get columnsButton(): Locator {
    return page.getByRole("button", { name: /^Columns/ });
  }

  get sortingButton(): Locator {
    return page.getByRole("button", { name: /^Sort/ });
  }

  get filtersButton(): Locator {
    return page.getByRole("button", { name: /^Filters/ });
  }

  get nextPage(): Locator {
    return page.getByRole("button", { name: "Next page" });
  }

  get pageStatus(): Locator {
    return page.getByText(/Page \d+ of \d+/);
  }

  get containerWidth(): Locator {
    return page.getByLabelText("TableList container width");
  }

  get widthToggle(): Locator {
    return page.getByRole("button", { name: /Use (narrow|wide) container/ });
  }

  card(name: string): Locator {
    return this.cards.getByRole("article", { name });
  }

  row(name: string): Locator {
    return this.table.getByRole("row").filter({ hasText: name });
  }

  async sortByTitle(): Promise<void> {
    await userEvent.click(page.getByRole("button", { name: "Sort by Title" }));
  }

  async hideOwner(): Promise<void> {
    await userEvent.click(this.columnsButton);
    await userEvent.click(page.getByRole("button", { name: "Hide Owner column" }));
    await userEvent.click(page.getByRole("button", { name: "Close columns" }));
  }
}
