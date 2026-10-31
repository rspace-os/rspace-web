import { bookingKeys } from "@/modules/booking/domain/bookingKeys";
import "@/__tests__/__mocks__/matchMedia";
import { act, cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it, onTestFinished, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { cataloguePage as creationCataloguePage } from "@/modules/booking/creation/__tests__/creationTestFixtures";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import {
  bookableItemFixtures,
  bookableItemsHandlers,
  bookableItemsOpenApi,
} from "../../bookable-items/mocks/bookableItemsMocks";
import { busyBooking, collectionResponse, otherBooking, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import {
  restoreCalendarScrollTo,
  scrollIntoView,
  setupCalendarBookableItems,
  setupCalendarScrollTo,
  toggleQuickFilter,
} from "./calendarPageTestUtils";
import { renderCalendar } from "./calendarTestHarness";

// 2026-08-17 is a Monday.
// The item shares the viewer's browser time zone, so its Monday closure fills the viewer's Monday.
const closedOnMonday = {
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  openingHours: [
    { dayOfWeek: 2, start: "00:00", end: "24:00" },
    { dayOfWeek: 3, start: "00:00", end: "24:00" },
    { dayOfWeek: 4, start: "00:00", end: "24:00" },
    { dayOfWeek: 5, start: "00:00", end: "24:00" },
    { dayOfWeek: 6, start: "00:00", end: "24:00" },
    { dayOfWeek: 7, start: "00:00", end: "24:00" },
  ],
};
const readOnlyCapabilities = {
  ...bookableItemFixtures[0].capabilities,
  canCreateBooking: false,
  canEditConfiguration: false,
};

type User = ReturnType<typeof userEvent.setup>;

/** Chooses a layout or period in the View menu, then closes it; an open menu makes the rest of the page inert. */
async function chooseView(user: User, option: string) {
  await user.click(await screen.findByRole("button", { name: /^View: / }));
  await user.click(await screen.findByRole("menuitemradio", { name: option }));
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("menu")).not.toBeInTheDocument());
}

function catalogueItem(item: (typeof bookableItemFixtures)[number], overrides: Record<string, unknown> = {}) {
  return {
    ...item,
    configurationId: item.id,
    targetType: "INSTRUMENT",
    targetId: item.target.value.id,
    globalId: item.target.globalId,
    name: item.target.value.name,
    location: null,
    ...overrides,
  };
}

function cataloguePage(items: readonly Record<string, unknown>[]) {
  return { ...creationCataloguePage([], 1, items.length), items };
}

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

