import { type Locator, page } from "vitest/browser";
import { bookableItemHeading, bookableItemTarget } from "../../bookable-items/pageObjects/bookableItemLocators";
import { upcomingBooking } from "../mocks/bookingMocks";

export class MyBookingsPageObject {
  readonly bookableItemDetailsHeading: Locator = bookableItemHeading("Confocal microscope");
  readonly bookableItemDetailsTarget: Locator = bookableItemTarget("IN123");

  get upcoming(): Locator {
    return page.getByRole("button", { name: "Upcoming", exact: true });
  }

  get past(): Locator {
    return page.getByRole("button", { name: "Past" });
  }

  get upcomingCount(): Locator {
    return page.getByLabelText("2 upcoming bookings");
  }

  get confocalStartDate(): Locator {
    const displayTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const displayedStart = new Intl.DateTimeFormat("en-US", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: displayTimeZone,
    }).format(new Date(upcomingBooking.start));
    return page
      .getByRole("article", { name: "Confocal microscope", exact: false })
      .getByText(displayedStart, { exact: true });
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
}
