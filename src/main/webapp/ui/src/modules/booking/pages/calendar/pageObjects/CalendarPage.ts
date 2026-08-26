import { type Locator, page } from "vitest/browser";

export class CalendarPage {
  readonly bookableItemDetailsHeading: Locator = page.getByRole("heading", { name: "Bookable item details" });
  readonly bookableItemDetailsTarget: Locator = page.getByText("Electron microscope", { exact: true });
  readonly heading: Locator = page.getByRole("heading", { name: "Calendar" });
  readonly search: Locator = page.getByRole("textbox", { name: "Search Calendar" });
  readonly timeGrid: Locator = page.getByRole("region", { name: "Time grid" });
  readonly resources: Locator = page.getByRole("button", { name: "Resources" });
  readonly resourceSchedule: Locator = page.getByRole("region", { name: "Resource booking schedule" });
  readonly agenda: Locator = page.getByRole("button", { name: "Agenda" });
  readonly bookingAgenda: Locator = page.getByRole("region", { name: "Booking agenda" });
  readonly day: Locator = page.getByRole("button", { name: "Day", exact: true });
  readonly month: Locator = page.getByRole("button", { name: "Month", exact: true });
  readonly mine: Locator = page.getByRole("button", { name: "My calendar" });
  readonly next: Locator = page.getByRole("button", { name: /^Next / });

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

  async searchFor(value: string): Promise<void> {
    await this.search.fill(value);
  }
}
