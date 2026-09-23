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

  get confocalCalendarFile(): Locator {
    return page.getByRole("button", { name: /^\.ics file for Confocal microscope,/ }).first();
  }

  get confocalCancel(): Locator {
    return page.getByRole("button", { name: "Cancel booking", exact: true }).first();
  }

  tooltip(name: string): Locator {
    return page.getByRole("tooltip", { name, exact: true });
  }

  get unknownItem(): Locator {
    return page.getByRole("article", { name: "Unknown item", exact: true }).getByText("Unknown item", { exact: true });
  }

  get roleLossNotice(): Locator {
    return page
      .getByRole("article", { name: "Unknown item", exact: true })
      .getByText("Read-only: you no longer have access to this item.");
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
