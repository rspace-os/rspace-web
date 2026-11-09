import type { BookingsUpdate } from "@/modules/common/api/v2/generated/models/bookingsUpdate";
import "@/modules/common/styles/index.css";
import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { withViewport } from "@/__tests__/pageObjects/viewport";
import { resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { ownBooking } from "../mocks/bookingCalendarFixtures";
import { CalendarPageStory } from "./CalendarPage.story";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

let history: RouterHistory;

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-08-01T00:00:00Z"));
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] });
  resetBookingPageRequests();
  registerHandlers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("Calendar page", () => {
  test("keeps the event expand control fixed when details open", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
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
    const details = page.getByRole("dialog").filter({ hasText: "Confocal microscope" }).element();
    const facts = details.querySelector("dl");
    const firstFact = facts?.firstElementChild;
    expect(facts).not.toBeNull();
    expect(firstFact).not.toBeNull();
    expect(firstFact?.getBoundingClientRect().left).toBe(facts?.getBoundingClientRect().left);
    expect(firstFact?.getBoundingClientRect().right).toBe(facts?.getBoundingClientRect().right);

    expect(expandedPosition?.top).toBe(collapsedPosition?.top);
    expect(expandedPosition?.right).toBe(collapsedPosition?.right);
  });

  test("does not expose details for a busy event", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
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
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);

    await calendar.showEventDetails("Confocal microscope").click();
    const card = page.getByRole("dialog").filter({ hasText: "Confocal microscope" });
    await calendar.editBooking.click();

    await expect.poll(() => history.location.pathname).toBe("/booking/calendar");
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
    await expect.poll(() => history.location.pathname).toBe("/booking/calendar");
  });

  test("opens a full event, edits it on its canonical page, and refreshes the readout", async () => {
    if (ownBooking.privacy !== "full") throw new Error("The own-booking fixture must expose full details");
    let details = { ...ownBooking, createdBy: "Ada Lovelace (ada)" };
    worker.use(
      http.get("/api/v2/bookings/41", () => HttpResponse.json(details)),
      http.patch<never, BookingsUpdate>("/api/v2/bookings/41", async ({ request }) => {
        const payload = await request.json();
        details = { ...details, ...payload, version: details.version + 1 };
        return HttpResponse.json(details);
      }),
    );
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);

    await calendar.showEventDetails("Confocal microscope").click();
    await calendar.viewItemDetails.click();
    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(page.getByRole("heading", { name: "Booking details" })).toBeVisible();

    await page.getByRole("link", { name: "Edit" }).click();
    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41/edit");
    const purpose = page.getByRole("textbox", { name: "Purpose" });
    await purpose.fill("Updated from event details");
    await page.getByRole("button", { name: "Save changes" }).click();

    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(page.getByText("Updated from event details", { exact: true })).toBeVisible();
  });

  test("closes booking and busy cards when the page is clicked", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
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

test("keeps mobile event editor actions in the viewport and restores focus on Escape", async () => {
  await withViewport(
    390,
    async () => {
      render(<CalendarPageStory history={history} />);
      const trigger = calendar.showEventDetails("Confocal microscope");
      await trigger.click();
      await calendar.editBooking.click();
      await expect.element(calendar.inlineSave).toBeVisible();
      await expect
        .poll(() => {
          const bounds = calendar.inlineSave.element().getBoundingClientRect();
          return (
            bounds.left >= 0 &&
            bounds.right <= window.innerWidth &&
            bounds.top >= 0 &&
            bounds.bottom <= window.innerHeight
          );
        })
        .toBe(true);
      await calendar.closeWithEscape();
      await expect.element(page.getByRole("dialog")).not.toBeInTheDocument();
      await expect.poll(() => document.activeElement).toBe(trigger.element());
    },
    667,
  );
});
