import { type Locator, page } from "vitest/browser";

export class BookingPreferencesPage {
  readonly heading: Locator = page.getByRole("heading", { name: "Booking preferences" });
  readonly start: Locator = page.getByLabelText("Start time");
  readonly end: Locator = page.getByLabelText("End time");
  readonly browser: Locator = page.getByRole("radio", { name: /Use Browser Timezone/ });
  readonly institution: Locator = page.getByRole("radio", { name: /Use Institution Timezone/ });
  readonly custom: Locator = page.getByRole("radio", { name: "Use Custom Timezone" });
  readonly customTimezone: Locator = page.getByRole("combobox", { name: "Custom timezone" });
  // The display settings form comes before the Booking notifications section, which has its own Save.
  readonly save: Locator = page.getByRole("button", { name: "Save" }).first();
  readonly saved: Locator = page.getByRole("button", { name: "Saved" });
  readonly reset: Locator = page.getByRole("button", { name: "Reset to global defaults" });
  readonly resetComplete: Locator = page.getByText("Global Booking defaults restored.");
}
