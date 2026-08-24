import { type Locator, page } from "vitest/browser";

export class CalendarPage {
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

  async searchFor(value: string): Promise<void> {
    await this.search.fill(value);
  }
}
