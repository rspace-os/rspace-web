import { type Locator, page, userEvent } from "vitest/browser";

function css(selector: string): Locator {
  const root = page.elementLocator(document.body) as unknown as { locator: (value: string) => Locator };
  return root.locator(`css=${selector}`);
}

export class AllBookableItemsPage {
  readonly bookableItemDetailsHeading: Locator = page.getByRole("heading", { name: "Confocal microscope" });
  readonly bookableItemDetailsTarget: Locator = page.getByRole("link", {
    name: "View Confocal microscope in Inventory",
  });

  get table(): Locator {
    return page.getByRole("table");
  }

  get heading(): Locator {
    return page.getByRole("heading", { name: "All Bookable Items" });
  }

  get toolbar(): Locator {
    return page.getByRole("toolbar", { name: "All Bookable Items controls" });
  }

  get dateControls(): Locator {
    return page.getByRole("group", { name: "Availability date controls" });
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

  availabilitySlice(itemName: string, contributorCount: number, state = ".*"): Locator {
    return page.getByRole("button", {
      name: new RegExp(
        `^${itemName}, ${state}, .*, ${contributorCount} constituent event${contributorCount === 1 ? "" : "s"}$`,
      ),
    });
  }

  get availabilityDetails(): Locator {
    return page.getByRole("dialog");
  }

  get availabilityBookingRows(): Locator {
    return this.availabilityDetails.getByText("Booking", { exact: true });
  }

  focusAvailabilitySlice(itemName: string, contributorCount: number): void {
    this.availabilitySlice(itemName, contributorCount).element().focus();
  }

  async pressEscape(): Promise<void> {
    await userEvent.keyboard("{Escape}");
  }

  sliceBorderRadius(itemName: string, contributorCount: number): string {
    return window.getComputedStyle(this.availabilitySlice(itemName, contributorCount).element()).borderRadius;
  }

  sliceHeight(itemName: string, contributorCount: number): number {
    return this.availabilitySlice(itemName, contributorCount).element().getBoundingClientRect().height;
  }

  availabilityDetailsRect(): DOMRect {
    return this.availabilityDetails.element().getBoundingClientRect();
  }

  parentContainer(name: string): Locator {
    return this.table.getByRole("link", { name, exact: true });
  }

  cardParentContainer(itemName: string, parentName: string): Locator {
    return this.card(itemName).getByRole("link", { name: parentName, exact: true });
  }

  nowMarker(itemName: string): Locator {
    return page.getByRole("img", { name: `${itemName} availability` }).getByTitle(/Current time:/);
  }

  get effectiveTimeZone(): Locator {
    return page.getByText("UTC", { exact: true });
  }

  get datePicker(): Locator {
    return page.getByRole("button", { name: "Jump to date" });
  }

  get availableNow(): Locator {
    return page.getByRole("button", { name: /Available now/ });
  }

  get freeLaterToday(): Locator {
    return page.getByRole("button", { name: /Free later today/ });
  }

  get quickFilterScope(): Locator {
    return page.getByText("Availability uses the selected display date and timezone.");
  }

  get bookButton(): Locator {
    return this.table
      .getByRole("row")
      .filter({ hasText: "Confocal microscope" })
      .getByRole("link", { name: "Book", exact: true });
  }

  get detailsButton(): Locator {
    return this.table
      .getByRole("row")
      .filter({ hasText: "Confocal microscope" })
      .getByRole("link", { name: "View details", exact: true });
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

  cardDetailsButton(itemName: string): Locator {
    return this.card(itemName).getByRole("link", { name: "View details", exact: true });
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
