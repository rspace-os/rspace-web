import { createBrowserHistory, createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import {
  bookingPageRequests,
  bookingPagesHandlers,
  calendarBookingFields,
  resetBookingPageRequests,
} from "@/modules/booking/pages/mocks/bookingPagesMocks";
import {
  customNewYorkBookingPreferences,
  institutionBookingPreferences,
} from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { bookableItemFixtures } from "../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, noParentBooking, ownBooking } from "./__tests__/calendarTestHarness";
import { CalendarPageStory } from "./CalendarPage.story";
import { currentUser } from "./calendarFixtures";
import { CalendarPage as CalendarPageObject } from "./pageObjects/CalendarPage";

const calendar = new CalendarPageObject();

function registerHandlers(): void {
  worker.use(...bookingPagesHandlers());
}

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
  test("preserves item and event scopes across Calendar layouts", async () => {
    const params = new URLSearchParams({
      date: "2026-08-17",
      "calendar-resources.where": "target==IN123",
      "calendar-events.where": 'purpose=="Cell imaging"',
    });
    history.replace(`/booking/calendar?${params}`);
    render(<CalendarPageStory history={history} />);
    await expect.element(calendar.resourceSchedule).toBeVisible();
    for (const layout of ["Time grid", "Agenda", "By Item"] as const) {
      await calendar.chooseView(layout);
      await expect.element(calendar.viewMenu).toHaveAccessibleName(`View: ${layout} · Day`);
    }
    await expect
      .poll(() =>
        bookingPageRequests.calendarBookingRequests.some(
          (url) =>
            url.searchParams.get("where")?.includes("purpose==") &&
            url.searchParams.get("where")?.includes("target==IN123"),
        ),
      )
      .toBe(true);
    expect(bookingPageRequests.collectionQueries.some((query) => query.includes("eventWhere=purpose=="))).toBe(true);
    expect(new URLSearchParams(history.location.search).get("calendar-resources.where")).toBe("target==IN123");
    await expect.element(page.getByRole("button", { name: "Bookable items, 1 applied" })).not.toBeInTheDocument();
    await expect.element(calendar.filters).toHaveAccessibleName("Filters, 1 applied");
  });

  test("filters Calendar events by target fields and target custom fields", async () => {
    render(<CalendarPageStory history={history} />);
    await calendar.openFilterPanel();

    await page.getByRole("button", { name: "Add filter", exact: true }).click();
    const targetNameField = page.getByRole("combobox", { name: "Field for filter 1" });
    await targetNameField.fill("Instrument name");
    await page.getByRole("option", { name: /Instrument name/ }).click();
    const targetNameValue = page.getByRole("textbox", { name: "Value for filter 1" });
    await targetNameValue.fill("Confocal microscope");

    await page.getByRole("button", { name: "Add filter", exact: true }).click();
    const customFieldGroup = page.getByRole("combobox", { name: "Field for filter 2" });
    await customFieldGroup.click();
    const bookableItemFieldGroup = page.getByRole("group", { name: "Bookable item" });
    await bookableItemFieldGroup.getByRole("option", { name: /Custom field/ }).click();
    const customFieldSearch = page.getByRole("combobox", {
      name: "Search Bookable item custom fields for filter 2",
    });
    await customFieldSearch.fill("Hazard class");
    await page.getByRole("option", { name: /Hazard class/ }).click();
    await page.getByRole("textbox", { name: "Value for filter 2" }).fill("BSL-2");
    await page.getByRole("button", { name: "Apply filters" }).click();

    const savedFilter = () => new URLSearchParams(history.location.search).get("calendar-events.where") ?? "";
    await expect.poll(savedFilter).toContain("target.customFields.SF152==BSL-2");
    expect(savedFilter()).toContain("target.name==");
    await expect
      .poll(() =>
        bookingPageRequests.calendarBookingRequests.some((url) => {
          const where = url.searchParams.get("where") ?? "";
          return where.includes("target.name==") && where.includes("target.customFields.SF152==BSL-2");
        }),
      )
      .toBe(true);
    await expect
      .poll(() =>
        bookingPageRequests.collectionQueries.some((query) => {
          const eventWhere = new URLSearchParams(query).get("eventWhere") ?? "";
          return eventWhere.includes("target.name==") && eventWhere.includes("target.customFields.SF152==BSL-2");
        }),
      )
      .toBe(true);
  });

  test("focuses the resource list on the target from the route", async () => {
    history.replace("/booking/calendar?date=2026-08-17&target=IN124");
    render(<CalendarPageStory history={history} />);

    await expect.element(calendar.resourceSchedule.getByText("Electron microscope", { exact: true })).toBeVisible();
    await expect.element(calendar.resourceSchedule.getByText("IN124", { exact: true })).toBeVisible();
    await expect.element(calendar.resourceSchedule.getByText("IN123", { exact: true })).not.toBeInTheDocument();
    await expect
      .poll(() =>
        bookingPageRequests.collectionQueries.some(
          (query) => new URLSearchParams(query).get("where") === "target==IN124",
        ),
      )
      .toBe(true);
    expect(new URLSearchParams(history.location.search).get("target")).toBe("IN124");

    await expect.element(page.getByText("Bookable item: Electron microscope (IN124)", { exact: true })).toBeVisible();
    await calendar.removeTargetFilter.click();
    await expect.poll(() => new URLSearchParams(history.location.search).has("target")).toBe(false);
    expect(new URLSearchParams(history.location.search).get("date")).toBe("2026-08-17");
    await expect.element(calendar.removeTargetFilter).not.toBeInTheDocument();
    await expect.element(calendar.resourceSchedule.getByText("IN123", { exact: true })).toBeVisible();
  });

  test("hides cached events and blocks requests for an unavailable saved item field", async () => {
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("Agenda");
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    const count = bookingPageRequests.calendarBookingRequests.length;
    worker.use(
      http.get("/api/v2/instruments/fields/customFields", () => HttpResponse.json({ fields: [], hasMore: false })),
    );
    history.push("/booking/calendar?date=2026-08-17&calendar-resources.where=target.customFields.SF999%3D%3Dx");
    await expect.element(page.getByRole("alert")).toHaveTextContent("target.customFields.SF999==x");
    await expect.element(calendar.event("Confocal microscope")).not.toBeInTheDocument();
    expect(bookingPageRequests.calendarBookingRequests).toHaveLength(count);
    await page.getByRole("button", { name: "Reset saved view" }).click();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
  });

  test("keeps controls available to narrow a Calendar capacity failure", async () => {
    worker.use(
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        const q = new URL(request.url).searchParams.get("q");
        return HttpResponse.json(
          collectionResponse(q ? [ownBooking] : [], { totalDocs: q ? 1 : 1001, totalPages: q ? 1 : 11 }),
        );
      }),
    );
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("Agenda");
    await expect.element(page.getByRole("alert")).toHaveTextContent("Booking events are unavailable.");
    await calendar.search.fill("Cell imaging");
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(page.getByRole("alert")).not.toBeInTheDocument();
  });

  test("uses hidden bookings when proposing a free resource slot", async () => {
    history.replace("/booking/calendar?date=2026-08-17&calendar-events.where=purpose%3D%3D%22Cell%20imaging%22");
    const hidden = {
      ...ownBooking,
      id: 999,
      purpose: "Hidden booking",
      start: "2026-08-17T10:00:00Z",
      end: "2026-08-17T11:00:00Z",
    };
    worker.use(
      http.get("/api/v2/booking-calendar/events", ({ request }) =>
        HttpResponse.json(
          collectionResponse(
            new URL(request.url).searchParams.get("where")?.includes("purpose==") ? [ownBooking] : [hidden, ownBooking],
          ),
        ),
      ),
    );
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);
    const add = page.getByRole("button", { name: "Add booking for Confocal microscope", exact: true });
    await expect.element(add).toBeEnabled();
    await add.click();
    await expect.element(calendar.bookingDialog.getByLabelText("Start time")).toHaveValue("11:00");
  });

  test("fills each resource row with its timeline and places locations below IDs", async () => {
    render(<CalendarPageStory history={history} />);
    await expect
      .element(page.getByRole("region", { name: "By Item", exact: true }))
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

  test("places icon-only configuration viewing below the day-row booking action", async () => {
    render(<CalendarPageStory history={history} />);

    const view = calendar.resourceSchedule.getByRole("link", { name: "View configuration", exact: true }).first();
    const add = calendar.resourceSchedule.getByRole("button", {
      name: "Add booking for Confocal microscope",
      exact: true,
    });
    await expect.element(view).toBeVisible();
    expect(view.element().getAttribute("href")).toBe("/booking/bookable-items/IN123/details");
    expect(view.element().textContent).toBe("");
    const viewBox = view.element().getBoundingClientRect();
    const addBox = add.element().getBoundingClientRect();
    const viewStyle = getComputedStyle(view.element());
    const addStyle = getComputedStyle(add.element());
    const actionContainer = view.element().parentElement;
    if (!actionContainer) throw new Error("View action must have a row action container");
    expect(getComputedStyle(actionContainer).position).toBe("sticky");
    expect(getComputedStyle(actionContainer).right).toBe("0px");
    const instrumentLink = calendar.resourceSchedule.getByRole("link", { name: /Open inventory record/ }).first();
    const instrumentHeader = instrumentLink.element().closest("header");
    if (!instrumentHeader) throw new Error("Instrument link must have a row header");
    expect(getComputedStyle(instrumentHeader).position).toBe("sticky");
    expect(getComputedStyle(instrumentHeader).left).toBe("0px");
    expect(viewBox.top).toBeGreaterThanOrEqual(addBox.bottom);
    expect(Math.abs(viewBox.x + viewBox.width / 2 - (addBox.x + addBox.width / 2))).toBeLessThanOrEqual(1);
    expect(viewStyle.border).toBe(addStyle.border);
    expect(viewStyle.backgroundColor).toBe(addStyle.backgroundColor);

    await view.hover();
    await expect.element(page.getByRole("tooltip", { name: "View configuration" })).toBeVisible();
  });

  test("keeps icon-only configuration viewing available in the week resource card", async () => {
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("Week");

    const view = calendar.resourceSchedule.getByRole("link", { name: "View configuration", exact: true }).first();
    await expect.element(view).toBeVisible();
    expect(view.element().getAttribute("href")).toBe("/booking/bookable-items/IN123/details");
    expect(view.element().textContent).toBe("");
    const card = calendar.resourceSchedule.element().querySelector<HTMLElement>("[data-inventory-item]");
    if (!card) throw new Error("Expected a resource inventory card");
    expect(view.element().getBoundingClientRect().top).toBeGreaterThanOrEqual(card.getBoundingClientRect().bottom);

    await view.hover();
    await expect.element(page.getByRole("tooltip", { name: "View configuration" })).toBeVisible();
  });

  test.each(["date", "layout", "period"])("resets an isolated %s change to Calendar defaults", async (control) => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-18T00:30:00Z"));
    history.replace("/booking/calendar");
    render(<CalendarPageStory history={history} preferences={customNewYorkBookingPreferences} />);
    await expect.element(calendar.heading).toBeVisible();
    await expect.element(calendar.timeZone).not.toBeInTheDocument();
    await expect.element(calendar.reset).not.toBeInTheDocument();

    if (control === "date") await calendar.next.click();
    if (control === "layout") await calendar.chooseView("Agenda");
    if (control === "period") await calendar.chooseView("Week");
    await expect.element(calendar.reset).toBeVisible();
    await calendar.reset.click();

    await expect.element(calendar.viewMenu).toHaveAccessibleName("View: By Item · Day");
    await expect.element(calendar.filters).toHaveAccessibleName("Filters, none applied");
    await expect.element(calendar.reset).not.toBeInTheDocument();
    await expect.poll(() => new URLSearchParams(history.location.search).has("date")).toBe(false);
    expect(new URLSearchParams(history.location.search).has("calendar-resources.q")).toBe(false);
    await expect
      .element(calendar.resourceSchedule.getByRole("heading", { name: "Monday, August 17, 2026" }).first())
      .toBeVisible();
  });

  test("resets event filters, shared search and My Bookings", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-19T00:30:00Z"));
    history.replace("/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory history={history} preferences={customNewYorkBookingPreferences} />);
    await calendar.search.fill("no matching event");
    await expect
      .poll(() => new URLSearchParams(history.location.search).get("calendar-resources.q"))
      .toBe("no matching event");
    await calendar.toggleQuickFilter("My Bookings");
    await calendar.openFilterPanel();
    await page.getByRole("button", { name: "Add filter", exact: true }).click();
    await page.getByRole("combobox", { name: "Field for filter 1" }).fill("Purpose");
    await page.getByRole("option", { name: "Purpose", exact: true }).click();
    await page.getByRole("textbox", { name: "Value for filter 1" }).fill("no matching purpose");
    await page.getByRole("button", { name: "Apply filters" }).click();
    await expect.element(calendar.quickFilter("My Bookings")).toHaveAttribute("aria-pressed", "true");
    await expect.element(calendar.filters).toHaveAccessibleName("Filters, 1 applied");
    await calendar.chooseView("Week");
    await expect.element(calendar.viewMenu).toHaveAccessibleName("View: By Item · Week");
    const requestsBeforeReset = bookingPageRequests.calendarBookingRequests.length;
    await calendar.reset.click();
    await expect.element(calendar.search).toHaveValue("");
    await expect.element(calendar.removeMine).not.toBeInTheDocument();
    await expect.element(calendar.quickFilter("My Bookings")).toHaveAttribute("aria-pressed", "false");
    await expect.element(calendar.viewMenu).toHaveAccessibleName("View: By Item · Day");
    await expect.element(calendar.filters).toHaveAccessibleName("Filters, none applied");
    await expect.element(calendar.reset).not.toBeInTheDocument();
    expect(new URLSearchParams(history.location.search).has("calendar-resources.q")).toBe(false);
    await expect
      .poll(() =>
        bookingPageRequests.calendarBookingRequests
          .slice(requestsBeforeReset)
          .map((url) => url.searchParams.get("where")),
      )
      .toEqual(["start=lt=2026-08-19T04:00:00Z;end=gt=2026-08-18T04:00:00Z;target=in=(IN123,IN124,IN125,IN126,IN127)"]);
  });

  test.each([390, 1279, 1280, 1440])("keeps integrated Calendar controls reachable at %s px", async (width) => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(width, 900);
    render(<CalendarPageStory history={history} />);
    try {
      await expect.element(calendar.toolbar).toBeVisible();
      await document.fonts.ready;
      await expect.element(calendar.timeZone).not.toBeInTheDocument();
      for (const control of [calendar.dateControls, calendar.viewMenu, calendar.search, calendar.filters]) {
        expect(calendar.toolbar.element()).toContainElement(control.element());
      }
      for (const control of calendar.toolbar.getByRole("button").all()) {
        const bounds = control.element().getBoundingClientRect();
        expect(bounds.left).toBeGreaterThanOrEqual(0);
        expect(bounds.right).toBeLessThanOrEqual(width);
        expect(bounds.height).toBe(36);
      }
      expect(
        calendar.toolbar
          .getByRole("group", { name: "Calendar period navigation", exact: true })
          .element()
          .getBoundingClientRect().height,
      ).toBe(36);
      await calendar.chooseView("Time grid");
      await calendar.chooseView("Month");
      await expect.element(calendar.viewMenu).toHaveAccessibleName("View: Time grid · Month");
      await calendar.chooseView("By Item");
      await expect.element(calendar.viewMenu).toHaveAccessibleName("View: By Item · Week");
      // The menu stays open between choices, and explains the Month it cannot offer here.
      await calendar.viewMenu.click();
      await expect.element(calendar.viewOption("Month")).toHaveAttribute("aria-disabled", "true");
      await expect
        .element(calendar.viewOption("Month"))
        .toHaveAccessibleDescription(
          "Month isn't available in the By Item view. Use Time grid or Agenda for a month overview.",
        );
      await calendar.viewOption("Agenda").click();
      await expect.element(calendar.viewOption("Agenda")).toHaveAttribute("aria-checked", "true");
      await expect.element(calendar.viewOption("Month")).not.toHaveAttribute("aria-disabled", "true");
      await userEvent.keyboard("{Escape}");
      await expect.element(calendar.viewMenu).toHaveAccessibleName("View: Agenda · Week");
      await expect.element(calendar.viewMenu).toHaveFocus();
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
        http.get("/api/v2/booking-catalogue/calendar", async () => {
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
        http.get("/api/v2/booking-calendar/events", async () => {
          await events.promise;
          return HttpResponse.json(collectionResponse([]));
        }),
      );
      await page.viewport(width, 900);
      render(<CalendarPageStory history={history} />);
      try {
        await calendar.chooseView("Week");
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
        const loaded = page.getByRole("region", { name: "By Item", exact: true });
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
        http.get("/api/v2/booking-calendar/events", async () => {
          await response.promise;
          return HttpResponse.json(collectionResponse([ownBooking]));
        }),
      );
      await page.viewport(width, 900);
      render(<CalendarPageStory history={history} />);
      try {
        await calendar.chooseView("Time grid");
        await calendar.chooseView(({ day: "Day", week: "Week", month: "Month" } as const)[view]);
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
      http.get("/api/v2/booking-calendar/events", async () => {
        await response.promise;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    await page.viewport(width, 900);
    render(<CalendarPageStory history={history} />);
    try {
      await calendar.chooseView("By Item");
      await calendar.chooseView("Day");
      const resources = page.getByRole("region", { name: "By Item", exact: true });
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
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("Time grid");
    await calendar.chooseView("Week");

    const visibleIdentity = calendar.event("Confocal microscope").getByText("Ada Lovelace (ada)", { exact: true });
    await expect.element(visibleIdentity).toBeVisible();
    expect(visibleIdentity.element().closest('[data-slot="user-badge"]')).not.toBeNull();
    await expect
      .element(calendar.event("Busy").getByText("Ada Lovelace (ada)", { exact: true }))
      .not.toBeInTheDocument();

    await calendar.chooseView("Month");
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
    await expect.element(event.getByRole("button", { name: /Hide details/ })).toHaveAttribute("aria-expanded", "true");
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
    const cards = monday.getByRole("button", { name: /^Show details for Confocal microscope/ });
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
        return HttpResponse.json(serverBooking);
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
    await expect.element(card.getByText("Booking #42")).toBeVisible();
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
    end.element().focus();
    await userEvent.keyboard("{ArrowLeft}");
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
    await calendar.chooseView("Time grid");
    await calendar.chooseView("Week");

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
    render(<CalendarPageStory history={history} />);

    await expect.element(calendar.heading).toBeVisible();
    await expect.element(calendar.resourceSchedule).toBeVisible();
    await expect.element(calendar.viewMenu).toHaveAccessibleName("View: By Item · Day");
    await expect.element(page.getByText("Mass spectrometer", { exact: true })).toBeVisible();
    await calendar.chooseView("Week");
    await calendar.chooseView("Time grid");
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
    expect(bookingPageRequests.calendarBookingRequests[0].searchParams.get("where")).not.toContain("state==CONFIRMED");

    await calendar.showEventDetails("Busy").click();
    await expect.element(calendar.viewItemDetails).not.toBeInTheDocument();
    await expect.element(calendar.editBooking).not.toBeInTheDocument();

    await calendar.searchFor("Cryo-grid");
    await expect.element(calendar.event("Confocal microscope")).not.toBeInTheDocument();
    await expect.element(calendar.event("Electron microscope")).toBeVisible();
    await page.getByRole("button", { name: "Clear search" }).click();

    await calendar.chooseView("By Item");
    await expect.element(calendar.resourceSchedule).toBeVisible();
    await calendar.chooseView("Day");
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(4);
    expect(
      bookingPageRequests.calendarBookingRequests.some((request) =>
        request.searchParams.get("where")?.includes("target=in=(IN123,IN124,IN125,IN126,IN127)"),
      ),
    ).toBe(true);
    await expect.poll(() => page.getByTestId("day-timeline-scroller").all().length).toBe(5);

    await calendar.toggleQuickFilter("My Bookings");
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(calendar.event("Electron microscope")).not.toBeInTheDocument();

    await calendar.chooseView("Agenda");
    await expect.element(calendar.bookingAgenda).toBeVisible();
    await calendar.chooseView("Month");
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(7);
    await expectNoAxeViolations();
  });

  test("keeps a drag-created booking attached to its first resource and blocks a second drag", async () => {
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("By Item");
    await calendar.chooseView("Day");
    await expect.poll(() => page.getByTestId("day-timeline-canvas").all().length).toBe(5);
    const canvases = calendar.resourceCanvases;
    await calendar.dragResourceSelection(0);
    const dialog = page.getByRole("dialog", { name: "New Booking" });
    await expect.element(dialog).toBeVisible();
    const marker = page.getByTestId("compact-booking-draft-marker");
    await expect.element(marker).toBeVisible();
    await expect
      .poll(() => {
        const dialogBounds = dialog.element().getBoundingClientRect();
        const markerBounds = marker.element().getBoundingClientRect();
        const left = Math.max(dialogBounds.left, markerBounds.left);
        const right = Math.min(dialogBounds.right, markerBounds.right);
        const top = Math.max(dialogBounds.top, markerBounds.top);
        const bottom = Math.min(dialogBounds.bottom, markerBounds.bottom);
        if (left >= right || top >= bottom) return "no-overlap";
        const topmost = document.elementFromPoint((left + right) / 2, (top + bottom) / 2);
        if (topmost && dialog.element().contains(topmost)) return "dialog";
        if (topmost && marker.element().contains(topmost)) return "marker";
        return "other";
      })
      .toBe("dialog");
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
    const startTime = dialog.getByLabelText("Start time");
    const endTime = dialog.getByLabelText("End time");
    const initialStart = (startTime.element() as HTMLInputElement).value;
    const initialEnd = (endTime.element() as HTMLInputElement).value;
    const moveDraft = marker.getByRole("button", { name: "Move booking time" });
    moveDraft.element().focus();
    await userEvent.keyboard("{ArrowRight}");
    await expect.poll(() => (startTime.element() as HTMLInputElement).value).not.toBe(initialStart);
    expect((endTime.element() as HTMLInputElement).value).not.toBe(initialEnd);
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

  test("creates a booking from a Time grid drag when one bookable item is in scope", async () => {
    history.replace("/booking/calendar?date=2026-08-17&target=IN123&layout=time-grid");
    render(<CalendarPageStory history={history} />);
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(calendar.timeGrid).toHaveAttribute("aria-busy", "false");
    await expect.element(calendar.timeGridCanvas).not.toHaveAttribute("data-creation-disabled");

    await calendar.dragTimeGridRange(12 * 60, 13 * 60 + 30);

    const dialog = calendar.bookingDialog;
    await expect.element(dialog).toBeVisible();
    // The item comes from the Time grid's scope, so the form does not ask for one.
    await expect.element(dialog.getByText("Bookable item", { exact: true })).not.toBeInTheDocument();
    await expect.element(dialog.getByLabelText("Start time")).toHaveValue("12:00");
    await expect.element(dialog.getByLabelText("End time")).toHaveValue("13:30");
    // The draft is drawn on the grid it came from, and the grid takes no second drag while it is open.
    await expect.element(calendar.timeGridCanvas.getByTestId("compact-booking-draft-marker")).toBeVisible();
    await expect.element(calendar.timeGridCanvas).toHaveAttribute("data-creation-disabled", "true");
  });

  test("keeps the Time grid free of drag creation when several bookable items are in scope", async () => {
    history.replace("/booking/calendar?date=2026-08-17&layout=time-grid");
    render(<CalendarPageStory history={history} />);
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(calendar.timeGrid).toHaveAttribute("aria-busy", "false");
    await expect.element(calendar.timeGridCanvas).toHaveAttribute("data-creation-disabled", "true");

    await calendar.dragTimeGridRange(12 * 60, 13 * 60);

    await expect.element(calendar.bookingDialog).not.toBeInTheDocument();
  });

  test("does not reject a drag across a repeated hour", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    try {
      await page.viewport(1440, 900);
      history = createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-10-25"] });
      const confocal = bookableItemFixtures.find((fixture) => fixture.target.globalId === "IN123");
      if (!confocal) throw new Error("Confocal microscope must be available in the catalogue fixture");
      worker.use(
        http.get(/\/api\/v2\/booking-catalogue(?:\/calendar)?$/, () =>
          HttpResponse.json({
            items: [
              {
                ...bookableItemOption(confocal),
                configurationVersion: confocal.configurationVersion,
                targetType: "INSTRUMENT",
                openingStart: "00:00",
                openingEnd: "24:00",
                effectiveRole: confocal.effectiveRole,
                capabilities: confocal.capabilities,
                location: {
                  name: confocal.target.value.parentContainerName,
                  globalId: confocal.target.value.parentContainerGlobalId,
                },
              },
            ],
            page: 1,
            pageSize: 20,
            total: 1,
            facets: { types: ["INSTRUMENT"] },
          }),
        ),
      );
      render(
        <CalendarPageStory
          history={history}
          preferences={{
            ...customNewYorkBookingPreferences,
            availabilityWindowStart: "00:00",
            availabilityWindowEnd: "24:00",
            timezoneMode: "CUSTOM",
            customTimezone: "Europe/Berlin",
          }}
        />,
      );
      await calendar.chooseView("By Item");
      await calendar.chooseView("Day");
      await expect.poll(() => calendar.resourceCanvases.length).toBe(1);
      const canvas = calendar.resourceCanvases[0];
      await expect.element(canvas).not.toHaveAttribute("data-creation-disabled", "true");
      const scroller = page.getByTestId("day-timeline-scroller").first();
      scroller.element().scrollTo({ left: 0 });
      await expect.poll(() => scroller.element().scrollLeft).toBe(0);
      const canvasBounds = canvas.element().getBoundingClientRect();
      const scrollerBounds = scroller.element().getBoundingClientRect();
      const dayLengthMinutes = 25 * 60;
      const positionAt = (minute: number) => ({
        x: canvasBounds.left - scrollerBounds.left + (minute / dayLengthMinutes) * canvasBounds.width,
        y: canvasBounds.top - scrollerBounds.top + 60,
      });
      // Target the viewport so Playwright does not center the oversized canvas and hide the drag origin.
      await userEvent.dragAndDrop(scroller, scroller, {
        sourcePosition: positionAt(150),
        targetPosition: positionAt(210),
      });
      await expect.element(calendar.bookingDialog).toBeVisible();
      const dialog = calendar.bookingDialog;
      await expect.element(dialog.getByLabelText("Start time")).toHaveValue("02:30");
      await expect.element(dialog.getByLabelText("End time")).toHaveValue("02:30");
      const occurrences = dialog.getByRole("group", { name: "Repeated local time" });
      await expect.element(occurrences.nth(0).getByRole("radio", { name: /Earlier occurrence/ })).toBeChecked();
      await expect.element(occurrences.nth(1).getByRole("radio", { name: /Later occurrence/ })).toBeChecked();

      await dialog.getByRole("textbox", { name: "Purpose" }).fill("Berlin repeated hour");
      await dialog.getByRole("button", { name: "Book", exact: true }).click();
      await expect.poll(() => bookingPageRequests.createdPayloads.length).toBe(1);
      expect(bookingPageRequests.createdPayloads[0]).toMatchObject({
        start: "2026-10-25T00:30:00Z",
        end: "2026-10-25T01:30:00Z",
      });
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("ends pristine creation when leaving Calendar so resource dragging works after returning", async () => {
    render(<CalendarPageStory history={history} />);
    await calendar.newBooking.click();
    await expect.element(calendar.bookingDialog).toBeVisible();

    history.push("/booking/bookable-items/IN124");
    await expect.element(calendar.bookableItemDetailsHeading).toBeVisible();
    await expect.element(calendar.bookingDialog).not.toBeInTheDocument();

    history.push("/booking/calendar?date=2026-08-17");
    await expect.element(calendar.heading).toBeVisible();
    await calendar.chooseView("By Item");
    await calendar.chooseView("Day");
    await expect.poll(() => calendar.resourceCanvases.length).toBe(5);

    await calendar.dragResourceSelection(0);
    await expect.element(calendar.bookingDialog.getByText("Bookable item", { exact: true })).not.toBeInTheDocument();
  });

  test("starts a resource booking from the keyboard with a proposed free hour", async () => {
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("By Item");
    await calendar.chooseView("Day");
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
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
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
    render(<CalendarPageStory history={history} />);
    await calendar.chooseView("By Item");
    await calendar.chooseView("Day");
    await expect.poll(() => calendar.resourceCanvases.length).toBe(5);

    await expect.element(page.getByRole("button", { name: "Add booking for Mass spectrometer" })).toBeDisabled();
  });

  test("creates a booking through the targetless compact form and restores trigger focus", async () => {
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);
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
    await dialog.getByLabelText("Start time").fill("08:30");
    await dialog.getByLabelText("End time").fill("09:30");
    await dialog.getByRole("textbox", { name: "Purpose" }).fill("Live-stack-shaped booking");
    await dialog.getByRole("button", { name: "Book", exact: true }).click();

    await expect.poll(() => bookingPageRequests.createdPayloads.length).toBe(1);
    expect(bookingPageRequests.createdPayloads[0]).toMatchObject({
      target: { relationTo: "booking-instruments", value: 123 },
      kind: "BOOKING",
      purpose: "Live-stack-shaped booking",
      start: "2026-08-17T08:30:00Z",
      end: "2026-08-17T09:30:00Z",
    });
    await expect.element(dialog).not.toBeInTheDocument();
    await expect.poll(() => document.activeElement).toBe(trigger.element());
  });

  test("opens the full booking form from compact More options", async () => {
    render(<CalendarPageStory history={history} preferences={customNewYorkBookingPreferences} />);
    const dialog = await calendar.openTargetlessBookingDialog();
    // A Berlin item seen from New York: the viewer's own hours for the date, with no timezone named.
    await expect.element(dialog.getByText(/^Open on this date: \d{2}:\d{2} [AP]M - \d{2}:\d{2} [AP]M$/)).toBeVisible();
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
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(390, 900);
    try {
      const item = page.getByRole("combobox", { name: "Bookable item" });
      const itemInformation = page.getByRole("region", { name: "Item information" });
      const start = page.getByRole("group", { name: "Start" });
      await expect.element(itemInformation).toBeVisible();
      await expect.element(itemInformation.getByText("Open", { exact: true })).toBeVisible();
      // Hours are in the viewer's timezone; the item's own are behind the globe button.
      await expect
        .element(itemInformation.getByText(/^Every day: \d{2}:\d{2}\s[AP]M\s-\s\d{2}:\d{2}\s[AP]M$/))
        .toBeVisible();
      // The item's own hours are in the shared instrument-time tooltip, which the time's button also names.
      await itemInformation
        .getByRole("button", { name: /^Every day: .*, Instrument time: .+ \(Europe\/Berlin\)$/ })
        .hover();
      await expect
        .element(page.getByRole("tooltip"))
        .toHaveTextContent(/^Instrument time: \d{2}:\d{2}\s[AP]M\s-\s\d{2}:\d{2}\s[AP]M \(Europe\/Berlin\)$/);
      await userEvent.keyboard("{Escape}");
      await expect.element(page.getByRole("button", { name: "Item information" })).not.toBeInTheDocument();
      expect(itemInformation.element().getBoundingClientRect().top).toBeGreaterThanOrEqual(
        item.element().getBoundingClientRect().bottom,
      );
      expect(itemInformation.element().getBoundingClientRect().bottom).toBeLessThanOrEqual(
        start.element().getBoundingClientRect().top,
      );
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
    const fullStart = page.getByRole("group", { name: "Start" });
    const fullEnd = page.getByRole("group", { name: "End" });
    await expect.element(fullStart.getByLabelText("Date")).toHaveValue("2026-08-18");
    await expect.element(fullStart.getByLabelText("Start time", { exact: true })).toHaveValue("09:15");
    await expect.element(fullEnd.getByLabelText("Date")).toHaveValue("2026-08-18");
    await expect.element(fullEnd.getByLabelText("End time", { exact: true })).toHaveValue("10:45");
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

  test("preserves the previous history entry when Back cancels a pending event focus", async () => {
    const originalUrl = window.location.href;
    const previousUrl = "/booking/calendar?date=2026-08-16&layout=agenda&calendar-resources.q=previous";
    window.history.replaceState({}, "", previousUrl);
    const browserHistory = createBrowserHistory();
    let release = () => {};
    let started = false;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    worker.use(
      http.get("/api/v2/booking-calendar/events", async ({ request }) => {
        if (new URL(request.url).searchParams.get("where")?.includes("IN123")) {
          started = true;
          await held;
        }
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const rendered = render(<CalendarPageStory history={browserHistory} />);
    try {
      await expect.element(calendar.heading).toBeVisible();
      browserHistory.push(
        "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=pending-back",
      );
      await expect.poll(() => started).toBe(true);
      browserHistory.back();
      await expect.poll(() => window.location.pathname + window.location.search).toBe(previousUrl);
      release();
      await expect.element(calendar.search).toHaveValue("previous");
      expect(window.location.pathname + window.location.search).toBe(previousUrl);
      browserHistory.forward();
      await expect.poll(() => new URLSearchParams(window.location.search).get("date")).toBe("2026-08-17");
    } finally {
      release();
      rendered.unmount();
      browserHistory.destroy();
      window.history.replaceState({}, "", originalUrl);
    }
  });

  test("keeps beforeunload protection after cancelling a dirty discard dialog", async () => {
    const originalUrl = window.location.href;
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    const browserHistory = createBrowserHistory();
    const rendered = render(<CalendarPageStory history={browserHistory} />);

    try {
      const dialog = await calendar.openTargetlessBookingDialog();
      await dialog.getByRole("textbox", { name: "Purpose" }).fill("Keep this draft");
      await dialog.getByRole("button", { name: "Cancel" }).click();
      const confirmation = page.getByRole("alertdialog", { name: "Discard this event?" });
      await expect.element(confirmation).toBeVisible();
      await confirmation.getByRole("button", { name: "Keep editing" }).click();

      const blockedBeforeUnload = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(blockedBeforeUnload);
      expect(blockedBeforeUnload.defaultPrevented).toBe(true);

      await dialog.getByRole("button", { name: "Cancel" }).click();
      await expect.element(confirmation).toBeVisible();
      await confirmation.getByRole("button", { name: "Discard changes" }).click();
      await expect.element(dialog).not.toBeInTheDocument();

      const allowedBeforeUnload = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(allowedBeforeUnload);
      expect(allowedBeforeUnload.defaultPrevented).toBe(false);
    } finally {
      rendered.unmount();
      browserHistory.destroy();
      window.history.replaceState({}, "", originalUrl);
      // MSW 2.15 installs a beforeunload listener that stops its browser worker
      // even when another listener prevents the synthetic event. Restart it so
      // later tests keep receiving the suite's request handlers.
      worker.stop();
      await worker.start({
        quiet: true,
        onUnhandledRequest: "bypass",
        serviceWorker: { url: "/mockServiceWorker.js" },
      });
    }
  });

  test("preserves a compact draft across recoverable server conflicts", async () => {
    let code = "errors.api.v2.booking.overlap";
    worker.use(
      http.post("/api/v2/bookings", () =>
        HttpResponse.json({ status: 409, code, detail: "private server detail" }, { status: 409 }),
      ),
    );
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);
    const dialog = await calendar.openTargetlessBookingDialog();
    const startTime = dialog.getByLabelText("Start time");
    const endTime = dialog.getByLabelText("End time");
    const submit = dialog.getByRole("button", { name: "Book", exact: true });
    await startTime.fill("08:30");
    await endTime.fill("09:30");
    const purpose = dialog.getByRole("textbox", { name: "Purpose" });
    await purpose.fill("Preserve this draft");

    await submit.click();
    await expect
      .element(dialog.getByText("This period overlaps another booking or a maintenance event."))
      .toBeVisible();
    await expect.element(purpose).toHaveValue("Preserve this draft");
    await expect.element(submit).toBeDisabled();

    await dialog.getByLabelText("End time").fill("09:45");
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

  test("warns before submitting a compact booking that overlaps another booking", async () => {
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);
    const dialog = await calendar.openTargetlessBookingDialog();
    const submit = dialog.getByRole("button", { name: "Book", exact: true });

    await dialog.getByLabelText("Start time").fill("12:00");
    await dialog.getByLabelText("End time").fill("13:00");

    await expect.element(dialog.getByText("This period overlaps:")).toBeVisible();
    await expect.element(dialog.getByText("Booking #42")).toBeVisible();
    await expect.element(submit).toBeDisabled();
    expect(bookingPageRequests.createdPayloads).toHaveLength(0);
  });

  test("does not replay a booking after a lost response and directs the user to existing bookings", async () => {
    let createRequests = 0;
    worker.use(
      http.post("/api/v2/bookings", () => {
        createRequests += 1;
        return HttpResponse.error();
      }),
    );
    render(<CalendarPageStory history={history} preferences={institutionBookingPreferences} />);
    const dialog = await calendar.openTargetlessBookingDialog();
    await dialog.getByLabelText("Start time").fill("08:30");
    await dialog.getByLabelText("End time").fill("09:30");
    const submit = dialog.getByRole("button", { name: "Book", exact: true });
    await submit.click();

    await expect.element(dialog.getByText("RSpace could not confirm whether the booking was saved.")).toBeVisible();
    await expect
      .element(dialog.getByRole("link", { name: "Check My Bookings" }))
      .toHaveAttribute("href", "/booking/my-bookings?period=upcoming");
    await expect.element(dialog.getByRole("button", { name: "More options" })).toBeDisabled();
    expect(createRequests).toBe(1);
    await expect.element(submit).toBeDisabled();

    await dialog.getByRole("textbox", { name: "Purpose" }).fill("Do not replay this request");
    await expect.element(submit).toBeDisabled();
    expect(createRequests).toBe(1);
  });

  test("does not expose maintenance creation to a run-as sysadmin", async () => {
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
    await expect.element(newBooking).toBeVisible();
    await expect.element(moreCreationOptions).toBeVisible();
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
    await dialog.getByRole("button", { name: "Choose a bookable item" }).click();
    await page.getByRole("option", { name: /Confocal microscope.*IN123/ }).click();
    await dialog.getByLabelText("Start time").fill("18:00");
    await dialog.getByLabelText("End time").fill("19:00");
    await expect.element(dialog.getByRole("button", { name: "Create maintenance event" })).toBeEnabled();
    await expect
      .element(dialog.getByText("This period overlaps another booking or a maintenance event."))
      .not.toBeInTheDocument();
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
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(390, 667);
    try {
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
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });
});
