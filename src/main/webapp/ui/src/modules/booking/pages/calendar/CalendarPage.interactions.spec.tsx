import "@/modules/common/styles/index.css";
import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, fireEvent, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { institutionBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { ownBooking } from "../mocks/bookingCalendarFixtures";
import { CalendarPageStory } from "./CalendarPage.story";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

let history: RouterHistory;

beforeEach(() => {
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

  test("warns before saving a compact edit that overlaps another booking", async () => {
    let updatedPayload: Record<string, unknown> | undefined;
    worker.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        updatedPayload = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...ownBooking, ...updatedPayload, version: ownBooking.version + 1 });
      }),
    );
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);

    await calendar.showEventDetails("Confocal microscope").click();
    const card = page.getByRole("dialog").filter({ hasText: "Confocal microscope" });
    await calendar.editBooking.click();
    const save = card.getByRole("button", { name: "Save changes" });

    await expect.element(save).toBeEnabled();
    const moveBooking = page.getByRole("button", { name: "Move booking time" });
    await expect.element(moveBooking).toBeVisible();
    moveBooking.element().focus();
    await userEvent.keyboard("{ArrowLeft}");
    await expect.element(card.getByLabelText("Start time")).toHaveValue("07:55");
    await expect.element(card.getByLabelText("End time")).toHaveValue("09:55");
    await card.getByLabelText("Start time").fill("12:00");
    await card.getByLabelText("End time").fill("13:00");

    await expect.element(card.getByText("This period overlaps:")).toBeVisible();
    await expect.element(save).toBeDisabled();
    expect(updatedPayload).toBeUndefined();
  });

  test("moves and resizes a booking on the day timeline", async () => {
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);

    await calendar.showEventDetails("Confocal microscope").click();
    const card = page.getByRole("dialog").filter({ hasText: "Confocal microscope" });
    await calendar.editBooking.click();
    const move = page.getByRole("button", { name: "Move booking time" });
    await expect.element(move).toBeVisible();
    const canvas = move.element().closest<HTMLElement>('[data-testid="day-timeline-canvas"]');
    if (!canvas) throw new Error("Timeline editor must be rendered inside a day timeline canvas");
    const fiveMinutes = (canvas.getBoundingClientRect().width / (24 * 60)) * 5;
    const moveBounds = move.element().getBoundingClientRect();

    await userEvent.dragAndDrop(move, move, {
      sourcePosition: { x: moveBounds.width / 2, y: moveBounds.height / 2 },
      targetPosition: { x: moveBounds.width / 2 + fiveMinutes, y: moveBounds.height / 2 },
    });
    await expect.element(card.getByLabelText("Start time")).toHaveValue("08:05");
    await expect.element(card.getByLabelText("End time")).toHaveValue("10:05");

    const end = page.getByRole("button", { name: "Change booking end time" });
    const endElement = end.element();
    const endBounds = endElement.getBoundingClientRect();
    const editor = endElement.closest<HTMLElement>("[data-timeline-window-editor]");
    if (!editor) throw new Error("Resize handle must be rendered inside a timeline editor");
    const pointerY = endBounds.top + endBounds.height / 2;
    const targetX = editor.getBoundingClientRect().right - fiveMinutes;
    fireEvent.pointerDown(endElement, {
      pointerId: 2,
      clientX: endBounds.left + endBounds.width / 2,
      clientY: pointerY,
    });
    fireEvent.pointerMove(endElement, { pointerId: 2, clientX: targetX, clientY: pointerY });
    fireEvent.pointerUp(endElement, { pointerId: 2, clientX: targetX, clientY: pointerY });
    await expect.element(card.getByLabelText("Start time")).toHaveValue("08:05");
    await expect.element(card.getByLabelText("End time")).toHaveValue("10:00");
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
    render(<CalendarPageStory history={history} />);

    await calendar.showEventDetails("Confocal microscope").click();
    await calendar.viewItemDetails.click();
    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(page.getByRole("heading", { name: "Booking details" })).toBeVisible();

    await page.getByRole("link", { name: "Edit" }).click();
    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41/edit");
    const purpose = page.getByRole("textbox", { name: "Purpose" });
    await purpose.fill("Updated from event details");
    await page.getByRole("button", { name: "Save" }).click();

    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(page.getByText("Updated from event details", { exact: true })).toBeVisible();
  });

  test("closes booking and busy cards when the page is clicked", async () => {
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
