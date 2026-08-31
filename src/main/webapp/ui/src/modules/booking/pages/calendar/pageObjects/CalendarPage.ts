import { type Locator, page } from "vitest/browser";

export class CalendarPage {
  readonly bookableItemDetailsHeading: Locator = page.getByRole("heading", { name: "Electron microscope" });
  readonly bookableItemDetailsTarget: Locator = page.getByRole("link", {
    name: "View Electron microscope in Inventory",
  });
  readonly heading: Locator = page.getByRole("heading", { name: "Calendar" });
  readonly toolbar: Locator = page.getByRole("toolbar", { name: "Calendar controls" });
  readonly dateControls: Locator = page.getByRole("group", { name: "Calendar date controls" });
  readonly displayControls: Locator = page.getByRole("group", { name: "Calendar display controls" });
  readonly search: Locator = page.getByRole("textbox", { name: "Search Calendar" });
  readonly timeGrid: Locator = page.getByRole("region", { name: "Time grid" });
  readonly resources: Locator = page.getByRole("button", { name: "Resources" });
  readonly resourceSchedule: Locator = page.getByRole("region", { name: "Resource booking schedule" });
  readonly agenda: Locator = page.getByRole("button", { name: "Agenda" });
  readonly bookingAgenda: Locator = page.getByRole("region", { name: "Booking agenda" });
  readonly day: Locator = page.getByRole("button", { name: "Day", exact: true });
  readonly month: Locator = page.getByRole("button", { name: "Month", exact: true });
  readonly mine: Locator = page.getByRole("button", { name: "My calendar" });
  readonly previous: Locator = page.getByRole("button", { name: /^Previous / });
  readonly next: Locator = page.getByRole("button", { name: /^Next / });
  readonly newBooking: Locator = page.getByRole("button", { name: "New Booking" });
  readonly timeZone: Locator = this.toolbar.getByLabelText(/^Time zone:/);

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
    return page.getByRole("link", { name: "Edit", exact: true });
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

  async dragResourceSelection(index: number, from: number, to: number, pointerId: number): Promise<void> {
    const canvas = this.resourceCanvases[index].element() as HTMLElement;
    const box = canvas.getBoundingClientRect();
    canvas.dispatchEvent(
      new PointerEvent("pointerdown", {
        bubbles: true,
        clientX: box.left + box.width * from,
        pointerId,
        buttons: 1,
      }),
    );
    await new Promise((resolve) => window.setTimeout(resolve, 0));
    canvas.dispatchEvent(
      new PointerEvent("pointermove", {
        bubbles: true,
        clientX: box.left + box.width * to,
        pointerId,
        buttons: 1,
      }),
    );
    await new Promise((resolve) => window.setTimeout(resolve, 0));
    canvas.dispatchEvent(
      new PointerEvent("pointerup", {
        bubbles: true,
        clientX: box.left + box.width * to,
        pointerId,
      }),
    );
  }

  async searchFor(value: string): Promise<void> {
    await this.search.fill(value);
  }
}
