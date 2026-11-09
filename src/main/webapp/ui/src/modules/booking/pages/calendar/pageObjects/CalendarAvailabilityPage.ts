import { type Locator, page } from "vitest/browser";
import { bookableItemHeading, bookableItemTarget, css } from "../../bookable-items/pageObjects/bookableItemLocators";

export class CalendarAvailabilityPage {
  get instrumentName(): Locator {
    return page.getByRole("link", { name: "Confocal microscope", exact: true }).first();
  }

  readonly bookableItemDetailsHeading: Locator = bookableItemHeading("Confocal microscope");
  readonly bookableItemDetailsTarget: Locator = bookableItemTarget("IN123");

  get table(): Locator {
    return page.getByRole("table");
  }

  get heading(): Locator {
    return page.getByRole("heading", { name: "Calendar" });
  }

  get toolbar(): Locator {
    return page.getByRole("toolbar", { name: "Calendar" });
  }

  get dateControls(): Locator {
    return page.getByRole("group", { name: "Calendar date controls" });
  }

  get tableElement(): Locator {
    return css('[data-slot="table"][aria-label="All items table"]');
  }

  get cards(): Locator {
    return css('[data-slot="table-list-card-view"][aria-label="All items cards"]');
  }

  get search(): Locator {
    return page.getByRole("textbox", { name: "Search Calendar" });
  }

  get filtersButton(): Locator {
    return page.getByRole("button", { name: /^Filters/ });
  }

  /** The panel the Filters button controls; each table on a page has its own panel ID. */
  get filtersPanel(): Locator {
    const id = this.filtersButton.element().getAttribute("aria-controls");
    if (!id) throw new Error("The Filters button controls no panel");
    return css(`#${CSS.escape(id)}`);
  }

  get resetTable(): Locator {
    return page.getByRole("button", { name: "Reset filters, sorting, and columns to defaults" });
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

  /** A wall-clock hour as the availability bar writes it, which follows the browser region's clock. */
  clock(hour: number, options: Intl.DateTimeFormatOptions = { hour: "2-digit", minute: "2-digit" }): string {
    return new Intl.DateTimeFormat(undefined, { ...options, timeZone: "UTC" }).format(
      new Date(Date.UTC(2026, 7, 17, hour)),
    );
  }

  /** A slice period from `startHour` to `endHour`, matched loosely around the date and clock-change wording. */
  periodPattern(startHour: number, endHour: number): string {
    return `.*${this.clock(startHour, { timeStyle: "short" })}.*${this.clock(endHour, { timeStyle: "short" })}.*`;
  }

  get confocalAvailabilityText(): Locator {
    return this.card("Confocal microscope").getByText(`Available until ${this.clock(10)}`, { exact: true });
  }

  availabilitySlice(itemName: string, contributorCount: number, state = ".*", period = ".*"): Locator {
    return page.getByRole("button", {
      name: new RegExp(
        `^${itemName}, ${state}, ${period}, ${contributorCount} event${contributorCount === 1 ? "" : "s"}$`,
      ),
    });
  }

  get availabilityDetails(): Locator {
    return page.getByRole("dialog");
  }

  get availabilityBookingRows(): Locator {
    return this.availabilityDetails.getByRole("listitem");
  }

  focusAvailabilitySlice(itemName: string, contributorCount: number): void {
    this.availabilitySlice(itemName, contributorCount).element().focus();
  }

  availabilityDetailsRect(): DOMRect {
    return this.availabilityDetails.element().getBoundingClientRect();
  }

  availabilityDetailsIsScrollable(): boolean {
    const details = this.availabilityDetails.element();
    return details.scrollHeight > details.clientHeight;
  }

  availabilityDetailsBorderWidth(): string {
    return getComputedStyle(this.availabilityDetails.element()).borderTopWidth;
  }

  get effectiveTimeZone(): Locator {
    return page.getByText("UTC", { exact: true });
  }

  get datePicker(): Locator {
    return page.getByRole("button", { name: /^Jump to date, / });
  }

  get nextDay(): Locator {
    return page.getByRole("button", { name: "Next day" });
  }

  get availableNow(): Locator {
    return page.getByRole("button", { name: "Available now", exact: true });
  }

  get freeLaterToday(): Locator {
    return page.getByRole("button", { name: "Free later", exact: true });
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
