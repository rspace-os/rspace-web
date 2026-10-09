import { type Locator, page } from "vitest/browser";
import { upcomingBooking } from "../mocks/bookingMocks";

export class MyBookingsPageObject {
  readonly bookableItemDetailsHeading: Locator = page.getByRole("heading", { name: "Confocal microscope" });
  readonly bookableItemDetailsTarget: Locator = page.getByText("IN123", { exact: true });

  get heading(): Locator {
    return page.getByRole("heading", { name: "My Bookings" });
  }

  get upcoming(): Locator {
    return page.getByRole("button", { name: /Upcoming/ });
  }

  get past(): Locator {
    return page.getByRole("button", { name: "Past" });
  }

  get cancelled(): Locator {
    return page.getByRole("button", { name: "Cancelled", exact: true });
  }

  get upcomingCount(): Locator {
    return page.getByLabelText("2 upcoming bookings");
  }

  get confocal(): Locator {
    return page.getByText("Confocal microscope", { exact: true });
  }

  get confocalStartDate(): Locator {
    const displayTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const displayedStart = new Intl.DateTimeFormat("en-US", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: displayTimeZone,
    }).format(new Date(upcomingBooking.start));
    return page.getByRole("article", { name: /Confocal microscope/ }).getByText(displayedStart, { exact: true });
  }

  get electron(): Locator {
    return page.getByText("Electron microscope", { exact: true });
  }

  get confocalDetails(): Locator {
    return page.getByRole("link", { name: "View details", exact: true }).first();
  }

  get confocalItemCalendar(): Locator {
    return page.getByRole("link", { name: "View item calendar", exact: true }).first();
  }

  get confocalEdit(): Locator {
    return page.getByRole("link", { name: "Edit", exact: true }).first();
  }

  get confocalMoreActions(): Locator {
    return page.getByRole("button", { name: "More actions", exact: true }).first();
  }

  get cancelMenuItem(): Locator {
    return page.getByRole("menuitem", { name: "Cancel booking", exact: true });
  }

  get cancelDialog(): Locator {
    return page.getByRole("alertdialog");
  }

  tooltip(name: string): Locator {
    return page.getByRole("tooltip", { name, exact: true });
  }

  get reset(): Locator {
    return page.getByRole("button", { name: "Reset filters, sorting, and columns to defaults" });
  }

  async selectPast(): Promise<void> {
    await this.past.click();
  }

  async resetView(): Promise<void> {
    await this.reset.click();
  }
}
