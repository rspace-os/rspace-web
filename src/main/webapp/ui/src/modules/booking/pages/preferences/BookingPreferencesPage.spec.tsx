import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import type { BookingDisplayPreferencesDocument } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  bookableItemDetailsHandlers,
  bookableItemsHandlers,
  sampleBookingEvents,
} from "../bookable-items/mocks/bookableItemsMocks";
import {
  busyBooking,
  collectionResponse,
  currentUser,
  otherBooking,
  ownBooking,
} from "../calendar/__tests__/calendarTestHarness";
import { BookingPreferencesPageStory } from "./BookingPreferencesPage.story";
import { inheritedBrowserBookingPreferences } from "./bookingPreferencesFixtures";
import { BookingPreferencesPage } from "./pageObjects/BookingPreferencesPage";

const preferences = new BookingPreferencesPage();
const availabilityBookingFields = "id,target,timezone,start,end,state";
const calendarBookingFields =
  "id,target,requesterId,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,createdAt,updatedAt";
let stored: BookingDisplayPreferencesDocument;

function registerHandlers() {
  worker.use(
    oauthTokenHandler(),
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
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
    ...bookableItemDetailsHandlers(),
    ...bookableItemsHandlers(() => undefined),
    http.get("/api/v2/bookings", ({ request }) => {
      const fields = new URL(request.url).searchParams.get("fields[bookings]");
      if (fields === calendarBookingFields) {
        return HttpResponse.json(collectionResponse([ownBooking, otherBooking, busyBooking]));
      }
      if (fields === availabilityBookingFields) {
        return HttpResponse.json({
          docs: sampleBookingEvents,
          totalDocs: sampleBookingEvents.length,
          totalPages: 1,
          page: 1,
          hasNextPage: false,
        });
      }
      return undefined;
    }),
  );
}

function navigate(path: string) {
  window.history.pushState({}, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
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
  test("a custom preference survives reload and remains authoritative across Booking routes", async () => {
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

    navigate("/booking/calendar?date=2026-08-17");
    await expect.element(page.getByText("America/New_York", { exact: true })).toBeVisible();
    await expect.element(page.getByRole("region", { name: "Time grid" })).toBeVisible();

    navigate("/booking/all-items?date=2026-08-28");
    await expect.element(page.getByRole("heading", { name: "All Bookable Items" })).toBeVisible();
    await expect.element(page.getByText("America/New_York", { exact: true })).toBeVisible();
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
          ).size,
      )
      .toBe(1);
    await expectNoAxeViolations();
  });
});
