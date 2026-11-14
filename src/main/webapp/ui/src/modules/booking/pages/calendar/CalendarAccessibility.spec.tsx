import { mapBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import "@/modules/common/styles/index.css";
import { createMemoryHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { collectionResponse, ownBooking } from "../mocks/bookingCalendarFixtures";
import { institutionBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { CalendarPageStory } from "./CalendarPage.story";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

beforeEach(registerHandlers);
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

test("keeps the mobile editor and Save on screen and returns focus after Escape", async () => {
  await page.viewport(390, 844);
  render(
    <CalendarPageStory history={createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] })} />,
  );
  const trigger = calendar.showEventDetails("Confocal microscope");
  await trigger.click();
  await calendar.editBooking.click();
  await expect.element(calendar.inlineSave).toBeVisible();
  await expect
    .poll(() => {
      const box = calendar.inlineSave.element().getBoundingClientRect();
      return box.bottom <= 844 && box.top >= 0 && box.left >= 0 && box.right <= 390;
    })
    .toBe(true);
  await expectNoAxeViolations();
  await calendar.closeWithEscape();
  await expect.element(trigger).toHaveFocus();
});

test("saves a booking time change from the mobile calendar details sheet", async () => {
  await page.viewport(390, 844);
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-08-01T00:00:00Z"));
  let serverBooking = ownBooking;
  let updatedPayload: Record<string, unknown> | undefined;
  worker.use(
    http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([serverBooking]))),
    http.patch("/api/v2/bookings/41", async ({ request }) => {
      updatedPayload = (await request.json()) as Record<string, unknown>;
      serverBooking = { ...serverBooking, ...updatedPayload, version: serverBooking.version + 1 };
      return HttpResponse.json({ ...serverBooking, cancellationReason: null });
    }),
  );
  render(
    <CalendarPageStory
      history={createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] })}
      preferences={mapBookingDisplayPreferences(institutionBookingPreferences)}
    />,
  );
  await calendar.chooseView("Day");
  await calendar.showEventDetails("Confocal microscope").click();
  await calendar.editBooking.click();
  const sheet = page.getByRole("dialog").filter({ hasText: "Confocal microscope" });
  await sheet.getByLabelText("Start time").fill("08:15");
  await sheet.getByLabelText("End time").fill("09:45");
  await expectNoAxeViolations();
  await calendar.inlineSave.click();

  await expect
    .poll(() => updatedPayload)
    .toEqual({
      start: "2026-08-17T08:15:00Z",
      end: "2026-08-17T09:45:00Z",
    });
  await expect.element(calendar.inlineSave).not.toBeInTheDocument();
  await expect.element(calendar.event("Confocal microscope")).toMatchTextContent("08:15 AM–09:45 AM");
});

test("supports the Availability view menu with keyboard and accessible period reasons", async () => {
  await page.viewport(390, 844);
  render(
    <CalendarPageStory
      history={createMemoryHistory({ initialEntries: ["/booking/calendar?layout=availability&date=2026-08-17"] })}
    />,
  );
  await expect.element(page.getByRole("img", { name: "Confocal microscope availability" })).toBeVisible();
  await calendar.viewMenu.click();
  await expect
    .element(page.getByRole("menuitemradio", { name: "Week", exact: true }))
    .toHaveAttribute("aria-disabled", "true");
  await expect
    .element(page.getByRole("menuitemradio", { name: "Month", exact: true }))
    .toHaveAccessibleDescription("Availability is available for Day only.");
  await expectNoAxeViolations();
  await calendar.closeWithEscape();
  await expect.element(calendar.viewMenu).toHaveFocus();
});
