import { type Locator, page } from "vitest/browser";

export class MyBookingsPageObject {
  readonly bookableItemDetailsHeading: Locator = page.getByRole("heading", { name: "Confocal microscope" });
  readonly bookableItemDetailsTarget: Locator = page.getByText("IN123", { exact: true });

  get upcoming(): Locator {
    return page.getByRole("button", { name: "Upcoming", exact: true });
  }

  get past(): Locator {
    return page.getByRole("button", { name: "Past" });
  }

  get upcomingCount(): Locator {
    return page.getByLabelText("2 upcoming bookings");
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

  get roleLossNotice(): Locator {
    return page.getByText("Read-only: you no longer have access to this item.");
  }
}
