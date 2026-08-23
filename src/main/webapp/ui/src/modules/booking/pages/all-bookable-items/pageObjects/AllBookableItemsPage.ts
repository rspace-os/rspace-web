import { type Locator, page } from "vitest/browser";

function css(selector: string): Locator {
  const root = page.elementLocator(document.body) as unknown as { locator: (value: string) => Locator };
  return root.locator(`css=${selector}`);
}

export class AllBookableItemsPage {
  get table(): Locator {
    return page.getByRole("table");
  }

  get heading(): Locator {
    return page.getByRole("heading", { name: "All Bookable Items" });
  }

  get tableElement(): Locator {
    return css('[data-slot="table"][aria-label="All Bookable Items table"]');
  }

  get cards(): Locator {
    return css('[data-slot="table-list-card-view"][aria-label="All Bookable Items cards"]');
  }

  get search(): Locator {
    return page.getByRole("textbox", { name: "Search All Bookable Items" });
  }

  get filtersButton(): Locator {
    return page.getByRole("button", { name: /^Filters/ });
  }

  get item(): Locator {
    return this.table.getByText("Confocal microscope", { exact: true });
  }

  get electronMicroscope(): Locator {
    return this.table.getByText("Electron microscope", { exact: true });
  }

  get massSpectrometer(): Locator {
    return this.table.getByText("Mass spectrometer", { exact: true });
  }

  get flowCytometer(): Locator {
    return this.table.getByText("Flow cytometer", { exact: true });
  }

  get confocalAvailability(): Locator {
    return page.getByRole("img", { name: "Confocal microscope availability" });
  }

  get electronAvailability(): Locator {
    return page.getByRole("img", { name: "Electron microscope availability" });
  }

  get massSpectrometerAvailability(): Locator {
    return page.getByRole("img", { name: "Mass spectrometer availability" });
  }

  get flowCytometerAvailability(): Locator {
    return page.getByRole("img", { name: "Flow cytometer availability" });
  }

  get date(): Locator {
    return page.getByLabelText("Date");
  }

  get availableNow(): Locator {
    return page.getByRole("button", { name: /Available now/ });
  }

  get freeLaterToday(): Locator {
    return page.getByRole("button", { name: /Free later today/ });
  }

  get quickFilterScope(): Locator {
    return page.getByText("Today in each bookable item's time zone.");
  }

  get bookButton(): Locator {
    return this.table
      .getByRole("row")
      .filter({ hasText: "Confocal microscope" })
      .getByRole("link", { name: "Book", exact: true });
  }

  get electronBookButton(): Locator {
    return this.table
      .getByRole("row")
      .filter({ hasText: "Electron microscope" })
      .getByRole("link", { name: "Book", exact: true });
  }

  card(itemName: string): Locator {
    return this.cards.getByRole("article").filter({ has: page.getByText(itemName, { exact: true }) });
  }

  cardBookButton(itemName: string): Locator {
    return this.card(itemName).getByRole("link", { name: "Book", exact: true });
  }

  cardWidth(itemName: string): number {
    return this.card(itemName).element().getBoundingClientRect().width;
  }

  availabilityWidth(itemName: string): number {
    return page
      .getByRole("img", { name: `${itemName} availability` })
      .element()
      .getBoundingClientRect().width;
  }
}
