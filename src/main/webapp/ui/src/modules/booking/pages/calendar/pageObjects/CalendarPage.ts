import { type Locator, page, userEvent } from "vitest/browser";
import { bookableItemHeading } from "../../bookable-items/pageObjects/bookableItemLocators";

type CalendarViewOption = "Time grid" | "By Item" | "Agenda" | "Day" | "Week" | "Month";

export class CalendarPage {
  readonly bookableItemDetailsHeading: Locator = bookableItemHeading("Electron microscope");
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

  /** A quick filter switch in the open Filters menu. */
  quickFilter(name: "My Bookings" | "Owned Items"): Locator {
    return page.getByRole("switch", { name, exact: true });
  }

  /** Turns a quick filter on or off in the Filters menu, then closes the menu. */
  async toggleQuickFilter(name: "My Bookings" | "Owned Items"): Promise<void> {
    await this.filters.click();
    await this.quickFilter(name).click();
    await userEvent.keyboard("{Escape}");
  }

  /** Opens the filter panel through the Filters menu. */
  async openFilterPanel(): Promise<void> {
    await this.filters.click();
    await page.getByRole("button", { name: "Edit filters", exact: true }).click();
  }

  event(itemName: string): Locator {
    return page.getByRole("article", { name: itemName, exact: false });
  }

  showEventDetails(itemName: string): Locator {
    return page.getByRole("button", { name: `Show details for ${itemName}`, exact: false });
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
    await page.getByRole("option", { name: "Confocal microscope (IN123) — Europe/Berlin", exact: true }).click();
    return this.bookingDialog;
  }

  async dragResourceSelection(index: number): Promise<void> {
    const scroller = page.getByTestId("day-timeline-scroller").all()[index];
    const element = scroller.element();
    const bounds = element.getBoundingClientRect();
    const canvas = this.resourceCanvases[index].element();
    // A compact editor can cover part of the next resource. Use an exposed part of its timeline.
    const y = 60;
    const sourceX = Array.from({ length: Math.floor((element.clientWidth - 140) / 40) }, (_, i) => 20 + i * 40).find(
      (x) =>
        [x, x + 120].every((offset) => {
          const hit = document.elementFromPoint(
            bounds.left + element.clientLeft + offset,
            bounds.top + element.clientTop + y,
          );
          return hit !== null && canvas.contains(hit) && hit.closest("button") === null;
        }),
    );
    if (sourceX === undefined) throw new Error("No exposed resource timeline range to drag");
    await userEvent.dragAndDrop(scroller, scroller, {
      sourcePosition: { x: sourceX, y },
      targetPosition: { x: sourceX + 120, y },
    });
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
    // Target the minute itself; the timeline rounds pointer positions to the nearest minute.
    const positionAt = (minute: number) => ({
      x: canvasBounds.left - scrollerBounds.left - scroller.clientLeft + minute * pixelsPerMinute,
      y: canvasBounds.top - scrollerBounds.top - scroller.clientTop + 40,
    });
    // Target the scroller, which fits the viewport, so Playwright does not re-center the oversized canvas.
    await userEvent.dragAndDrop(this.timeGridScroller, this.timeGridScroller, {
      sourcePosition: positionAt(startMinute),
      targetPosition: positionAt(endMinute),
    });
  }
}
