import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import {
  bookingPageRequests,
  bookingPagesHandlers,
  calendarBookingFields,
  resetBookingPageRequests,
} from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { customNewYorkBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { bookableItemFixtures } from "../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, ownBooking } from "./__tests__/calendarTestHarness";
import { CalendarPageStory } from "./CalendarPage.story";
import { CalendarPage as CalendarPageObject } from "./pageObjects/CalendarPage";

const calendar = new CalendarPageObject();

function registerHandlers(): void {
  worker.use(...bookingPagesHandlers());
}

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
  window.history.replaceState({}, "", "/");
});

describe("Calendar page", () => {
  test("fills each resource row with its timeline and places locations below IDs", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await expect
      .element(page.getByRole("region", { name: "Resources", exact: true }))
      .toHaveAttribute("aria-busy", "false");
    const scrollers = page.getByTestId("day-timeline-scroller");
    await expect.element(scrollers.first()).toBeVisible();
    const id = calendar.resourceSchedule.getByText("IN123", { exact: true });
    const location = calendar.resourceSchedule.getByRole("link", { name: "Imaging lab" });
    await expect.element(location).toBeVisible();
    expect(location.element().getBoundingClientRect().top).toBeGreaterThanOrEqual(
      id.element().getBoundingClientRect().bottom,
    );
    expect(scrollers.first().element().getBoundingClientRect().height).toBeLessThanOrEqual(132);
    for (const scroller of scrollers.elements()) {
      const row = scroller.parentElement?.closest("section.grid");
      if (!row) throw new Error("Timeline must belong to a resource row");
      expect(
        Math.abs(scroller.getBoundingClientRect().bottom - row.getBoundingClientRect().bottom),
      ).toBeLessThanOrEqual(2);
    }
  });

  test.each(["date", "layout", "period"])("resets an isolated %s change to Calendar defaults", async (control) => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-18T00:30:00Z"));
    window.history.replaceState({}, "", "/booking/calendar?calendar-resources.q=Confocal");
    render(<CalendarPageStory preferences={customNewYorkBookingPreferences} />);
    await expect.element(calendar.timeZone).toHaveTextContent("America/New_York");
    await expect.element(calendar.reset).not.toBeInTheDocument();

    if (control === "date") await calendar.next.click();
    if (control === "layout") await calendar.agenda.click();
    if (control === "period") await calendar.week.click();
    await expect.element(calendar.reset).toBeVisible();
    await calendar.reset.click();

    await expect.element(calendar.resources).toHaveAttribute("aria-pressed", "true");
    await expect.element(calendar.day).toHaveAttribute("aria-pressed", "true");
    await expect.element(calendar.mine).toHaveAttribute("aria-pressed", "false");
    await expect.element(calendar.reset).not.toBeInTheDocument();
    await expect.poll(() => new URLSearchParams(window.location.search).has("date")).toBe(false);
    expect(new URLSearchParams(window.location.search).get("calendar-resources.q")).toBe("Confocal");
    await expect
      .element(calendar.resourceSchedule.getByRole("heading", { name: "Monday, August 17, 2026" }).first())
      .toBeVisible();
  });

  test("resets event filters and My calendar while preserving resource search", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-19T00:30:00Z"));
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17&calendar-resources.q=Confocal");
    render(<CalendarPageStory preferences={customNewYorkBookingPreferences} />);
    await calendar.search.fill("no matching event");
    await calendar.mine.click();
    await calendar.filters.click();
    await page.getByRole("button", { name: "Add filter", exact: true }).click();
    await page.getByRole("textbox", { name: "Value for filter 1" }).fill("no matching purpose");
    await page.getByRole("button", { name: "Apply filters" }).click();
    await expect.element(calendar.filters).toHaveAccessibleName("Filters, 1 applied");
    await calendar.week.click();
    await expect
      .element(page.getByRole("region", { name: "Resources", exact: true }))
      .toHaveAttribute("aria-busy", "false");
    const requestsBeforeReset = bookingPageRequests.calendarBookingRequests.length;
    await calendar.reset.click();
    await expect.element(calendar.search).toHaveValue("");
    await expect.element(calendar.mine).toHaveAttribute("aria-pressed", "false");
    await expect.element(calendar.day).toHaveAttribute("aria-pressed", "true");
    await expect.element(calendar.filters).toHaveAccessibleName("Filters, none applied");
    await expect.element(calendar.reset).not.toBeInTheDocument();
    expect(new URLSearchParams(window.location.search).get("calendar-resources.q")).toBe("Confocal");
    await expect
      .poll(() =>
        bookingPageRequests.calendarBookingRequests
          .slice(requestsBeforeReset)
          .map((url) => url.searchParams.get("where")),
      )
      .toEqual(["start<2026-08-19T04:00:00Z;end>2026-08-18T04:00:00Z;state==CONFIRMED;target=in=(IN123)"]);
  });

  test.each([390, 1279, 1280, 1440])("keeps integrated Calendar controls reachable at %s px", async (width) => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(width, 900);
    render(<CalendarPageStory />);
    try {
      await expect.element(calendar.toolbar).toBeVisible();
      await document.fonts.ready;
      for (const control of [
        calendar.dateControls,
        calendar.displayControls,
        calendar.search,
        calendar.mine,
        calendar.filters,
      ]) {
        expect(calendar.toolbar.element()).toContainElement(control.element());
      }
      for (const control of calendar.toolbar.getByRole("button").all()) {
        const bounds = control.element().getBoundingClientRect();
        expect(bounds.left).toBeGreaterThanOrEqual(0);
        expect(bounds.right).toBeLessThanOrEqual(width);
      }
      await calendar.timeGridLayout.click();
      await calendar.month.click();
      await expect.element(calendar.month).toHaveAttribute("aria-pressed", "true");
      await calendar.resources.click();
      await expect.element(calendar.week).toHaveAttribute("aria-pressed", "true");
      await expect.element(calendar.month).toBeDisabled();
      await userEvent.keyboard("{Tab}{Enter}");
      await expect.element(calendar.agenda).toHaveAttribute("aria-pressed", "true");
      // Month fades from disabled to enabled when leaving Resources.
      await expect.poll(() => getComputedStyle(calendar.month.element()).opacity).toBe("1");
      await expectNoAxeViolations();
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test.each([390, 1440])(
    "keeps the week resource grid through sequential resource/event loading at %s px",
    async (width) => {
      const originalViewport = { width: window.innerWidth, height: window.innerHeight };
      const resources = Promise.withResolvers<void>();
      const events = Promise.withResolvers<void>();
      worker.use(
        http.get("/api/v2/booking-catalogue", async () => {
          await resources.promise;
          return HttpResponse.json({
            items: bookableItemFixtures.slice(0, 4).map((item) => ({
              ...item,
              configurationId: item.id,
              targetId: item.target.value.id,
              globalId: item.target.globalId,
              name: item.target.value.name,
              configurationVersion: item.configurationVersion,
              targetType: "INSTRUMENT",
              effectiveRole: item.effectiveRole,
              capabilities: item.capabilities,
              location: null,
            })),
            page: 1,
            pageSize: 20,
            total: 4,
            facets: { types: ["INSTRUMENT"] },
          });
        }),
        http.get("/api/v2/bookings", async () => {
          await events.promise;
          return HttpResponse.json(collectionResponse([]));
        }),
      );
      await page.viewport(width, 900);
      render(<CalendarPageStory />);
      try {
        await calendar.week.click();
        const pending = page.getByRole("region", { name: "Loading", exact: true });
        await expect.element(pending).toHaveAttribute("aria-busy", "true");
        const dateCount = 7;
        await expect.poll(() => pending.element().querySelectorAll("[data-calendar-date]").length).toBe(dateCount);
        await expect.element(page.getByText("Confocal microscope", { exact: true })).not.toBeInTheDocument();
        await expect.element(pending.getByRole("button")).not.toBeInTheDocument();
        await document.fonts.ready;
        const before = pending.element().getBoundingClientRect();
        await expectNoAxeViolations();
        resources.resolve();
        const loaded = page.getByRole("region", { name: "Resources", exact: true });
        await expect.element(loaded.getByText("IN123", { exact: true })).toBeVisible();
        await expect.element(loaded).toHaveAttribute("aria-busy", "true");
        const rowsReady = loaded.element().getBoundingClientRect();
        expect(Math.abs(rowsReady.height - before.height)).toBeLessThanOrEqual(8);
        expect(Math.abs(rowsReady.width - before.width)).toBeLessThanOrEqual(2);
        expect(Math.abs(rowsReady.top - before.top)).toBeLessThanOrEqual(2);
        events.resolve();
        await expect.element(loaded).toHaveAttribute("aria-busy", "false");
        const after = loaded.element().getBoundingClientRect();
        expect(Math.abs(after.height - before.height)).toBeLessThanOrEqual(8);
        expect(loaded.element().querySelectorAll("[data-calendar-date]").length).toBe(dateCount);
      } finally {
        resources.resolve();
        events.resolve();
        await page.viewport(originalViewport.width, originalViewport.height);
      }
    },
  );

  test.each([390, 1440].flatMap((width) => (["day", "week", "month"] as const).map((view) => ({ width, view }))))(
    "keeps the $view grid geometry while events load at $width px",
    async ({ width, view }) => {
      const originalViewport = { width: window.innerWidth, height: window.innerHeight };
      const response = Promise.withResolvers<void>();
      worker.use(
        http.get("/api/v2/bookings", async () => {
          await response.promise;
          return HttpResponse.json(collectionResponse([ownBooking]));
        }),
      );
      await page.viewport(width, 900);
      window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
      render(<CalendarPageStory />);
      try {
        await calendar.timeGridLayout.click();
        await calendar[view].click();
        await expect.element(calendar.timeGrid).toHaveAttribute("aria-busy", "true");
        await document.fonts.ready;
        const before = calendar.timeGrid.element().getBoundingClientRect();
        const toolbarBefore = calendar.toolbar.element().getBoundingClientRect();
        response.resolve();
        await expect.element(calendar.timeGrid).toHaveAttribute("aria-busy", "false");
        const after = calendar.timeGrid.element().getBoundingClientRect();
        const toolbarAfter = calendar.toolbar.element().getBoundingClientRect();
        expect(Math.abs(after.width - before.width)).toBeLessThanOrEqual(2);
        expect(Math.abs(after.left - before.left)).toBeLessThanOrEqual(2);
        expect(Math.abs(after.height - before.height)).toBeLessThanOrEqual(8);
        expect(Math.abs(toolbarAfter.top - toolbarBefore.top)).toBeLessThanOrEqual(2);
      } finally {
        response.resolve();
        await page.viewport(originalViewport.width, originalViewport.height);
      }
    },
  );

  test.each([390, 1440])("keeps authorized resource rows stable while events load at %s px", async (width) => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    const response = Promise.withResolvers<void>();
    worker.use(
      http.get("/api/v2/bookings", async () => {
        await response.promise;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    await page.viewport(width, 900);
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    try {
      await calendar.resources.click();
      await calendar.day.click();
      const resources = page.getByRole("region", { name: "Resources", exact: true });
      await expect.element(resources.getByText("IN123", { exact: true })).toBeVisible();
      await expect.element(resources).toHaveAttribute("aria-busy", "true");
      await document.fonts.ready;
      const before = resources.element().getBoundingClientRect();
      const toolbarBefore = calendar.toolbar.element().getBoundingClientRect();
      response.resolve();
      await expect.element(resources).toHaveAttribute("aria-busy", "false");
      const after = resources.element().getBoundingClientRect();
      expect(Math.abs(after.width - before.width)).toBeLessThanOrEqual(2);
      expect(Math.abs(after.left - before.left)).toBeLessThanOrEqual(2);
      expect(Math.abs(after.height - before.height)).toBeLessThanOrEqual(8);
      expect(Math.abs(calendar.toolbar.element().getBoundingClientRect().top - toolbarBefore.top)).toBeLessThanOrEqual(
        2,
      );
    } finally {
      response.resolve();
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("renders visible booking identities as user badges without leaking busy identities", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    const visibleIdentity = calendar.event("Confocal microscope").getByText("Ada Lovelace (ada)", { exact: true });
    await expect.element(visibleIdentity).toBeVisible();
    expect(visibleIdentity.element().closest('[data-slot="user-badge"]')).not.toBeNull();
    await expect
      .element(calendar.event("Busy").getByText("Ada Lovelace (ada)", { exact: true }))
      .not.toBeInTheDocument();

    await calendar.month.click();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect
      .poll(() => {
        const event = calendar.event("Confocal microscope").element();
        const badge = event.querySelector<HTMLElement>('[data-slot="user-badge"]');
        const title = Array.from(event.querySelectorAll<HTMLElement>("span")).find(
          (element) => element.textContent === "Confocal microscope",
        );
        const badgeBox = badge?.getBoundingClientRect();
        const titleBox = title?.getBoundingClientRect();
        return {
          badgeIsCompact: (badge?.getBoundingClientRect().height ?? Infinity) <= 20,
          contentFits: event.scrollHeight <= event.clientHeight + 1,
          identityOnSeparateLine: badgeBox !== undefined && titleBox !== undefined && badgeBox.top >= titleBox.bottom,
        };
      })
      .toEqual({ badgeIsCompact: true, contentFits: true, identityOnSeparateLine: true });
    await expectNoAxeViolations();
  });

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
    await expect.element(event.getByRole("button", { name: /Hide details/ })).toHaveAttribute("aria-expanded", "true");
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

  test("closes booking and busy cards when the page is clicked", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    await calendar.event("Confocal microscope").click();
    await expect
      .element(calendar.event("Confocal microscope").getByRole("button", { name: /Hide details/ }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Confocal microscope")).toHaveAttribute("aria-expanded", "false");

    await calendar.event("Busy").click();
    await expect
      .element(calendar.event("Busy").getByRole("button", { name: /Hide details/ }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Busy")).toHaveAttribute("aria-expanded", "false");
  });

  test("uses live booking events across every prototype layout and period", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await expect.element(calendar.heading).toBeVisible();
    await expect.element(calendar.resourceSchedule).toBeVisible();
    await expect.element(calendar.resources).toHaveAttribute("aria-pressed", "true");
    await expect.element(calendar.day).toHaveAttribute("aria-pressed", "true");
    await expect.element(page.getByText("Mass spectrometer", { exact: true })).toBeVisible();
    await calendar.week.click();
    await calendar.timeGridLayout.click();
    await expect.element(calendar.timeGrid).toBeVisible();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    const compactIdentity = calendar.event("Confocal microscope").getByText("Ada Lovelace (ada)", { exact: true });
    await expect.element(compactIdentity).toBeVisible();
    expect(compactIdentity.element().closest('[data-slot="user-badge"]')).not.toBeNull();
    await expect.element(calendar.event("Busy")).toBeVisible();
    await expect
      .element(calendar.event("Busy").getByText("Ada Lovelace (ada)", { exact: true }))
      .not.toBeInTheDocument();
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(3);
    expect(bookingPageRequests.calendarBookingRequests[0].searchParams.get("fields[bookings]")).toBe(
      calendarBookingFields,
    );
    expect(bookingPageRequests.calendarBookingRequests[0].searchParams.get("where")).toContain("state==CONFIRMED");

    await calendar.showEventDetails("Busy").click();
    await expect.element(calendar.viewItemDetails).not.toBeInTheDocument();
    await expect.element(calendar.editBooking).not.toBeInTheDocument();

    await calendar.searchFor("Grace");
    await expect.element(calendar.event("Confocal microscope")).not.toBeInTheDocument();
    await expect.element(calendar.event("Electron microscope")).toBeVisible();
    await page.getByRole("button", { name: "Clear search" }).click();

    await calendar.resources.click();
    await expect.element(calendar.resourceSchedule).toBeVisible();
    await calendar.day.click();
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(3);
    expect(
      bookingPageRequests.calendarBookingRequests.some((request) =>
        request.searchParams.get("where")?.includes("target=in=(IN123,IN124,IN125,IN126,IN127)"),
      ),
    ).toBe(true);
    await expect.poll(() => page.getByTestId("day-timeline-scroller").all().length).toBe(5);

    await calendar.mine.click();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(calendar.event("Electron microscope")).not.toBeInTheDocument();

    await calendar.agenda.click();
    await expect.element(calendar.bookingAgenda).toBeVisible();
    await calendar.month.click();
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(5);
    await expectNoAxeViolations();
  });
});
