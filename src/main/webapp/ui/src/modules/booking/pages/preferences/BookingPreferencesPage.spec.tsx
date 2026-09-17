import { createMemoryHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import type { BookingDisplayPreferencesDocument } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingPagesHandlers } from "../mocks/bookingPagesMocks";
import { BookingPreferencesPageStory } from "./BookingPreferencesPage.story";
import { inheritedBrowserBookingPreferences } from "./bookingPreferencesFixtures";
import { BookingPreferencesPage } from "./pageObjects/BookingPreferencesPage";

const preferences = new BookingPreferencesPage();
let stored: BookingDisplayPreferencesDocument;

function registerHandlers() {
  worker.use(
    ...bookingPagesHandlers(),
    http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(stored)),
    http.put("/api/v2/users/me/booking-preferences", async ({ request }) => {
      stored = {
        ...inheritedBrowserBookingPreferences,
        ...((await request.json()) as Omit<BookingDisplayPreferencesDocument, "institutionTimezone" | "overridden">),
        overridden: true,
      };
      return HttpResponse.json(stored);
    }),
    http.delete("/api/v2/users/me/booking-preferences", () => {
      stored = inheritedBrowserBookingPreferences;
      return new HttpResponse(null, { status: 204 });
    }),
  );
}

registerHandlers();

beforeEach(() => {
  stored = inheritedBrowserBookingPreferences;
  registerHandlers();
});

afterEach(() => {
  cleanup();
});

describe("Booking display preferences", () => {
  test("a custom preference survives reload and remains authoritative across Booking routes", async () => {
    const browserUrl = window.location.href;
    const first = render(<BookingPreferencesPageStory />);
    await expect.element(preferences.heading).toBeVisible();
    await preferences.start.fill("09:00");
    await preferences.end.fill("17:00");
    await preferences.custom.click();
    await preferences.customTimezone.fill("America/New_York");
    await preferences.save.click();
    await expect.element(preferences.saved).toBeVisible();
    expect(stored).toMatchObject({
      availabilityWindowStart: "09:00",
      availabilityWindowEnd: "17:00",
      timezoneMode: "CUSTOM",
      customTimezone: "America/New_York",
      overridden: true,
    });

    first.unmount();
    const history = createMemoryHistory({ initialEntries: ["/booking/preferences"] });
    render(<BookingPreferencesPageStory history={history} />);
    await expect.element(preferences.custom).toBeChecked();
    await expect.element(preferences.customTimezone).toHaveValue("America/New_York");

    history.push("/booking/calendar?date=2026-08-17");
    await expect
      .element(page.getByRole("region", { name: /24-hour calendar for Confocal microscope.*America\/New_York/ }))
      .toBeVisible();
    await expect.element(page.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();

    history.push("/booking/all-items?date=2026-08-28");
    await expect.element(page.getByRole("heading", { name: "All Bookable Items" })).toBeVisible();
    await expect.element(page.getByLabelText("Time zone: America/New_York")).not.toBeInTheDocument();
    const itemNames = ["Confocal microscope", "Electron microscope", "Mass spectrometer", "Flow cytometer"];
    await expect
      .poll(
        () =>
          new Set(
            itemNames.map(
              (name) =>
                page
                  .getByRole("img", { name: `${name} availability` })
                  .getByTitle(/Current time/)
                  .element().style.left,
            ),
          ),
      )
      .toEqual(new Set(["37.5%"]));
    await expectNoAxeViolations();
    expect(window.location.href).toBe(browserUrl);
  });
});
