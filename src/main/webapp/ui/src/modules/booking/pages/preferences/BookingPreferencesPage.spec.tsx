import type { ApiV2CurrentUser } from "@/modules/common/api/v2/generated/models/apiV2CurrentUser";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import type { BookingDisplayPreferencesControllerReplacement } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferencesControllerReplacement";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import type { BookingDisplayPreferences } from "@/modules/common/api/v2/generated/models/bookingDisplayPreferences";
import { bookableItemsHandlers } from "../bookable-items/mocks/bookableItemsMocks";
import { busyBooking, collectionResponse, otherBooking, ownBooking } from "../mocks/bookingCalendarFixtures";
import { BookingPreferencesPageStory } from "./BookingPreferencesPage.story";
import { inheritedBrowserBookingPreferences } from "./bookingPreferencesFixtures";
import { BookingPreferencesPage } from "./pageObjects/BookingPreferencesPage";

const preferences = new BookingPreferencesPage();
let stored: BookingDisplayPreferences;

function registerHandlers() {
  worker.use(
    oauthTokenHandler(true),
    ...bookableItemsHandlers(() => undefined),
    http.get<never, never, ApiV2CurrentUser>("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    http.get<never, never, ListBookings200>("/api/v2/bookings", () =>
      HttpResponse.json({
        ...collectionResponse([ownBooking, otherBooking, busyBooking]),
        docs: [ownBooking, otherBooking, busyBooking],
      }),
    ),
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
  window.history.replaceState({}, "", "/booking/preferences");
  registerHandlers();
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("Booking display preferences", () => {
  test("a custom preference survives reload and applies to the calendar", async () => {
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
    render(<BookingPreferencesPageStory />);
    await expect.element(preferences.custom).toBeChecked();
    await expect.element(preferences.customTimezone).toHaveValue("America/New_York");

    window.history.pushState({}, "", "/booking/calendar?date=2026-08-17");
    window.dispatchEvent(new PopStateEvent("popstate"));
    await expect.element(page.getByLabelText("Time zone: America/New_York")).toBeVisible();
    await expect.element(page.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    await expectNoAxeViolations();
  });
});
