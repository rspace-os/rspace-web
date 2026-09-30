import { type Locator, page, userEvent } from "vitest/browser";

type CalendarViewOption = "Time grid" | "By Item" | "Agenda" | "Day" | "Week" | "Month";

export class CalendarPage {
  readonly bookableItemDetailsHeading: Locator = page.getByRole("heading", { name: "Electron microscope" });
  readonly bookableItemDetailsTarget: Locator = page.getByText("IN124", { exact: true });
  readonly heading: Locator = page.getByRole("heading", { name: "Calendar" });
  readonly toolbar: Locator = page.getByRole("toolbar", { name: "Calendar", exact: true });
  readonly reset: Locator = this.toolbar.getByRole("button", {
    name: "Reset filters, sorting, and columns to defaults",
  });
  readonly filters: Locator = this.toolbar.getByRole("button", { name: /^Filters(?:$|,)/ });
  readonly dateControls: Locator = page.getByRole("group", { name: "Calendar date controls" });
  readonly viewMenu: Locator = this.toolbar.getByRole("button", { name: /^View: / });
  readonly search: Locator = page.getByRole("textbox", { name: "Search Calendar" });
  readonly timeGrid: Locator = page.getByRole("region", { name: "Time grid" });
  readonly weekGrid: Locator = this.timeGrid.getByRole("region", { name: "Calendar grid" });
  readonly resourceSchedule: Locator = page.getByRole("region", { name: "Resource booking schedule" });
  readonly bookingAgenda: Locator = page.getByRole("region", { name: "Booking agenda" });
  readonly removeMine: Locator = page.getByRole("button", { name: "Remove My Bookings filter" });
  readonly previous: Locator = this.toolbar.getByRole("button", { name: /^Previous / });
  readonly next: Locator = this.toolbar.getByRole("button", { name: /^Next / });
  readonly newBooking: Locator = page.getByRole("button", { name: "New Booking" });
  readonly timeZone: Locator = this.toolbar.getByLabelText(/^Time zone:/);
  readonly removeTargetFilter: Locator = page.getByRole("button", { name: "Remove bookable item filter" });

  /** A layout or period in the open View menu. */
  viewOption(name: CalendarViewOption): Locator {
    return page.getByRole("menuitemradio", { name, exact: true });
  }

  /** Chooses a layout or period, then closes the menu, which otherwise leaves the rest of the page inert. */
  async chooseView(name: CalendarViewOption): Promise<void> {
    await this.viewMenu.click();
    await this.viewOption(name).click();
    await userEvent.keyboard("{Escape}");
  }

  quickFilter(name: "My Bookings" | "Owned Items"): Locator {
    return this.toolbar.getByRole("button", { name, exact: true });
  }

  /** Turns a quick filter on or off using its toolbar button. */
  async toggleQuickFilter(name: "My Bookings" | "Owned Items"): Promise<void> {
    await this.quickFilter(name).click();
  }

  /** Opens the advanced filter panel directly from the toolbar. */
  async openFilterPanel(): Promise<void> {
    await this.filters.click();
  }

  event(itemName: string): Locator {
    return page.getByRole("article", { name: new RegExp(itemName) });
  }

  showEventDetails(itemName: string): Locator {
    return page.getByRole("button", { name: new RegExp(`Show details for ${itemName}`) });
  }

  get viewItemDetails(): Locator {
    return page.getByRole("link", { name: "View details", exact: true });
  }

  get editBooking(): Locator {
    return page.getByRole("button", { name: "Edit", exact: true });
  }

  get bookingDialog(): Locator {
    return page.getByRole("dialog", { name: "New Booking" });
  }

  get resourceCanvases(): Locator[] {
    return page.getByTestId("day-timeline-canvas").all();
  }

  async openTargetlessBookingDialog(): Promise<Locator> {
    await this.newBooking.click();
    await this.bookingDialog.getByRole("button", { name: "Choose a bookable item" }).click();
    await page.getByRole("option", { name: /Confocal microscope.*IN123/ }).click();
    return this.bookingDialog;
  }

  async dragResourceSelection(index: number): Promise<void> {
    const canvas = this.resourceCanvases[index];
    const positions = {
      sourcePosition: { x: 300, y: 60 },
      targetPosition: { x: 420, y: 60 },
    };
    await userEvent.dragAndDrop(canvas, canvas, positions);
  }

  get timeGridCanvas(): Locator {
    return this.timeGrid.getByTestId("day-timeline-canvas");
  }

  get timeGridScroller(): Locator {
    return this.timeGrid.getByTestId("day-timeline-scroller");
  }

  /** Drags across the Time grid day view between two elapsed minutes of a 24-hour day, scrolled into view first. */
  async dragTimeGridRange(startMinute: number, endMinute: number): Promise<void> {
    const canvas = this.timeGridCanvas.element() as HTMLElement;
    const scroller = this.timeGridScroller.element() as HTMLElement;
    const pixelsPerMinute = canvas.getBoundingClientRect().width / (24 * 60);
    scroller.scrollLeft = Math.max(0, startMinute * pixelsPerMinute - 40);
    const canvasBounds = canvas.getBoundingClientRect();
    const scrollerBounds = scroller.getBoundingClientRect();
    // Half a minute in, so the pointer never sits on the boundary the snap rounds from; above the event lanes.
    const positionAt = (minute: number) => ({
      x: canvasBounds.left - scrollerBounds.left + (minute + 0.5) * pixelsPerMinute,
      y: canvasBounds.top - scrollerBounds.top + 40,
    });
    // Target the scroller, which fits the viewport, so Playwright does not re-center the oversized canvas.
    await userEvent.dragAndDrop(this.timeGridScroller, this.timeGridScroller, {
      sourcePosition: positionAt(startMinute),
      targetPosition: positionAt(endMinute),
    });
  }

  async searchFor(value: string): Promise<void> {
    await this.search.fill(value);
  }
}
