import { type Locator, page } from "vitest/browser";

export class BookingPreferencesPage {
  readonly heading: Locator = page.getByRole("heading", { name: "Booking preferences" });
  readonly start: Locator = page.getByLabelText("Start time");
  readonly end: Locator = page.getByLabelText("End time");
  readonly custom: Locator = page.getByRole("radio", { name: "Use Custom Timezone" });
  readonly customTimezone: Locator = page.getByRole("combobox", { name: "Custom timezone" });
  readonly save: Locator = page.getByRole("button", { name: "Save" });
  readonly saved: Locator = page.getByText("Booking preferences saved.");
}
