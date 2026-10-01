import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { ownBooking } from "../mocks/bookingCalendarFixtures";
import { CalendarPageStory } from "./CalendarPage.story";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

beforeEach(() => {
  resetBookingPageRequests();
  registerHandlers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("Calendar page", () => {
  test("keeps the event expand control fixed when details open", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    const event = calendar.event("Confocal microscope");
    await expect.element(event).toBeVisible();
    const indicator = event.element().querySelector<HTMLElement>("[data-event-expand-indicator]");
    expect(indicator).not.toBeNull();
    const collapsedPosition = indicator?.getBoundingClientRect();

    await calendar.showEventDetails("Confocal microscope").click();
    await expect
      .element(event.getByRole("button", { name: "Hide details", exact: false }))
      .toHaveAttribute("aria-expanded", "true");
    const expandedPosition = indicator?.getBoundingClientRect();

    expect(expandedPosition?.top).toBe(collapsedPosition?.top);
    expect(expandedPosition?.right).toBe(collapsedPosition?.right);
  });

  test("does not expose details for a busy event", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    await calendar.showEventDetails("Busy").click();
    await expect.element(calendar.viewItemDetails).not.toBeInTheDocument();
  });

  test("edits an editable booking inside its expanded calendar card", async () => {
    let updatedPayload: Record<string, unknown> | undefined;
    let ifMatch: string | null = null;
    worker.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        updatedPayload = (await request.json()) as Record<string, unknown>;
        ifMatch = request.headers.get("If-Match");
        return HttpResponse.json({ ...ownBooking, ...updatedPayload, version: ownBooking.version + 1 });
      }),
    );
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await calendar.showEventDetails("Confocal microscope").click();
    const card = page.getByRole("dialog").filter({ hasText: "Confocal microscope" });
    await calendar.editBooking.click();

    await expect.poll(() => window.location.pathname).toBe("/booking/calendar");
    const purpose = card.getByRole("textbox", { name: "Purpose" });
    await expect.element(purpose).toBeVisible();
    expect(card.element().querySelectorAll('input[type="date"]')).toHaveLength(1);
    const editDate = card.getByLabelText("Date");
    const editStartTime = card.getByLabelText("Start time");
    const editEndTime = card.getByLabelText("End time");
    expect(editDate.element().getBoundingClientRect().top).toBeLessThan(
      editStartTime.element().getBoundingClientRect().top,
    );
    expect(editStartTime.element().getBoundingClientRect().top).toBe(editEndTime.element().getBoundingClientRect().top);
    await purpose.fill("Updated cell imaging");
    await card.getByRole("button", { name: "Save changes" }).click();

    await expect.poll(() => updatedPayload).toEqual({ purpose: "Updated cell imaging" });
    expect(ifMatch).toBe('"0"');
    await expect.element(purpose).not.toBeInTheDocument();
    await expect.poll(() => window.location.pathname).toBe("/booking/calendar");
  });

  test("opens a full event, edits it on its canonical page, and refreshes the readout", async () => {
    let details = { ...ownBooking, createdBy: "Ada Lovelace (ada)" };
    worker.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json(details)),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        const payload = (await request.json()) as Partial<typeof details>;
        details = { ...details, ...payload, version: details.version + 1 };
        return HttpResponse.json(details);
      }),
    );
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await calendar.showEventDetails("Confocal microscope").click();
    await calendar.viewItemDetails.click();
    await expect.poll(() => window.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(page.getByRole("heading", { name: "Booking details" })).toBeVisible();

    await page.getByRole("link", { name: "Edit" }).click();
    await expect.poll(() => window.location.pathname).toBe("/booking/calendar/bookings/41/edit");
    const purpose = page.getByRole("textbox", { name: "Purpose" });
    await purpose.fill("Updated from event details");
    await page.getByRole("button", { name: "Save" }).click();

    await expect.poll(() => window.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(page.getByText("Updated from event details", { exact: true })).toBeVisible();
  });

  test("closes booking and busy cards when the page is clicked", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    await calendar.event("Confocal microscope").click();
    await expect
      .element(calendar.event("Confocal microscope").getByRole("button", { name: "Hide details", exact: false }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Confocal microscope")).toHaveAttribute("aria-expanded", "false");

    await calendar.event("Busy").click();
    await expect
      .element(calendar.event("Busy").getByRole("button", { name: "Hide details", exact: false }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Busy")).toHaveAttribute("aria-expanded", "false");
  });
});
