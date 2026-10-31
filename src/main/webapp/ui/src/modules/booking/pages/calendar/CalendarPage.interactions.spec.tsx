import "@/modules/common/styles/index.css";
import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import {
  customNewYorkBookingPreferences,
  institutionBookingPreferences,
} from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { collectionResponse, ownBooking } from "../mocks/bookingCalendarFixtures";
import { CalendarPageStory } from "./CalendarPage.story";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

let history: RouterHistory;

beforeEach(() => {
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
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("Time grid");
    await calendar.chooseView("Week");

    const event = calendar.event("Confocal microscope");
    await expect.element(event).toBeVisible();
    // The week grid opens on the availability window; bring the event into view before measuring.
    event.element().scrollIntoView({ block: "center" });
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

  test("lays the week time grid out on one hour axis", async () => {
    const overlapping = { ...ownBooking, id: 46, start: "2026-08-17T09:00:00Z", end: "2026-08-17T11:00:00Z" };
    worker.use(
      http.get("/api/v2/booking-calendar/events", () =>
        HttpResponse.json(collectionResponse([ownBooking, overlapping])),
      ),
    );
    render(<CalendarPageStory history={history} preferences={customNewYorkBookingPreferences} />);
    await calendar.chooseView("Time grid");
    await calendar.chooseView("Week");

    const monday = calendar.timeGrid.getByRole("region", { name: "Monday, August 17, 2026" });
    const cards = monday.getByRole("button", { name: "Show details for Confocal microscope", exact: false });
    await expect.poll(() => cards.all().length).toBe(2);
    // 09:00 New York, at 56px per hour, less the clearance for its label under the sticky header.
    // Firefox reports a sub-pixel scroll offset.
    expect(Math.round(calendar.weekGrid.element().scrollTop)).toBe(9 * 56 - 12);
    const column = monday.element().getBoundingClientRect();
    const [first, second] = cards.all().map((card) => {
      const slot = card.element().closest("li");
      if (!slot) throw new Error("Week events must render inside the day column list");
      return slot.getBoundingClientRect();
    });
    // 04:00-06:00 and 05:00-07:00 New York overlap, so they share the hour axis side by side.
    expect(Math.round(first.top - column.top)).toBe(4 * 56);
    expect(Math.round(second.top - column.top)).toBe(5 * 56);
    expect(Math.round(first.height)).toBe(2 * 56);
    expect(Math.round(first.width)).toBe(Math.round(second.width));
    expect(second.left).toBeGreaterThanOrEqual(first.right - 1);
    await expectNoAxeViolations();

    await cards.first().click();
    await expect.element(calendar.viewItemDetails).toBeVisible();
  });

  test("edits an editable booking inside its expanded calendar card", async () => {
    let updatedPayload: Record<string, unknown> | undefined;
    let ifMatch: string | null = null;
    worker.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        updatedPayload = (await request.json()) as Record<string, unknown>;
        ifMatch = request.headers.get("If-Match");
        return HttpResponse.json({
          ...ownBooking,
          cancellationReason: null,
          ...updatedPayload,
          version: ownBooking.version + 1,
        });
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

  test("discards a stale compact edit, keeps the card open and loads the latest version", async () => {
    let serverBooking: typeof ownBooking = ownBooking;
    const saves: { ifMatch: string | null; payload: Record<string, unknown> }[] = [];
    worker.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([serverBooking]))),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        const ifMatch = request.headers.get("If-Match");
        const payload = (await request.json()) as Record<string, unknown>;
        saves.push({ ifMatch, payload });
        if (ifMatch !== `"${serverBooking.version}"`) {
          return HttpResponse.json({ status: 412, code: "errors.api.v2.preconditionFailed" }, { status: 412 });
        }
        serverBooking = { ...serverBooking, ...payload, version: serverBooking.version + 1 };
        return HttpResponse.json({ ...serverBooking, cancellationReason: null });
      }),
    );
    render(<CalendarPageStory history={history} />);

    await calendar.showEventDetails("Confocal microscope").click();
    const card = page.getByRole("dialog").filter({ hasText: "Confocal microscope" });
    await calendar.editBooking.click();
    const purpose = card.getByRole("textbox", { name: "Purpose" });
    await purpose.fill("Updated cell imaging");
    serverBooking = { ...serverBooking, purpose: "Changed elsewhere", version: 1 };
    await card.getByRole("button", { name: "Save changes" }).click();

    const discard = card.getByRole("button", { name: "Discard my changes and load the latest" });
    await expect.element(discard).toBeEnabled();
    await discard.click();

    // The focused button leaves with the alert; the card stays open on the latest version.
    await expect.element(card).toBeVisible();
    await expect.element(purpose).toHaveValue("Changed elsewhere");
    await expect.element(purpose).toHaveFocus();
    await expect.element(card.getByRole("alert")).not.toBeInTheDocument();
    await purpose.fill("Changed elsewhere, then here");
    await card.getByRole("button", { name: "Save changes" }).click();

    await expect.poll(() => saves.map(({ ifMatch }) => ifMatch)).toEqual(['"0"', '"1"']);
    expect(saves[1].payload).toEqual({ purpose: "Changed elsewhere, then here" });
    await expect.element(purpose).not.toBeInTheDocument();
  });

  test("warns before saving a compact edit that overlaps another booking", async () => {
    let updatedPayload: Record<string, unknown> | undefined;
    worker.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        updatedPayload = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({
          ...ownBooking,
          cancellationReason: null,
          ...updatedPayload,
          version: ownBooking.version + 1,
        });
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
    await expect.element(card.getByLabelText("Start time")).toHaveValue("07:59");
    await expect.element(card.getByLabelText("End time")).toHaveValue("09:59");
    await card.getByLabelText("Start time").fill("12:00");
    await card.getByLabelText("End time").fill("13:00");

    await expect.element(card.getByText("This period overlaps:")).toBeVisible();
    await expect.element(card.getByText("Booking #42", { exact: false })).toBeVisible();
    await expect.element(save).toBeDisabled();
    expect(updatedPayload).toBeUndefined();
  });

  test.each(["By Item", "Time grid"] as const)(
    "aligns the inline editor with its overlap lane in %s",
    async (layout) => {
      const earlier = {
        ...ownBooking,
        id: 46,
        start: "2026-08-17T07:00:00Z",
        end: "2026-08-17T09:00:00Z",
        purpose: "Earlier calibration",
      };
      worker.use(
        http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([earlier, ownBooking]))),
      );
      render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);
      await calendar.chooseView(layout);
      await calendar.chooseView("Day");
      const event = calendar.event("Cell imaging");
      await expect.element(event).toBeVisible();
      const lane = event.element().closest<HTMLElement>("[data-event-id]");
      if (!lane) throw new Error("Booking must have a timeline lane");
      expect(Number(lane.dataset.lane)).toBeGreaterThan(0);
      await event.getByRole("button", { name: "Show details", exact: false }).click();
      await calendar.editBooking.click();
      const overlay = page.getByTestId("timeline-window-editor");
      await expect.element(page.getByRole("button", { name: "Move booking time" })).toBeVisible();
      await expect
        .poll(() =>
          Math.abs(overlay.element().getBoundingClientRect().top - event.element().getBoundingClientRect().top),
        )
        .toBeLessThanOrEqual(1);
      await expect
        .poll(() =>
          Math.abs(overlay.element().getBoundingClientRect().height - event.element().getBoundingClientRect().height),
        )
        .toBeLessThanOrEqual(1);
    },
  );

  test("moves and resizes a booking on the day timeline", async () => {
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);

    await calendar.showEventDetails("Confocal microscope").click();
    const card = page.getByRole("dialog").filter({ hasText: "Confocal microscope" });
    await calendar.editBooking.click();
    const move = page.getByRole("button", { name: "Move booking time" });
    await expect.element(move).toBeVisible();
    const canvas = move.element().closest<HTMLElement>('[data-testid="day-timeline-canvas"]');
    if (!canvas) throw new Error("Timeline editor must be rendered inside a day timeline canvas");
    // The item uses the default 15-minute aligned increment.
    const fifteenMinutes = (canvas.getBoundingClientRect().width / (24 * 60)) * 15;
    const moveBounds = move.element().getBoundingClientRect();

    await userEvent.dragAndDrop(move, move, {
      sourcePosition: { x: moveBounds.width / 2, y: moveBounds.height / 2 },
      targetPosition: { x: moveBounds.width / 2 + fifteenMinutes, y: moveBounds.height / 2 },
    });
    await expect.element(card.getByLabelText("Start time")).toHaveValue("08:15");
    await expect.element(card.getByLabelText("End time")).toHaveValue("10:15");

    const end = page.getByRole("button", { name: "Change booking end time" });
    end.element().focus();
    await userEvent.keyboard("{ArrowLeft}");
    await expect.element(card.getByLabelText("Start time")).toHaveValue("08:15");
    await expect.element(card.getByLabelText("End time")).toHaveValue("10:00");
  });

  test("opens a full event, edits it on its canonical page, and refreshes the readout", async () => {
    let details = { ...ownBooking, cancellationReason: null, createdBy: "Ada Lovelace (ada)" };
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
    await page.getByRole("button", { name: "Save changes" }).click();

    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(page.getByText("Updated from event details", { exact: true })).toBeVisible();
  });

  test("closes booking and busy cards when the page is clicked", async () => {
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("Time grid");
    await calendar.chooseView("Week");

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
