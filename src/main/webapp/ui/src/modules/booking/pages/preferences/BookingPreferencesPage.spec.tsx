import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingDisplayPreferencesControllerReplacement } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferencesControllerReplacement";
import "@/modules/common/styles/index.css";
import { createMemoryHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import type { BookingDisplayPreferences } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferences";
import { bookingPagesHandlers } from "../mocks/bookingPagesMocks";
import { BookingPreferencesPageStory } from "./BookingPreferencesPage.story";
import { inheritedBrowserBookingPreferences } from "./bookingPreferencesFixtures";
import { BookingPreferencesPage } from "./pageObjects/BookingPreferencesPage";

const preferences = new BookingPreferencesPage();
let stored: BookingDisplayPreferences;

function registerHandlers() {
  worker.use(
    ...bookingPagesHandlers(),
    http.get<never, never, BookingDisplayPreferences>("/api/v2/users/me/booking-preferences", () =>
      HttpResponse.json(stored),
    ),
    http.put<never, BookingDisplayPreferencesControllerReplacement, BookingDisplayPreferences | ApiV2Problem>(
      "/api/v2/users/me/booking-preferences",
      async ({ request }) => {
        stored = {
          ...inheritedBrowserBookingPreferences,
          ...(await request.json()),
          overridden: true,
        };
        return HttpResponse.json(stored);
      },
    ),
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
      .element(
        page.getByRole("region", {
          name: "24-hour calendar for Confocal microscope on Monday, August 17, 2026 in America/New_York",
          exact: true,
        }),
      )
      .toBeVisible();
    await expect.element(page.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();

    await expectNoAxeViolations();
    expect(window.location.href).toBe(browserUrl);
  });
});
