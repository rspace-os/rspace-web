import { type Locator, page } from "vitest/browser";

function summariesWithin(root: Locator): Locator {
  // Vitest Browser does not expose native <summary> as a role locator in Chromium.
  return (root as unknown as { locator(selector: string): Locator }).locator("css=summary");
}

export class BookingDashboardPageObject {
  get heading(): Locator {
    return page.getByRole("heading", { name: "Dashboard", exact: true });
  }

  get quickActions(): Locator {
    return page.getByRole("region", { name: "Quick Actions", exact: true });
  }

  quickAction(name: string): Locator {
    return this.quickActions.getByRole("link", { name: new RegExp(`^${name}`) });
  }

  get upcoming(): Locator {
    return page.getByRole("region", { name: "Upcoming Bookings", exact: true });
  }

  upcomingInstrument(name: string): Locator {
    return summariesWithin(this.upcoming).filter({ hasText: name });
  }

  upcomingDisclosure(name: string): Locator {
    return this.upcomingInstrument(name);
  }

  upcomingDetailsLink(name: string): Locator {
    return this.upcoming.getByRole("link", { name: new RegExp(name) });
  }

  upcomingPurpose(text: string): Locator {
    return this.upcoming.getByText(text, { exact: true });
  }

  get upcomingLoading(): Locator {
    return this.upcoming.getByRole("status");
  }

  get upcomingRetry(): Locator {
    return this.upcoming.getByRole("button", { name: "Retry", exact: true });
  }

  get upcomingViewAll(): Locator {
    return this.upcoming.getByRole("link", { name: "View all", exact: true });
  }

  get upcomingError(): Locator {
    return this.upcoming.getByRole("alert");
  }

  get calendar(): Locator {
    return page.getByRole("region", { name: "At a glance", exact: true });
  }

  calendarDay(name: string): Locator {
    return this.calendar.getByRole("button", { name: new RegExp(`^${name}:`) });
  }

  get calendarError(): Locator {
    return this.calendar.getByRole("alert");
  }

  get popup(): Locator {
    return page.getByRole("dialog");
  }

  summary(heading: string): Locator {
    return this.popup.getByText(heading, { exact: true });
  }

  detailsSummary(heading: string, period: string): Locator {
    return this.popup.getByLabelText(`Show details for ${heading}, ${period}`);
  }

  summaryDisclosure(heading: string): Locator {
    return this.popup.getByLabelText(new RegExp(`^Show details for ${heading},`));
  }

  get range(): Locator {
    return this.popup.getByText(/\d+[\u2013-]\d+ of \d+/, { exact: false });
  }

  get previous(): Locator {
    return this.popup.getByRole("button", { name: "Previous bookings", exact: true });
  }

  get next(): Locator {
    return this.popup.getByRole("button", { name: "Next bookings", exact: true });
  }
}
