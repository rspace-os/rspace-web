import { type Locator, page } from "vitest/browser";

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

  get electron(): Locator {
    return page.getByText("Electron microscope", { exact: true });
  }

  get confocalDetails(): Locator {
    return page.getByRole("link", { name: "View details", exact: true }).first();
  }

  get roleLossNotice(): Locator {
    return page.getByText("Read-only: you no longer have access to this item.");
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
