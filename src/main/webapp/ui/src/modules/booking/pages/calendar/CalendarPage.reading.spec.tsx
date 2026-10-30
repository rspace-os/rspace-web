import "@/modules/common/styles/index.css";
import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { withViewport } from "@/__tests__/pageObjects/viewport";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import {
  bookingPageRequests,
  calendarBookingFields,
  resetBookingPageRequests,
} from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { customNewYorkBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { bookableItemFixtures } from "../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, ownBooking } from "../mocks/bookingCalendarFixtures";
import { noParentBooking } from "./__tests__/calendarTestHarness";
import { CalendarPageStory } from "./CalendarPage.story";
import { currentUser } from "./calendarFixtures";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

let history: RouterHistory;
let browserUrl: string;

beforeEach(() => {
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] });
  browserUrl = window.location.href;
  resetBookingPageRequests();
  registerHandlers();
});

afterEach(() => {
  cleanup();
  expect(window.location.href).toBe(browserUrl);
  vi.useRealTimers();
});

describe("Calendar page", () => {
  test("fills each resource row with its timeline and places locations below IDs", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
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
    history.replace("/booking/calendar?calendar-resources.q=Confocal");
    render(<CalendarPageStory history={history} preferences={customNewYorkBookingPreferences} />);
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
    await expect.poll(() => new URLSearchParams(history.location.search).has("date")).toBe(false);
    expect(new URLSearchParams(history.location.search).get("calendar-resources.q")).toBe("Confocal");
    await expect
      .element(calendar.resourceSchedule.getByRole("heading", { name: "Monday, August 17, 2026" }).first())
      .toBeVisible();
  });

  test("resets event filters and My calendar while preserving resource search", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-19T00:30:00Z"));
    history.replace("/booking/calendar?date=2026-08-17&calendar-resources.q=Confocal");
    render(<CalendarPageStory history={history} preferences={customNewYorkBookingPreferences} />);
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
    expect(new URLSearchParams(history.location.search).get("calendar-resources.q")).toBe("Confocal");
    await expect
      .poll(() =>
        bookingPageRequests.calendarBookingRequests
          .slice(requestsBeforeReset)
          .map((url) => url.searchParams.get("where")),
      )
      .toEqual(["start=lt=2026-08-19T04:00:00Z;end=gt=2026-08-18T04:00:00Z;state==CONFIRMED;target=in=(IN123)"]);
  });

  test.each([390, 1279, 1280, 1440])("keeps integrated Calendar controls reachable at %s px", async (width) => {
    await withViewport(width, async () => {
      render(<CalendarPageStory history={history} />);
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
    });
  });

  test.each([390, 1440])(
    "keeps the week resource grid through sequential resource/event loading at %s px",
    async (width) => {
      const resources = Promise.withResolvers<void>();
      const events = Promise.withResolvers<void>();
      worker.use(
        http.get("/api/v2/booking-catalogue", async () => {
          await resources.promise;
          return HttpResponse.json({
            items: bookableItemFixtures.slice(0, 4).map((item) => ({
              ...bookableItemOption(item),
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
      try {
        await withViewport(width, async () => {
          render(<CalendarPageStory history={history} />);
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
        });
      } finally {
        resources.resolve();
        events.resolve();
      }
    },
  );

  test.each([390, 1440].flatMap((width) => (["day", "week", "month"] as const).map((view) => ({ width, view }))))(
    "keeps the $view grid geometry while events load at $width px",
    async ({ width, view }) => {
      const response = Promise.withResolvers<void>();
      worker.use(
        http.get("/api/v2/bookings", async () => {
          await response.promise;
          return HttpResponse.json(collectionResponse([ownBooking]));
        }),
      );
      try {
        await withViewport(width, async () => {
          history.replace("/booking/calendar?date=2026-08-17");
          render(<CalendarPageStory history={history} />);
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
        });
      } finally {
        response.resolve();
      }
    },
  );

  test.each([390, 1440])("keeps authorized resource rows stable while events load at %s px", async (width) => {
    const response = Promise.withResolvers<void>();
    worker.use(
      http.get("/api/v2/bookings", async () => {
        await response.promise;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    try {
      await withViewport(width, async () => {
        history.replace("/booking/calendar?date=2026-08-17");
        render(<CalendarPageStory history={history} />);
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
        expect(
          Math.abs(calendar.toolbar.element().getBoundingClientRect().top - toolbarBefore.top),
        ).toBeLessThanOrEqual(2);
      });
    } finally {
      response.resolve();
    }
  });

  test("renders visible booking identities as user badges without leaking busy identities", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
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

  test("uses live booking events across every calendar layout and period", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);

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

    await calendar.search.fill("Grace");
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

  test("keeps a drag-created booking attached to its first resource and blocks a second drag", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
    await calendar.resources.click();
    await calendar.day.click();
    await expect.poll(() => page.getByTestId("day-timeline-canvas").all().length).toBe(5);
    const canvases = calendar.resourceCanvases;
    await calendar.dragResourceSelection(0);
    const dialog = page.getByRole("dialog", { name: "New Booking" });
    await expect.element(dialog).toBeVisible();
    const marker = page.getByTestId("compact-booking-draft-marker");
    await expect.element(marker).toBeVisible();
    await expect
      .poll(() => {
        const canvasBounds = canvases[0].element().getBoundingClientRect();
        const markerBounds = marker.element().getBoundingClientRect();
        return {
          topOffset: Math.round(markerBounds.top - canvasBounds.top),
          bottomOffset: Math.round(canvasBounds.bottom - markerBounds.bottom),
        };
      })
      .toEqual({ topOffset: 32, bottomOffset: 0 });
    expect(canvases[0].element().querySelector('[data-testid="compact-booking-draft-marker"]')).not.toBeNull();
    expect(canvases[1].element().querySelector('[data-testid="compact-booking-draft-marker"]')).toBeNull();
    await expect.element(dialog).not.toHaveAttribute("aria-modal", "true");
    await expect.element(dialog.getByText("Bookable item", { exact: true })).not.toBeInTheDocument();
    await expect
      .poll(() => canvases.every((canvas) => canvas.element().dataset.creationDisabled === "true"))
      .toBe(true);

    await calendar.dragResourceSelection(1);
    await expect.poll(() => page.getByRole("dialog", { name: "New Booking" }).all().length).toBe(1);
    expect(canvases[0].element().querySelector('[data-testid="compact-booking-draft-marker"]')).not.toBeNull();
    expect(canvases[1].element().querySelector('[data-testid="compact-booking-draft-marker"]')).toBeNull();
    await expect.element(dialog.getByText("Bookable item", { exact: true })).not.toBeInTheDocument();
    await expect.element(dialog.getByText("Electron microscope", { exact: true })).not.toBeInTheDocument();
  });

  test("ends pristine creation when leaving Calendar so resource dragging works after returning", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
    await calendar.newBooking.click();
    await expect.element(calendar.bookingDialog).toBeVisible();

    history.push("/booking/bookable-items/IN124");
    await expect.element(calendar.bookableItemDetailsHeading).toBeVisible();
    await expect.element(calendar.bookingDialog).not.toBeInTheDocument();

    history.push("/booking/calendar?date=2026-08-17");
    await expect.element(calendar.heading).toBeVisible();
    await calendar.resources.click();
    await calendar.day.click();
    await expect.poll(() => calendar.resourceCanvases.length).toBe(5);

    await calendar.dragResourceSelection(0);
    await expect.element(calendar.bookingDialog.getByText("Bookable item", { exact: true })).not.toBeInTheDocument();
  });

  test("starts a resource booking from the keyboard with a proposed free hour", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
    await calendar.resources.click();
    await calendar.day.click();
    await expect.poll(() => calendar.resourceCanvases.length).toBe(5);

    const addForMassSpectrometer = page.getByRole("button", { name: "Add booking for Mass spectrometer" });
    addForMassSpectrometer.element().focus();
    await expect.element(addForMassSpectrometer).toHaveFocus();
    await userEvent.keyboard("{Enter}");

    const dialog = page.getByRole("dialog", { name: "New Booking" });
    await expect.element(dialog.getByText("Bookable item", { exact: true })).not.toBeInTheDocument();
    await expect.element(dialog.getByLabelText("Start time")).toHaveValue("08:00");
    await expect.element(dialog.getByLabelText("End time")).toHaveValue("09:00");
  });

  test("disables resource-row creation when no free window exists", async () => {
    worker.use(
      http.get("/api/v2/bookings", ({ request }) => {
        if (new URL(request.url).searchParams.get("fields[bookings]") !== calendarBookingFields) return undefined;
        return HttpResponse.json(
          collectionResponse([
            {
              ...noParentBooking,
              start: "2026-08-16T00:00:00Z",
              end: "2026-08-19T00:00:00Z",
            },
          ]),
        );
      }),
    );
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
    await calendar.resources.click();
    await calendar.day.click();
    await expect.poll(() => calendar.resourceCanvases.length).toBe(5);

    await expect.element(page.getByRole("button", { name: "Add booking for Mass spectrometer" })).toBeDisabled();
  });

  test("creates a booking through the targetless compact form and restores trigger focus", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-01T00:00:00Z"));
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
    const trigger = page.getByRole("button", { name: "New Booking" });
    const dialog = await calendar.openTargetlessBookingDialog();
    expect(getComputedStyle(dialog.element()).borderRadius).toBe("8px");
    const dialogBox = dialog.element().getBoundingClientRect();
    const headingBox = dialog.getByRole("heading", { name: "New Booking" }).element().getBoundingClientRect();
    expect({ left: headingBox.left - dialogBox.left, top: headingBox.top - dialogBox.top }).toEqual({
      left: 17,
      top: 13,
    });
    expect(getComputedStyle(dialog.getByRole("button", { name: "Cancel" }).element()).borderRadius).toBe("0px");
    await dialog.getByLabelText("Start time").fill("09:00");
    await dialog.getByLabelText("End time").fill("10:00");
    await dialog.getByRole("textbox", { name: "Purpose" }).fill("Live-stack-shaped booking");
    await dialog.getByRole("button", { name: "Book", exact: true }).click();

    await expect.poll(() => bookingPageRequests.createdPayloads.length).toBe(1);
    expect(bookingPageRequests.createdPayloads[0]).toMatchObject({
      target: { relationTo: "instruments", value: 123 },
      kind: "BOOKING",
      purpose: "Live-stack-shaped booking",
    });
    await expect.element(dialog).not.toBeInTheDocument();
    await expect.poll(() => document.activeElement).toBe(trigger.element());
  });

  test("opens the full booking form from compact More options", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} preferences={customNewYorkBookingPreferences} />);
    const dialog = await calendar.openTargetlessBookingDialog();
    await expect.element(dialog.getByText("Open: 08:00 - 17:00 (Europe/Berlin)")).toBeVisible();
    expect(dialog.element().querySelectorAll('input[type="date"]')).toHaveLength(1);
    const compactDate = dialog.getByLabelText("Date");
    const compactStartTime = dialog.getByLabelText("Start time");
    const compactEndTime = dialog.getByLabelText("End time");
    expect(compactDate.element().getBoundingClientRect().top).toBeLessThan(
      compactStartTime.element().getBoundingClientRect().top,
    );
    expect(compactStartTime.element().getBoundingClientRect().top).toBe(
      compactEndTime.element().getBoundingClientRect().top,
    );
    await compactDate.fill("2026-08-18");
    await compactStartTime.fill("09:15");
    await compactEndTime.fill("10:45");
    await dialog.getByRole("textbox", { name: "Purpose" }).fill("Carry this draft into the full form");

    await dialog.getByRole("button", { name: "More options" }).click();
    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/add");
    await expect.poll(() => new URLSearchParams(history.location.search).get("target")).toBe("IN123");
    await expect.element(page.getByRole("heading", { name: "Add Booking" })).toBeVisible();
    await expect.element(page.getByRole("button", { name: "Booking rules" })).toBeVisible();
    const fullStart = page.getByRole("group", { name: "Start" });
    const fullEnd = page.getByRole("group", { name: "End" });
    await expect.element(fullStart.getByLabelText("Date")).toHaveValue("2026-08-18");
    await expect.element(fullStart.getByLabelText("Time")).toHaveValue("09:15");
    await expect.element(fullEnd.getByLabelText("Date")).toHaveValue("2026-08-18");
    await expect.element(fullEnd.getByLabelText("Time")).toHaveValue("10:45");
    await expect
      .element(page.getByRole("textbox", { name: "Purpose" }))
      .toHaveValue("Carry this draft into the full form");
  });

  test("keeps the popover open after outside press and confirms dirty history navigation", async () => {
    history.replace("/booking/calendar?date=2026-08-16");
    history.push("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
    const trigger = page.getByRole("button", { name: "New Booking" });
    await expect.element(trigger).toBeVisible();
    history.back();
    await expect.poll(() => history.location.search).toContain("date=2026-08-16");
    history.forward();
    await expect.poll(() => history.location.search).toContain("date=2026-08-17");

    await trigger.click();
    const cleanDialog = page.getByRole("dialog", { name: "New Booking" });
    await expect.element(cleanDialog).toBeVisible();
    await calendar.heading.click();
    await expect.element(cleanDialog).toBeVisible();
    await cleanDialog.getByRole("button", { name: "Cancel" }).click();
    await expect.element(cleanDialog).not.toBeInTheDocument();
    await expect.poll(() => document.activeElement).toBe(trigger.element());

    const dirtyDialog = await calendar.openTargetlessBookingDialog();
    await dirtyDialog.getByRole("textbox", { name: "Purpose" }).fill("Keep this draft");
    // TanStack memory history runs blockers for push/replace, not back/forward.
    history.push("/booking/calendar?date=2026-08-16");
    const confirmation = page.getByRole("alertdialog", { name: "Discard this event?" });
    await expect.element(confirmation).toBeVisible();
    await confirmation.getByRole("button", { name: "Keep editing" }).click();
    await expect.element(dirtyDialog.getByRole("textbox", { name: "Purpose" })).toHaveValue("Keep this draft");
    await expect.poll(() => history.location.search).toContain("date=2026-08-17");

    history.push("/booking/calendar?date=2026-08-16");
    await expect.element(confirmation).toBeVisible();
    await confirmation.getByRole("button", { name: "Discard changes" }).click();
    await expect.element(dirtyDialog).not.toBeInTheDocument();
    await expect.poll(() => history.location.search).toContain("date=2026-08-16");
  });

  test("preserves a compact draft across recoverable server conflicts", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-01T00:00:00Z"));
    let code = "errors.api.v2.booking.overlap";
    worker.use(
      http.post("/api/v2/bookings", () =>
        HttpResponse.json({ status: 409, code, detail: "private server detail" }, { status: 409 }),
      ),
    );
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} />);
    const dialog = await calendar.openTargetlessBookingDialog();
    const startTime = dialog.getByLabelText("Start time");
    const endTime = dialog.getByLabelText("End time");
    const submit = dialog.getByRole("button", { name: "Book", exact: true });
    await startTime.fill("09:00");
    await endTime.fill("10:00");
    const purpose = dialog.getByRole("textbox", { name: "Purpose" });
    await purpose.fill("Preserve this draft");

    await submit.click();
    await expect.element(dialog.getByText("This period overlaps another booking.")).toBeVisible();
    await expect.element(purpose).toHaveValue("Preserve this draft");
    await expect.element(submit).toBeDisabled();

    await dialog.getByLabelText("End time").fill("10:30");
    code = "errors.api.v2.booking.concurrentModification";
    await submit.click();
    await expect
      .element(
        dialog.getByText("This event changed while you were editing it. Review the latest details and try again."),
      )
      .toBeVisible();
    await expect.element(purpose).toHaveValue("Preserve this draft");

    code = "errors.api.v2.booking.target.unavailable";
    await submit.click();
    await expect.element(dialog.getByText("This bookable item is unavailable.")).toBeVisible();
    await expect.element(purpose).toHaveValue("Preserve this draft");
  });

  test("does not expose maintenance creation to a run-as sysadmin", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(
      <CalendarPageStory
        history={history}
        user={{
          ...currentUser,
          hasSysAdminRole: true,
          session: { ...currentUser.session, operatedAs: true },
        }}
      />,
    );

    await expect.element(page.getByRole("button", { name: "New Booking" })).toBeVisible();
    await expect.element(page.getByRole("button", { name: "More event creation options" })).not.toBeInTheDocument();
  });

  test("confirms dirty dismissal and keeps maintenance type immutable for a direct sysadmin", async () => {
    history.replace("/booking/calendar?date=2026-08-17");
    render(
      <CalendarPageStory
        history={history}
        user={{
          ...currentUser,
          hasSysAdminRole: true,
          session: { ...currentUser.session, operatedAs: false },
        }}
      />,
    );
    const newBooking = page.getByRole("button", { name: "New Booking" });
    const moreCreationOptions = page.getByRole("button", { name: "More event creation options" });
    await expect
      .poll(() => {
        const bookingElement = newBooking.element();
        const moreElement = moreCreationOptions.element();
        const bookingIcon = bookingElement.querySelector("svg")?.getBoundingClientRect();
        const moreIcon = moreElement.querySelector("svg")?.getBoundingClientRect();
        const bookingStyle = getComputedStyle(bookingElement);
        const moreStyle = getComputedStyle(moreElement);
        return {
          bookingRadii: [
            bookingStyle.borderTopLeftRadius,
            bookingStyle.borderTopRightRadius,
            bookingStyle.borderBottomRightRadius,
            bookingStyle.borderBottomLeftRadius,
          ],
          moreOuterRadii: [moreStyle.borderTopRightRadius, moreStyle.borderBottomRightRadius],
          iconSizes: [
            bookingIcon ? [bookingIcon.width, bookingIcon.height] : null,
            moreIcon ? [moreIcon.width, moreIcon.height] : null,
          ],
        };
      })
      .toEqual({
        bookingRadii: ["4px", "0px", "0px", "4px"],
        moreOuterRadii: ["4px", "4px"],
        iconSizes: [
          [14, 14],
          [14, 14],
        ],
      });

    await moreCreationOptions.click();
    const newMaintenance = page.getByRole("menuitem", { name: "New Maintenance Event" });
    await expect
      .poll(() => {
        const icon = newMaintenance.element().querySelector("svg")?.getBoundingClientRect();
        return icon ? [icon.width, icon.height] : null;
      })
      .toEqual([14, 14]);
    await newMaintenance.click();
    const dialog = page.getByRole("dialog", { name: "New Maintenance Event" });
    await expect.element(dialog).toBeVisible();
    await expect.element(dialog.getByRole("radio")).not.toBeInTheDocument();
    await dialog.getByRole("textbox", { name: "Notes" }).fill("Laser service");
    await userEvent.keyboard("{Escape}");

    const confirmation = page.getByRole("alertdialog", { name: "Discard this event?" });
    await expect.element(confirmation).toBeVisible();
    await confirmation.getByRole("button", { name: "Keep editing" }).click();
    await expect.element(dialog.getByRole("textbox", { name: "Notes" })).toHaveValue("Laser service");
    await dialog.getByRole("button", { name: "Cancel" }).click();
    await confirmation.getByRole("button", { name: "Discard changes" }).click();
    await expect.element(dialog).not.toBeInTheDocument();
  });

  test("keeps the compact form readable in a narrow viewport", async () => {
    await withViewport(
      390,
      async () => {
        history.replace("/booking/calendar?date=2026-08-17");
        render(<CalendarPageStory history={history} />);
        await page.getByRole("button", { name: "New Booking" }).click();
        const dialog = page.getByRole("dialog", { name: "New Booking" });
        await expect.element(dialog).toBeVisible();
        await expect
          .poll(() => {
            const element = dialog.element();
            const bounds = element.getBoundingClientRect();
            return {
              left: Math.round(bounds.left),
              right: Math.round(bounds.right),
              viewport: window.innerWidth,
              overflow: element.scrollWidth - element.clientWidth,
            };
          })
          .toEqual({ left: 16, right: 374, viewport: 390, overflow: 0 });
        await expect.element(dialog.getByRole("textbox", { name: "Purpose" })).toBeVisible();
        await expect.element(dialog.getByRole("button", { name: "Book", exact: true })).toBeVisible();
        await expect.element(dialog.getByRole("button", { name: "More actions" })).not.toBeInTheDocument();
        await dialog.getByRole("button", { name: "Cancel" }).click();
      },
      667,
    );
  });
});