describe("CalendarPage", () => {
  it.each(["resources", "time-grid", "agenda"])("renders relationship locations in %s", async (layout) => {
    server.use(
      http.get("/api/v2/booking-calendar/events", () =>
        HttpResponse.json(collectionResponse([ownBooking, otherBooking])),
      ),
    );
    const user = userEvent.setup();
    await renderCalendar(`/booking/calendar?date=2026-08-17&view=week&layout=${layout}`);
    const own = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    await user.click(within(own).getByRole("button"));
    expect(
      within(await screen.findByRole("dialog")).getByRole("link", { name: "Confocal microscope room" }),
    ).toHaveAttribute("href", "/globalId/IC123");
    await user.keyboard("{Escape}");
    const other = screen.getByRole("article", { name: /Electron microscope · Grace Hopper/ });
    await user.click(within(other).getByRole("button"));
    expect(within(await screen.findByRole("dialog")).getByRole("link", { name: "Workbench" })).toHaveAttribute(
      "href",
      "/globalId/BE124",
    );
    await user.keyboard("{Escape}");
    if (layout === "resources") expect(screen.getByRole("link", { name: "Imaging lab" })).toBeVisible();
  });

  it("keeps the view and layout in the query string so a reload reopens them", async () => {
    const user = userEvent.setup();
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))));

    await renderCalendar("/booking/calendar?layout=time-grid&view=week");

    expect(await screen.findByRole("button", { name: "View: Time grid · Week" })).toBeVisible();
    await chooseView(user, "Agenda");
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("layout")).toBe("agenda"));
    expect(new URLSearchParams(window.location.search).get("view")).toBe("week");
  });

  it("focuses the saved event occurrence in Agenda and removes the consumed request", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))));
    const { router } = await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&view=week&focus=41&focusRequest=agenda-focus",
    );

    const article = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    const eventElement = article.closest<HTMLElement>("[data-calendar-event-focus]");
    if (!eventElement) throw new Error("The event card must expose its date and event ID");
    const trigger = eventElement.querySelector<HTMLElement>("button");
    if (!trigger) throw new Error("The event card must have a keyboard-focusable trigger");

    await waitFor(() => expect(trigger).toHaveFocus());
    expect(eventElement).toHaveAttribute("data-calendar-event-focus", "2026-08-17:41");
    expect(eventElement).toHaveAttribute("data-calendar-event-focus-highlight", "true");
    expect(scrollIntoView).toHaveBeenCalledWith({ block: "center" });
    await waitFor(() => expect(router.state.location.searchStr).not.toContain("focusRequest="));
    expect(router.state.location.searchStr).toContain("layout=agenda");
    expect(router.state.location.searchStr).toContain("view=week");
    expect(router.state.location.searchStr).toContain("target=IN123");
  });

  it("waits for an invalidation refetch before deciding a focused event is unavailable", async () => {
    let requestNumber = 0;
    let refetchStarted = () => {};
    let releaseRefetch = () => {};
    const refetchHasStarted = new Promise<void>((resolve) => {
      refetchStarted = resolve;
    });
    const refetchMayFinish = new Promise<void>((resolve) => {
      releaseRefetch = resolve;
    });
    onTestFinished(() => releaseRefetch());
    server.use(
      http.get("/api/v2/booking-calendar/events", async () => {
        requestNumber += 1;
        if (requestNumber === 1) return HttpResponse.json(collectionResponse([]));
        refetchStarted();
        await refetchMayFinish;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );

    const { queryClient, router } = await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda",
    );
    await waitFor(() => {
      const eventQueries = queryClient.getQueryCache().findAll({
        queryKey: bookingKeys.calendarEvents(),
      });
      expect(eventQueries.some((query) => query.state.data !== undefined && query.state.fetchStatus === "idle")).toBe(
        true,
      );
    });

    act(() => {
      void queryClient.invalidateQueries({ queryKey: bookingKeys.all });
    });
    await refetchHasStarted;
    const cachedRefetch = queryClient
      .getQueryCache()
      .findAll({ queryKey: bookingKeys.calendarEvents() })
      .find((query) => query.state.data !== undefined && query.state.fetchStatus === "fetching");
    expect(cachedRefetch?.state.data).toEqual([]);

    await act(async () => {
      window.history.replaceState(
        null,
        "",
        "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=invalidation-refetch",
      );
      router.history.push(
        "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=invalidation-refetch",
      );
    });

    expect(screen.queryByText("This event is no longer available in the calendar.")).not.toBeInTheDocument();
    expect(router.state.location.searchStr).toContain("focusRequest=invalidation-refetch");

    releaseRefetch();
    const article = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    await waitFor(() => expect(article.querySelector("button")).toHaveFocus());
    await waitFor(() => expect(router.state.location.searchStr).not.toContain("focusRequest="));
    expect(requestNumber).toBe(2);
  });

  it("falls back from the Resources layout when its disabled target has no resource row", async () => {
    const bookingTarget = ownBooking.target;
    if (!bookingTarget) throw new Error("The calendar fixture must include a target");
    const disabledBooking: BookingListDocument = {
      ...ownBooking,
      id: 55,
      target: {
        ...bookingTarget,
        globalId: "IN999",
        value: { ...bookingTarget.value, id: 999, name: "Disabled microscope" },
      },
    };
    server.use(
      http.get("/api/v2/booking-catalogue/calendar", () => HttpResponse.json(cataloguePage([]))),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([disabledBooking]))),
    );
    await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN999&layout=resources&view=week&focus=55&focusRequest=disabled-resource",
    );

    expect(await screen.findByRole("button", { name: "View: Time grid · Day" })).toBeVisible();
    const article = await screen.findByRole("article", { name: /Disabled microscope/ });
    const eventElement = article.closest<HTMLElement>("[data-calendar-event-focus]");
    if (!eventElement) throw new Error("The fallback event must expose its date and event ID");
    await waitFor(() => expect(eventElement.querySelector("button")).toHaveFocus());
    expect(eventElement).toHaveAttribute("data-calendar-event-focus", "2026-08-17:55");
  });

  it("falls back to the day view when the requested week event is in +N more", async () => {
    const overlappingBookings = [61, 62, 63].map((id) => ({
      ...ownBooking,
      id,
      start: "2026-08-17T08:00:00Z",
      end: "2026-08-17T09:00:00Z",
    }));
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse(overlappingBookings))),
    );
    await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=time-grid&view=week&focus=63&focusRequest=week-overflow",
    );

    expect(await screen.findByRole("button", { name: "View: Time grid · Day" })).toBeVisible();
    await waitFor(() => expect(document.querySelector('[data-calendar-event-focus="2026-08-17:63"]')).toBeVisible());
    const focusTarget = document.querySelector<HTMLElement>('[data-calendar-event-focus="2026-08-17:63"]');
    expect(focusTarget).toBeVisible();
    await waitFor(() => expect(focusTarget?.querySelector("button")).toHaveFocus());
  });

  it("focuses a multi-day event at its start-date occurrence", async () => {
    const multiDayBooking: BookingListDocument = {
      ...ownBooking,
      id: 72,
      start: "2026-08-19T12:00:00Z",
      end: "2026-08-21T12:00:00Z",
    };
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([multiDayBooking]))),
    );
    await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=time-grid&view=week&focus=72&focusRequest=multi-day",
    );

    const dateParts = new Intl.DateTimeFormat("en-US", {
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(new Date(multiDayBooking.start));
    const part = (type: "year" | "month" | "day") => dateParts.find((datePart) => datePart.type === type)?.value;
    const startDate = `${part("year")}-${part("month")}-${part("day")}`;
    const eventTriggers = await screen.findAllByRole("button", { name: /^Show details for Confocal microscope/ });
    let startOccurrence: HTMLElement | null = null;
    await waitFor(() => {
      const focusedTrigger = eventTriggers.find((trigger) => trigger === document.activeElement);
      startOccurrence = focusedTrigger?.closest<HTMLElement>("[data-calendar-event-focus]") ?? null;
      expect(startOccurrence).toHaveAttribute("data-calendar-event-focus", `${startDate}:72`);
    });
    expect(startOccurrence).toBeVisible();
    const eventOccurrences = [...document.querySelectorAll<HTMLElement>("[data-calendar-event-focus]")].filter(
      (element) => element.dataset.calendarEventFocus?.endsWith(":72"),
    );
    expect(eventOccurrences.length).toBeGreaterThan(1);
    expect(eventOccurrences).toContain(startOccurrence);
  });

  it("retries a missing focused event and clears its unavailable status", async () => {
    let available = false;
    server.use(
      http.get("/api/v2/booking-calendar/events", () =>
        HttpResponse.json(collectionResponse(available ? [ownBooking] : [])),
      ),
    );
    const { router } = await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=missing-event",
    );

    expect(await screen.findByText("This event is no longer available in the calendar.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Retry" })).toBeVisible();
    await waitFor(() => expect(router.state.location.searchStr).not.toContain("focusRequest="));
    available = true;
    // Keep the real URL aligned with the memory router for the focus cleanup guard.
    window.history.replaceState(null, "", router.state.location.href);
    await userEvent.setup().click(screen.getByRole("button", { name: "Retry" }));
    const article = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    await waitFor(() => expect(article.closest("[data-calendar-event-focus]")?.querySelector("button")).toHaveFocus());
    expect(screen.queryByText("This event is no longer available in the calendar.")).not.toBeInTheDocument();
  });

  it("focuses an available event when animation frames are suspended", async () => {
    const frames = vi.spyOn(window, "requestAnimationFrame").mockReturnValue(0);
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))));
    try {
      const { router } = await renderCalendar(
        "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=suspended-frames",
      );
      const article = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
      await waitFor(() =>
        expect(article.closest("[data-calendar-event-focus]")?.querySelector("button")).toHaveFocus(),
      );
      expect(screen.queryByText("This event is no longer available in the calendar.")).not.toBeInTheDocument();
      await waitFor(() => expect(router.state.location.searchStr).not.toContain("focusRequest="));
    } finally {
      frames.mockRestore();
    }
  });

  it("removes the previous highlight when another event is focused", async () => {
    const second = {
      ...ownBooking,
      id: 42,
      bookedBy: "Grace Hopper",
      purpose: "Second imaging",
      start: "2026-08-17T11:00:00Z",
      end: "2026-08-17T12:00:00Z",
    };
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking, second]))),
    );
    const { router } = await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=first",
    );
    const first = await screen.findByRole("article", { name: /Ada Lovelace/ });
    await waitFor(() =>
      expect(first.closest("[data-calendar-event-focus]")).toHaveAttribute(
        "data-calendar-event-focus-highlight",
        "true",
      ),
    );
    const nextUrl = "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=42&focusRequest=second";
    window.history.replaceState(null, "", nextUrl);
    await act(() => router.history.push(nextUrl));
    const next = await screen.findByRole("article", { name: /Grace Hopper/ });
    await waitFor(() =>
      expect(next.closest("[data-calendar-event-focus]")).toHaveAttribute(
        "data-calendar-event-focus-highlight",
        "true",
      ),
    );
    expect(first.closest("[data-calendar-event-focus]")).not.toHaveAttribute("data-calendar-event-focus-highlight");
  });

  it("does not expire a focus request while the event read is pending", async () => {
    let release = () => {};
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    onTestFinished(release);
    server.use(
      http.get("/api/v2/booking-calendar/events", async () => {
        await held;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const { router } = await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=slow",
    );
    await screen.findByRole("heading", { name: "Calendar" });
    vi.useFakeTimers();
    try {
      await act(() => vi.advanceTimersByTimeAsync(16_000));
      expect(router.state.location.searchStr).toContain("focusRequest=slow");
      expect(screen.queryByText("This event is no longer available in the calendar.")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
      release();
    }
    const article = await screen.findByRole("article", { name: /Ada Lovelace/ });
    await waitFor(() => expect(article.closest("[data-calendar-event-focus]")?.querySelector("button")).toHaveFocus());
  });

  it("cancels a pending focus after the user moves on", async () => {
    let releaseResolve: () => void = () => {};
    let requestStarted: () => void = () => {};
    const releaseRequest = new Promise<void>((resolve) => {
      releaseResolve = resolve;
    });
    const started = new Promise<void>((resolve) => {
      requestStarted = resolve;
    });
    onTestFinished(() => releaseResolve());
    server.use(
      http.get("/api/v2/booking-calendar/events", async () => {
        requestStarted();
        await releaseRequest;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    const { router } = await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=pending-focus",
    );
    await started;

    await user.keyboard("x");
    await waitFor(() => expect(router.state.location.searchStr).not.toContain("focusRequest="));
    releaseResolve();
    const article = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    const trigger = article.closest("[data-calendar-event-focus]")?.querySelector("button");

    expect(trigger).not.toHaveFocus();
    expect(article.closest("[data-calendar-event-focus]")).not.toHaveAttribute("data-calendar-event-focus-highlight");
  });

  it("opens the week when a link asks for Month in By Item", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))));

    await renderCalendar("/booking/calendar?view=month");

    expect(await screen.findByRole("button", { name: "View: By Item · Week" })).toBeVisible();
  });

  it("hides item filter controls and keeps Calendar controls available", async () => {
    const user = userEvent.setup();
    await renderCalendar();
    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /^Bookable items(?:,|$)/ })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Search Calendar" })).toBeVisible();
    for (const name of ["Jump to date", "View: By Item · Day"]) {
      expect(screen.getByRole("button", { name })).toBeVisible();
    }

    await user.click(screen.getByRole("button", { name: /^Filters(?:$|,)/ }));
    expect(await screen.findByRole("switch", { name: "My Bookings" })).toHaveAccessibleDescription("Bookings you made");
    expect(screen.getByRole("switch", { name: "Owned Items" })).toHaveAccessibleDescription(
      "Bookings on items you own",
    );
    await user.click(screen.getByRole("button", { name: "Edit filters" }));
    expect(await screen.findByRole("button", { name: "Add filter" })).toBeVisible();
  });

  it("shows quick filters that are on as removable chips", async () => {
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("region", { name: "Resource booking schedule" });

    await toggleQuickFilter(user, "My Bookings");
    expect(screen.getByRole("button", { name: "Filters, 1 applied" })).toBeVisible();
    await waitFor(() => expect(new URLSearchParams(window.location.search).has("mineOnly")).toBe(true));
    const remove = screen.getByRole("button", { name: "Remove My Bookings filter" });

    await user.click(remove);
    expect(screen.queryByRole("button", { name: "Remove My Bookings filter" })).not.toBeInTheDocument();
    // The chip unmounts with its button, so focus moves to the Filters control rather than the page.
    expect(screen.getByRole("button", { name: "Filters, none applied" })).toHaveFocus();
  });

  it("resets a target-only calendar view and widens its request scopes", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-17T12:00:00Z"));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    try {
      const catalogueRequests: URL[] = [];
      const eventRequests: URL[] = [];
      server.use(
        http.get("/api/v2/openapi.json", () =>
          HttpResponse.json({
            paths: { ...bookableItemsOpenApi.paths, ...bookingsOpenApi.paths },
            components: bookableItemsOpenApi.components,
          }),
        ),
        ...bookableItemsHandlers((request) => {
          const url = new URL(request.url);
          if (url.pathname === "/api/v2/booking-catalogue/calendar") catalogueRequests.push(url);
        }),
        http.get("/api/v2/booking-calendar/events", ({ request }) => {
          eventRequests.push(new URL(request.url));
          return HttpResponse.json(collectionResponse([]));
        }),
      );
      const { router } = await renderCalendar("/booking/calendar?target=IN124");

      expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
      expect(screen.getByRole("button", { name: "View: By Item · Day" })).toBeVisible();
      await waitFor(() => {
        expect(catalogueRequests.some((request) => request.searchParams.get("where") === "target==IN124")).toBe(true);
        expect(eventRequests.some((request) => request.searchParams.get("where")?.includes("(target==IN124)"))).toBe(
          true,
        );
      });

      const resetButton = await screen.findByRole("button", {
        name: "Reset filters, sorting, and columns to defaults",
      });
      const catalogueRequestsBeforeReset = catalogueRequests.length;
      const eventRequestsBeforeReset = eventRequests.length;
      await user.click(resetButton);

      await waitFor(() => {
        expect(
          catalogueRequests
            .slice(catalogueRequestsBeforeReset)
            .some((request) => !(request.searchParams.get("where") ?? "")),
        ).toBe(true);
        expect(new URLSearchParams(router.history.location.search).has("target")).toBe(false);
      });
      const resetEventWhere = await waitFor(() => {
        const request = eventRequests.slice(eventRequestsBeforeReset).find((candidate) => {
          const where = candidate.searchParams.get("where") ?? "";
          return where.includes("target=in=(") && !where.includes("(target==IN124)");
        });
        if (!request) throw new Error("Expected the reset calendar event request to include all resources");
        return request.searchParams.get("where") ?? "";
      });

      for (const target of ["IN123", "IN124", "IN125", "IN126", "IN127"]) {
        expect(resetEventWhere).toContain(target);
      }
      expect(resetEventWhere).not.toContain("(target==IN124)");
      expect(screen.getByRole("button", { name: "View: By Item · Day" })).toBeVisible();
      expect(
        screen.queryByRole("button", { name: "Reset filters, sorting, and columns to defaults" }),
      ).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("shows the route's bookable-item focus as a removable filter chip", async () => {
    const user = userEvent.setup();
    const { router } = await renderCalendar("/booking/calendar?date=2026-08-17&target=IN124&unrelated=kept");

    const remove = await screen.findByRole("button", { name: "Remove bookable item filter" });
    const chip = remove.closest<HTMLElement>("[data-calendar-target-filter]");
    if (!chip) throw new Error("The remove button must belong to the target filter chip");
    await waitFor(() => expect(chip).toHaveTextContent("Bookable item: Electron microscope (IN124)"));
    expect(screen.getByRole("button", { name: "Reset filters, sorting, and columns to defaults" })).toBeVisible();

    await user.click(remove);

    await waitFor(() => expect(router.state.location.searchStr).not.toContain("target="));
    expect(router.state.location.searchStr).toContain("date=2026-08-17");
    expect(router.state.location.searchStr).toContain("unrelated=kept");
    expect(screen.queryByRole("button", { name: "Remove bookable item filter" })).not.toBeInTheDocument();
    // Focus stays in the calendar controls rather than falling back to the page.
    expect(document.activeElement).toHaveAttribute("data-table-list-filters");
    // Items other than IN124 come back once the route focus is gone.
    expect((await screen.findAllByText("IN123", { exact: true }))[0]).toBeVisible();
  });

  it("names the route's bookable item while Search hides it from the loaded items and events", async () => {
    const catalogueRequests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        catalogueRequests.push(new URL(request.url));
        return undefined;
      }),
    );
    await renderCalendar("/booking/calendar?date=2026-08-17&target=IN124&calendar-resources.q=confocal");

    const remove = await screen.findByRole("button", { name: "Remove bookable item filter" });
    const chip = remove.closest<HTMLElement>("[data-calendar-target-filter]");
    if (!chip) throw new Error("The remove button must belong to the target filter chip");
    await waitFor(() => expect(chip).toHaveTextContent("Bookable item: Electron microscope (IN124)"));
    expect(chip).not.toHaveAttribute("aria-busy");
    // The Search really excludes the item: no row or event names it.
    expect(catalogueRequests.some((url) => url.searchParams.get("q") === "confocal")).toBe(true);
    expect(screen.getByRole("textbox", { name: "Search Calendar" })).toHaveValue("confocal");
    expect(screen.queryByText("Electron microscope", { exact: true })).not.toBeInTheDocument();
  });

  it("shows the route's bookable item as loading, then as not restored when its name cannot be resolved", async () => {
    let releaseResolve: () => void = () => {};
    const resolveHeld = new Promise<void>((resolve) => {
      releaseResolve = resolve;
    });
    server.use(
      http.get("/api/v2/booking-configurations", async ({ request }) => {
        if (!new URL(request.url).searchParams.get("where")?.startsWith("target=in=")) return undefined;
        await resolveHeld;
        return new HttpResponse(null, { status: 503 });
      }),
    );
    onTestFinished(() => releaseResolve());
    await renderCalendar("/booking/calendar?date=2026-08-17&target=IN124&calendar-resources.q=confocal");

    const remove = await screen.findByRole("button", { name: "Remove bookable item filter" });
    const chip = remove.closest<HTMLElement>("[data-calendar-target-filter]");
    if (!chip) throw new Error("The remove button must belong to the target filter chip");
    await waitFor(() => expect(chip).toHaveAttribute("aria-busy", "true"));
    expect(within(chip).getByText("Bookable item: IN124", { exact: true })).toBeInTheDocument();
    expect(within(chip).getByText("Loading", { exact: true })).toBeInTheDocument();

    releaseResolve();
    await waitFor(() =>
      expect(chip).toHaveTextContent("Bookable item: IN124 — Could not restore this saved selection. Try again."),
    );
    expect(chip).not.toHaveAttribute("aria-busy");
    expect(within(chip).queryByText("Loading", { exact: true })).not.toBeInTheDocument();
  });

  it("explains why Month is unavailable in the By Item layout", async () => {
    const user = userEvent.setup();
    await renderCalendar();
    await user.click(await screen.findByRole("button", { name: "View: By Item · Day" }));
    const month = await screen.findByRole("menuitemradio", { name: "Month" });
    expect(month).toHaveAttribute("aria-disabled", "true");
    expect(within(month).getByText("Not available in the By Item view")).toBeVisible();
    expect(month).toHaveAccessibleDescription(
      "Month isn't available in the By Item view. Use Time grid or Agenda for a month overview.",
    );

    await user.click(screen.getByRole("menuitemradio", { name: "Agenda" }));
    await waitFor(() =>
      expect(screen.getByRole("menuitemradio", { name: "Month" })).not.toHaveAttribute("aria-disabled", "true"),
    );
    expect(screen.getByRole("menuitemradio", { name: "Month" })).not.toHaveAccessibleDescription();
    await user.click(screen.getByRole("menuitemradio", { name: "Month" }));
    await user.keyboard("{Escape}");
    expect(await screen.findByRole("button", { name: "View: Agenda · Month" })).toBeVisible();
  });

  it("keeps viewer events visible while disabling resource creation", async () => {
    const item = bookableItemFixtures[0];
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", () =>
        HttpResponse.json({
          items: [
            {
              ...item,
              configurationId: item.id,
              targetType: "INSTRUMENT",
              targetId: item.target.value.id,
              globalId: item.target.globalId,
              name: item.target.value.name,
              location: null,
              capabilities: {
                ...item.capabilities,
                canCreateBooking: false,
                canEditConfiguration: false,
              },
              effectiveRole: "VIEWER",
            },
          ],
          page: 1,
          pageSize: 20,
          total: 1,
          facets: { types: ["INSTRUMENT"] },
        }),
      ),
    );
    await renderCalendar();
    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    expect(screen.getByRole("button", { name: "Add booking for Confocal microscope" })).toBeDisabled();
    expect(screen.queryByRole("link", { name: "Edit configuration" })).not.toBeInTheDocument();
    expect(screen.getByTestId("day-timeline-canvas")).toHaveAttribute("data-creation-disabled", "true");
  });

  it("shades a closed weekday for read-only viewers in the By Item and Time grid day views", async () => {
    const item = bookableItemFixtures[0];
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", () =>
        HttpResponse.json({
          items: [
            {
              ...item,
              configurationId: item.id,
              targetType: "INSTRUMENT",
              targetId: item.target.value.id,
              globalId: item.target.globalId,
              name: item.target.value.name,
              location: null,
              // 2026-08-17 is a Monday.
              openingHours: item.openingHours.filter((entry) =>
                [2, 3, 4, 5, 6, 7].some((dayOfWeek) => dayOfWeek === entry.dayOfWeek),
              ),

              capabilities: { ...item.capabilities, canCreateBooking: false, canEditConfiguration: false },
              effectiveRole: "VIEWER",
            },
          ],
          page: 1,
          pageSize: 20,
          total: 1,
          facets: { types: ["INSTRUMENT"] },
        }),
      ),
    );
    await renderCalendar(`/booking/calendar?date=2026-08-17&target=${item.target.globalId}`);

    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    await waitFor(() =>
      expect(screen.getByTestId("day-timeline-closed-hours")).toHaveStyle({ left: "0%", width: "100%" }),
    );

    await chooseView(userEvent.setup(), "Time grid");
    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    expect(screen.getByTestId("day-timeline-closed-hours")).toHaveStyle({ left: "0%", width: "100%" });
    // The one item in scope is read-only for this viewer, so the Time grid offers no creation either.
    expect(screen.getByTestId("day-timeline-canvas")).toHaveAttribute("data-creation-disabled", "true");
  });

  it("shades the Time grid for the one item an item filter leaves in scope", async () => {
    const [confocal, electron] = bookableItemFixtures;
    const catalogueWheres: (string | null)[] = [];
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where");
        catalogueWheres.push(where);
        const items = [catalogueItem(confocal, closedOnMonday), catalogueItem(electron, closedOnMonday)];
        return HttpResponse.json(cataloguePage(where === "target==IN123" ? items.slice(0, 1) : items));
      }),
    );
    await renderCalendar("/booking/calendar?date=2026-08-17&calendar-resources.where=target%3D%3DIN123");

    await waitFor(() => expect(screen.getAllByTestId("day-timeline-closed-hours")).toHaveLength(1));
    expect(catalogueWheres.at(-1)).toBe("target==IN123");
    expect(screen.queryByRole("button", { name: "Remove bookable item filter" })).not.toBeInTheDocument();

    await chooseView(userEvent.setup(), "Time grid");
    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    expect(screen.getByTestId("day-timeline-closed-hours")).toHaveStyle({ left: "0%", width: "100%" });
  });

  it("does not shade the Time grid when several items are in scope", async () => {
    const [confocal, electron] = bookableItemFixtures;
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", () =>
        HttpResponse.json(
          cataloguePage([catalogueItem(confocal, closedOnMonday), catalogueItem(electron, closedOnMonday)]),
        ),
      ),
    );
    await renderCalendar("/booking/calendar?date=2026-08-17&calendar-resources.where=target%3Din%3D(IN123%2CIN124)");

    // Both rows have loaded, each with its closed Monday.
    await waitFor(() => expect(screen.getAllByTestId("day-timeline-closed-hours")).toHaveLength(2));

    await chooseView(userEvent.setup(), "Time grid");
    const timeGrid = await screen.findByRole("region", { name: "Time grid" });
    expect(await within(timeGrid).findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    await waitFor(() => expect(timeGrid).toHaveAttribute("aria-busy", "false"));
    expect(screen.queryAllByTestId("day-timeline-closed-hours")).toHaveLength(0);
    // Several items have no single item to book, so the Time grid keeps drag creation off.
    expect(within(timeGrid).getByTestId("day-timeline-canvas")).toHaveAttribute("data-creation-disabled", "true");
  });

  it("keeps closure shading for a read-only row and offers it no booking or configuration action", async () => {
    const [confocal, electron] = bookableItemFixtures;
    server.use(
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
      http.get("/api/v2/booking-catalogue/calendar", () =>
        HttpResponse.json(
          cataloguePage([
            catalogueItem(confocal, { ...closedOnMonday, capabilities: readOnlyCapabilities, effectiveRole: "VIEWER" }),
            catalogueItem(electron),
          ]),
        ),
      ),
    );
    await renderCalendar();

    const addReadOnly = await screen.findByRole("button", { name: "Add booking for Confocal microscope" });
    const readOnlyRow = addReadOnly.closest("section");
    const addBookable = screen.getByRole("button", { name: "Add booking for Electron microscope" });
    const bookableRow = addBookable.closest("section");
    if (!readOnlyRow || !bookableRow) throw new Error("Each Add booking button must belong to its resource row");

    await waitFor(() =>
      expect(within(readOnlyRow).getByTestId("day-timeline-closed-hours")).toHaveStyle({ left: "0%", width: "100%" }),
    );
    expect(addReadOnly).toBeDisabled();
    expect(within(readOnlyRow).getByTestId("day-timeline-canvas")).toHaveAttribute("data-creation-disabled", "true");
    expect(within(readOnlyRow).queryByRole("link", { name: "View configuration" })).not.toBeInTheDocument();
    // The bookable row's action proves the query above would find the link if the row offered it.
    expect(within(bookableRow).getByRole("link", { name: "View configuration" })).toBeVisible();
  });

  it("offers a retry when booking events cannot be loaded", async () => {
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-calendar/events", () => {
        requests += 1;
        return requests === 1
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    const { router } = await renderCalendar(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=retry-focus",
    );

    expect(await screen.findByRole("alert")).toMatchTextContent("Booking events are unavailable.");
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    const article = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    expect(article).toBeVisible();
    await waitFor(() => expect(article.querySelector("button")).toHaveFocus());
    await waitFor(() => expect(router.state.location.searchStr).not.toContain("focusRequest="));
    expect(requests).toBe(2);
  });

  it("offers a calendar file from the event card only when the booking can be exported", async () => {
    const roleLost: BookingListDocument = {
      ...ownBooking,
      target: null,
      timezone: null,
      canViewConfiguration: false,
      canEdit: false,
      canCancel: false,
    };
    const showCalendarWith = async (booking: BookingListDocument) => {
      server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([booking]))));
      await renderCalendar(`/booking/calendar?date=${booking.start.slice(0, 10)}`);
      await chooseView(userEvent.setup(), "Time grid");
      await userEvent.setup().click(await screen.findByRole("button", { name: /^Show details for/ }));
    };

    await showCalendarWith(ownBooking);
    expect(await screen.findByRole("button", { name: /^\.ics file for Confocal microscope/ })).toBeVisible();

    cleanup();
    // The download endpoint requires the configuration read this row has lost.
    await showCalendarWith(roleLost);
    expect(await screen.findByRole("link", { name: "View details" })).toBeVisible();
    expect(screen.getByText("Unknown item")).toBeVisible();
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^\.ics file for/ })).not.toBeInTheDocument();

    cleanup();
    await showCalendarWith(busyBooking);
    expect(screen.queryByRole("link", { name: "View details" })).not.toBeInTheDocument();
  });
});
