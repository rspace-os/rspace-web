import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import "@/__tests__/__mocks__/matchMedia";
import { act, cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it, onTestFinished, vi } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import {
  bookableItemFixtures,
  bookableItemsHandlers,
  bookableItemsOpenApi,
} from "../../bookable-items/mocks/bookableItemsMocks";
import { bookingPagesHandlers } from "../../mocks/bookingPagesMocks";
import { busyBooking, collectionResponse, currentUser, ownBooking, renderCalendar } from "./calendarTestHarness";

const scrollToDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTo");
const scrollIntoViewDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollIntoView");
const scrollIntoView = vi.fn();

// 2026-08-17 is a Monday.
const closedOnMonday = { openDays: [2, 3, 4, 5, 6, 7], openingExceptions: [] };
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

async function toggleQuickFilter(user: User, name: string) {
  await user.click(await screen.findByRole("button", { name }));
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
  return { items, page: 1, pageSize: 20, total: items.length, facets: { types: ["INSTRUMENT"] } };
}

/** nuqs reads `window.location`, not the router's memory history, so seed both. */
async function renderCalendarAt(url: string) {
  const previous = window.location.href;
  window.history.replaceState(null, "", url);
  onTestFinished(() => window.history.replaceState(null, "", previous));
  return renderCalendar(url);
}

beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, "scrollTo", { configurable: true, value: vi.fn() });
  Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: scrollIntoView });
});

beforeEach(() => {
  scrollIntoView.mockClear();
  server.use(...bookingPagesHandlers());
});

afterAll(() => {
  if (scrollToDescriptor) Object.defineProperty(HTMLElement.prototype, "scrollTo", scrollToDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
  if (scrollIntoViewDescriptor)
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", scrollIntoViewDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollIntoView");
});

describe("CalendarPage", () => {
  it("filters calendar resources and events to Owned Items", async () => {
    const catalogueRequests: URL[] = [];
    const eventRequests: URL[] = [];
    server.use(
      http.get("/api/v2/openapi.json", () =>
        HttpResponse.json({ paths: { ...bookableItemsOpenApi.paths, ...bookingsOpenApi.paths } }),
      ),
      ...bookableItemsHandlers((request) => {
        const url = new URL(request.url);
        if (url.pathname === "/api/v2/booking-catalogue/calendar") catalogueRequests.push(url);
      }),
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        eventRequests.push(new URL(request.url));
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    await toggleQuickFilter(user, "Owned Items");

    await waitFor(() => {
      expect(catalogueRequests.at(-1)?.searchParams.get("mine")).toBe("true");
      expect(eventRequests.some((request) => request.searchParams.get("mine") === "true")).toBe(true);
      expect(new URLSearchParams(window.location.search).has("myItemsOnly")).toBe(true);
    });
  });

  it("shows every bookable item by default when the period has no bookings", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendar();

    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByRole("button", { name: "View: By Item · Day" })).toBeVisible();
    expect(await screen.findByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });

  it("keeps the view and layout in the query string so a reload reopens them", async () => {
    const user = userEvent.setup();
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendarAt("/booking/calendar?layout=time-grid&view=week");

    expect(await screen.findByRole("button", { name: "View: Time grid · Week" })).toBeVisible();
    await chooseView(user, "Agenda");
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("layout")).toBe("agenda"));
    expect(new URLSearchParams(window.location.search).get("view")).toBe("week");
  });

  it("focuses the saved event occurrence in Agenda and removes the consumed request", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const { router } = await renderCalendarAt(
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", async () => {
        requestNumber += 1;
        if (requestNumber === 1) return HttpResponse.json(collectionResponse([]));
        refetchStarted();
        await refetchMayFinish;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );

    const { queryClient, router } = await renderCalendarAt(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda",
    );
    await waitFor(() => {
      const eventQueries = queryClient.getQueryCache().findAll({
        queryKey: ["api-v2", "bookings", "calendar-events"],
      });
      expect(eventQueries.some((query) => query.state.data !== undefined && query.state.fetchStatus === "idle")).toBe(
        true,
      );
    });

    act(() => {
      void queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
    });
    await refetchHasStarted;
    const cachedRefetch = queryClient
      .getQueryCache()
      .findAll({ queryKey: ["api-v2", "bookings", "calendar-events"] })
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-catalogue/calendar", () => HttpResponse.json(cataloguePage([]))),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([disabledBooking]))),
    );
    await renderCalendarAt(
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse(overlappingBookings))),
    );
    await renderCalendarAt(
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([multiDayBooking]))),
    );
    await renderCalendarAt(
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () =>
        HttpResponse.json(collectionResponse(available ? [ownBooking] : [])),
      ),
    );
    const { router } = await renderCalendarAt(
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
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    try {
      const { router } = await renderCalendarAt(
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking, second]))),
    );
    const { router } = await renderCalendarAt(
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", async () => {
        await held;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const { router } = await renderCalendarAt(
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", async () => {
        requestStarted();
        await releaseRequest;
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    const { router } = await renderCalendarAt(
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
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendarAt("/booking/calendar?view=month");

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

    expect(screen.getByRole("button", { name: "My Bookings" })).toHaveAccessibleDescription("Bookings you made");
    expect(screen.getByRole("button", { name: "Owned Items" })).toHaveAccessibleDescription(
      "Bookings on items you own",
    );
    await user.click(screen.getByRole("button", { name: /^Filters(?:$|,)/ }));
    expect(await screen.findByRole("button", { name: "Add filter" })).toBeVisible();
    expect(screen.getByRole("button", { name: /^Filters(?:$|,)/ })).toHaveAttribute("aria-expanded", "true");
  });

  it("shows quick filters that are on as removable chips", async () => {
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("region", { name: "Resource booking schedule" });

    await toggleQuickFilter(user, "My Bookings");
    expect(screen.getByRole("button", { name: "My Bookings" })).toHaveAttribute("aria-pressed", "true");
    await waitFor(() => expect(new URLSearchParams(window.location.search).has("mineOnly")).toBe(true));
    expect(screen.getByRole("button", { name: "Filters, none applied" })).toBeVisible();
    const remove = screen.getByRole("button", { name: "Remove My Bookings filter" });

    await user.click(remove);
    expect(screen.queryByRole("button", { name: "Remove My Bookings filter" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "My Bookings" })).toHaveAttribute("aria-pressed", "false");
    // The chip unmounts with its button, so focus moves to the Filters control rather than the page.
    expect(screen.getByRole("button", { name: "Filters, none applied" })).toHaveFocus();
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
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
              openDays: [2, 3, 4, 5, 6, 7],
              openingExceptions: [],
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where");
        catalogueWheres.push(where);
        const items = [catalogueItem(confocal, closedOnMonday), catalogueItem(electron, closedOnMonday)];
        return HttpResponse.json(cataloguePage(where === "target==IN123" ? items.slice(0, 1) : items));
      }),
    );
    await renderCalendarAt("/booking/calendar?date=2026-08-17&calendar-resources.where=target%3D%3DIN123");

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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", () =>
        HttpResponse.json(
          cataloguePage([catalogueItem(confocal, closedOnMonday), catalogueItem(electron, closedOnMonday)]),
        ),
      ),
    );
    await renderCalendarAt("/booking/calendar?date=2026-08-17&calendar-resources.where=target%3Din%3D(IN123%2CIN124)");

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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
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
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => {
        requests += 1;
        return requests === 1
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    const { router } = await renderCalendarAt(
      "/booking/calendar?date=2026-08-17&target=IN123&layout=agenda&focus=41&focusRequest=retry-focus",
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("Booking events are unavailable.");
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    const article = await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });
    expect(article).toBeVisible();
    await waitFor(() => expect(article.querySelector("button")).toHaveFocus());
    await waitFor(() => expect(router.state.location.searchStr).not.toContain("focusRequest="));
    expect(requests).toBe(2);
  });

  it("offers a calendar file from the event card only when the booking can be exported", async () => {
    const roleLost: BookingListDocument = { ...ownBooking, canViewConfiguration: false, canEdit: false };
    const showCalendarWith = async (booking: BookingListDocument) => {
      server.use(
        oauthTokenHandler(true),
        http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
        http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([booking]))),
      );
      await renderCalendar(`/booking/calendar?date=${booking.start.slice(0, 10)}`);
      await userEvent.setup().click(await screen.findByRole("button", { name: /^Show details for/ }));
    };

    await showCalendarWith(ownBooking);
    expect(await screen.findByRole("button", { name: /^\.ics file for Confocal microscope/ })).toBeVisible();

    cleanup();
    // The download endpoint requires the configuration read this row has lost.
    await showCalendarWith(roleLost);
    expect(await screen.findByRole("link", { name: "View details" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /^\.ics file for/ })).not.toBeInTheDocument();

    cleanup();
    await showCalendarWith(busyBooking);
    expect(screen.queryByRole("link", { name: "View details" })).not.toBeInTheDocument();
  });

  it.each([
    {
      kind: "booking",
      event: ownBooking,
      actorLabel: "Booked by",
      fieldLabel: "Purpose",
      originalValue: "Cell imaging",
      updatedValue: "Updated imaging purpose",
    },
    {
      kind: "maintenance",
      event: { ...ownBooking, kind: "MAINTENANCE" as const, createdBy: "Ada Lovelace", purpose: "Equipment service" },
      actorLabel: "Created by",
      fieldLabel: "Notes",
      originalValue: "Equipment service",
      updatedValue: "Updated maintenance notes",
    },
  ])(
    "hides read-only details while editing $kind events and restores them after cancel or save",
    async ({ event, actorLabel, fieldLabel, originalValue, updatedValue }) => {
      let serverBooking: BookingListDocument = event;
      server.use(
        oauthTokenHandler(true),
        http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
        http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([serverBooking]))),
        http.patch("/api/v2/bookings/41", async ({ request }) => {
          const patch = (await request.json()) as Partial<BookingListDocument>;
          serverBooking = { ...serverBooking, ...patch };
          return HttpResponse.json(serverBooking);
        }),
      );
      const user = userEvent.setup();
      await renderCalendar();

      await user.click(await screen.findByRole("button", { name: /^Show details for/ }));
      const dialog = await screen.findByRole("dialog");
      expect(within(dialog).getByText(actorLabel)).toBeVisible();
      expect(within(dialog).getByText(fieldLabel)).toBeVisible();

      await user.click(within(dialog).getByRole("button", { name: "Edit" }));
      const field = await within(dialog).findByRole("textbox", { name: fieldLabel });
      expect(field).toHaveValue(originalValue);
      expect(field).toHaveFocus();
      expect(within(dialog).queryByText(actorLabel)).not.toBeInTheDocument();
      expect(within(dialog).getAllByText(fieldLabel)).toHaveLength(1);
      expect(within(dialog).getByRole("heading")).toBeVisible();
      expect(within(dialog).getByRole("button", { name: /^Hide details for/ })).toBeVisible();
      await user.click(field);
      expect(field).toHaveFocus();

      await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
      expect(within(dialog).getByRole("button", { name: "Edit" })).toHaveFocus();
      expect(within(dialog).getByText(actorLabel)).toBeVisible();
      expect(within(dialog).getByText(fieldLabel)).toBeVisible();
      expect(within(dialog).getByText(originalValue)).toBeVisible();

      await user.click(within(dialog).getByRole("button", { name: "Edit" }));
      const editedField = await within(dialog).findByRole("textbox", { name: fieldLabel });
      await user.clear(editedField);
      await user.type(editedField, updatedValue);
      expect(within(dialog).queryByText(actorLabel)).not.toBeInTheDocument();
      expect(within(dialog).getAllByText(fieldLabel)).toHaveLength(1);
      await user.click(within(dialog).getByRole("button", { name: "Save changes" }));

      await waitFor(() => expect(within(dialog).queryByRole("textbox", { name: fieldLabel })).not.toBeInTheDocument());
      expect(within(dialog).getByRole("button", { name: "Edit" })).toHaveFocus();
      expect(within(dialog).getByText(actorLabel)).toBeVisible();
      expect(within(dialog).getByText(fieldLabel)).toBeVisible();
      expect(within(dialog).getByText(updatedValue)).toBeVisible();
    },
  );

  it("restores read-only details when edit permission is revoked during an inline edit", async () => {
    let serverBooking: BookingListDocument = ownBooking;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([serverBooking]))),
    );
    const user = userEvent.setup();
    const { queryClient } = await renderCalendar();

    await user.click((await screen.findAllByRole("button", { name: /^Show details for Confocal microscope/ }))[0]);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Edit" }));
    expect(await within(dialog).findByRole("textbox", { name: "Purpose" })).toBeVisible();

    serverBooking = { ...serverBooking, canEdit: false };
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings", "calendar-events"] });
    });

    await waitFor(() => expect(within(dialog).queryByRole("textbox", { name: "Purpose" })).not.toBeInTheDocument());
    expect(within(dialog).getByText("Booked by")).toBeVisible();
    expect(within(dialog).getByText("Purpose")).toBeVisible();
    expect(within(dialog).queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "View details" })).toHaveFocus();
  });

  it("explains a server buffer rejection in the inline calendar editor and lists the named booking", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json(
          {
            status: 409,
            code: "errors.api.v2.booking.buffer",
            detail: "private server detail",
            conflict: { id: 59, kind: "BOOKING", start: "2026-08-17T11:00:00Z", end: "2026-08-17T12:00:00Z" },
            bufferBeforeMinutes: 0,
            bufferAfterMinutes: 30,
          },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    await renderCalendar();

    await user.click((await screen.findAllByRole("button", { name: /^Show details for Confocal microscope/ }))[0]);
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.type(await screen.findByRole("textbox", { name: "Purpose" }), " updated");
    const save = screen.getByRole("button", { name: "Save changes" });
    await user.click(save);

    const alert = await screen.findByRole("alert", {}, { timeout: 3_000 });
    await waitFor(() => expect(alert).toHaveTextContent(/Too close to another booking/));
    expect(alert).not.toHaveTextContent("private server detail");
    expect(within(alert).getByRole("listitem")).toHaveTextContent("Booking #59");
    expect(save).toBeDisabled();
  });

  it("keeps an inline edit through a stale-version rejection and saves it against the refreshed version", async () => {
    let serverBooking: BookingListDocument = ownBooking;
    const saves: { ifMatch: string | null; payload: unknown }[] = [];
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([serverBooking]))),
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        const ifMatch = request.headers.get("If-Match");
        const payload = (await request.json()) as Partial<BookingListDocument>;
        saves.push({ ifMatch, payload });
        if (ifMatch !== `"${serverBooking.version}"`) {
          return HttpResponse.json(
            { status: 412, code: "errors.api.v2.preconditionFailed", detail: "private server detail" },
            { status: 412 },
          );
        }
        serverBooking = { ...serverBooking, ...payload, version: serverBooking.version + 1 };
        return HttpResponse.json(serverBooking);
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    await user.click((await screen.findAllByRole("button", { name: /^Show details for Confocal microscope/ }))[0]);
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.type(await screen.findByRole("textbox", { name: "Purpose" }), " updated");
    // Someone else edits the booking before this save reaches the server.
    serverBooking = { ...serverBooking, purpose: "Changed elsewhere", version: 1 };
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Someone else changed this booking while you were editing. Your changes are kept");
    expect(alert).not.toHaveTextContent("private server detail");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.getByRole("textbox", { name: "Purpose" })).toHaveValue("Cell imaging updated");
    await waitFor(() => expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled());

    // The explanation lasts while the draft is edited further; it is not the save's transient error.
    await user.type(screen.getByRole("textbox", { name: "Purpose" }), "!");
    expect(screen.getByRole("alert")).toHaveTextContent("Someone else changed this booking while you were editing.");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(saves.map(({ ifMatch }) => ifMatch)).toEqual(['"0"', '"1"']));
    expect(saves[1].payload).toEqual({ purpose: "Cell imaging updated!" });
    await waitFor(() => expect(screen.queryByRole("textbox", { name: "Purpose" })).not.toBeInTheDocument());
  });

  it("shows the matching purpose on collapsed Agenda cards while a search is applied", async () => {
    const auroraBooking: BookingListDocument = { ...ownBooking, purpose: "Spring Aurora imaging run" };
    const otherPurpose: BookingListDocument = {
      ...ownBooking,
      id: 46,
      start: "2026-08-18T08:00:00Z",
      end: "2026-08-18T09:00:00Z",
      purpose: "Calibration",
    };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () =>
        HttpResponse.json(collectionResponse([auroraBooking, otherPurpose, busyBooking])),
      ),
    );
    const user = userEvent.setup();
    await renderCalendarAt("/booking/calendar?date=2026-08-17&layout=agenda&view=week&calendar-resources.q=aurora");

    const agenda = await screen.findByRole("region", { name: "Booking agenda" });
    const highlight = await within(agenda).findByText("Aurora", { selector: "mark" });
    expect(highlight.closest("[data-calendar-search-match]")).toHaveTextContent("PurposeSpring Aurora imaging run");
    // Only the card whose purpose matched shows it; the others keep their purpose (or busy state) hidden.
    expect(within(agenda).getAllByRole("article")).toHaveLength(3);
    expect(agenda.querySelectorAll("[data-calendar-search-match]")).toHaveLength(1);
    expect(within(agenda).queryByText("Calibration")).not.toBeInTheDocument();

    await user.clear(screen.getByRole("textbox", { name: "Search Calendar" }));
    await waitFor(() => expect(agenda.querySelectorAll("[data-calendar-search-match]")).toHaveLength(0));
    expect(within(agenda).queryByText(/Spring Aurora imaging run/)).not.toBeInTheDocument();
  });

  it("uses one search for calendar events and bookable items", async () => {
    const catalogueSearches: string[] = [];
    server.use(
      http.get("/api/v2/openapi.json", () =>
        HttpResponse.json({ paths: { ...bookableItemsOpenApi.paths, ...bookingsOpenApi.paths } }),
      ),
      ...bookableItemsHandlers((request) => {
        const url = new URL(request.url);
        if (url.pathname === "/api/v2/booking-catalogue/calendar")
          catalogueSearches.push(url.searchParams.get("q") ?? "");
      }),
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    expect(screen.queryByRole("textbox", { name: "Search Bookable Items" })).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "Mass");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "View: By Item · Day" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    await waitFor(() => expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument());
    expect(await screen.findByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    expect(catalogueSearches.filter(Boolean)).toEqual(["Mass"]);
  });

  it("loads purpose-matching resources from server pagination rather than appending off-page events", async () => {
    const ownTarget = ownBooking.target;
    if (!ownTarget) throw new Error("The calendar fixture must include a target");
    const offPageEvent: BookingListDocument = {
      ...ownBooking,
      id: 99,
      target: {
        ...ownTarget,
        globalId: "IN999",
        value: { ...ownTarget.value, id: 999, name: "Off-page microscope" },
      },
      purpose: "QuasarPurposeMarker",
    };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const query = new URL(request.url).searchParams.get("q");
        const item = bookableItemFixtures[0];
        return HttpResponse.json({
          items: [
            {
              ...item,
              configurationId: item.id,
              targetId: query ? 999 : 123,
              targetType: "INSTRUMENT",
              globalId: query ? "IN999" : "IN123",
              name: query ? "Off-page microscope" : "Confocal microscope",
              location: null,
            },
          ],
          total: 1,
          page: 1,
          pageSize: 20,
          facets: { types: ["INSTRUMENT"] },
        });
      }),
      http.get("/api/v2/booking-calendar/events", ({ request }) => {
        const url = new URL(request.url);
        const matching = url.searchParams.get("q");
        if (matching) expect(url.searchParams.get("where")).toContain("target=in=(IN999)");
        return HttpResponse.json(collectionResponse([matching ? offPageEvent : ownBooking]));
      }),
    );

    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("region", { name: "Resource booking schedule" }, { timeout: 3_000 });
    const search = screen.getByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "QuasarPurposeMarker");

    expect(await screen.findByRole("article", { name: /Off-page microscope/ }, { timeout: 3_000 })).toBeVisible();
    expect(await screen.findByText("1–1 of 1 records")).toBeVisible();
    await user.clear(search);
    await user.type(search, "IN999");
    expect(await screen.findByRole("article", { name: /Off-page microscope/ }, { timeout: 3_000 })).toBeVisible();
  });

  it("keeps resource pagination when a search matches more than one page", async () => {
    const catalogueRequests: { query: string | null; page: number }[] = [];
    const catalogueItems = Array.from({ length: 21 }, (_, index) => {
      const fixture = bookableItemFixtures[index % bookableItemFixtures.length];
      return {
        configurationId: 100 + index,
        configurationVersion: fixture.configurationVersion,
        targetType: "INSTRUMENT",
        targetId: 1_000 + index,
        globalId: `IN${900 + index}`,
        name: `No-event microscope ${index + 1}`,
        timezone: fixture.timezone,
        slotGranularityMinutes: fixture.slotGranularityMinutes,
        openingStart: fixture.openingStart,
        openingEnd: fixture.openingEnd,
        openDays: fixture.openDays,
        openingExceptions: fixture.openingExceptions,
        bufferBeforeMinutes: fixture.bufferBeforeMinutes,
        bufferAfterMinutes: fixture.bufferAfterMinutes,
        maxBookingDurationMinutes: fixture.maxBookingDurationMinutes,
        allowDoubleBooking: fixture.allowDoubleBooking,
        effectiveRole: fixture.effectiveRole,
        capabilities: fixture.capabilities,
        location: null,
      };
    });
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", ({ request }) => {
        const url = new URL(request.url);
        const query = url.searchParams.get("q");
        const page = Number(url.searchParams.get("page") ?? "1");
        const pageSize = Number(url.searchParams.get("limit") ?? "20");
        catalogueRequests.push({ query, page });
        const start = (page - 1) * pageSize;
        return HttpResponse.json({
          items: catalogueItems.slice(start, start + pageSize),
          page,
          pageSize,
          total: catalogueItems.length,
          facets: { types: ["INSTRUMENT"] },
        });
      }),
    );

    const user = userEvent.setup();
    await renderCalendar();
    const search = await screen.findByRole("textbox", { name: "Search Calendar" });
    await user.type(search, "No-event");

    await waitFor(() => expect(catalogueRequests).toContainEqual({ query: "No-event", page: 1 }));
    expect(await screen.findByText("No-event microscope 1")).toBeVisible();
    expect(screen.getByText("1–20 of 21 records")).toBeVisible();
    const nextPage = screen.getByRole("button", { name: "Next page" });
    expect(nextPage).toBeEnabled();
    await user.click(nextPage);
    await waitFor(() => expect(catalogueRequests).toContainEqual({ query: "No-event", page: 2 }));
    await waitFor(() => expect(screen.getByText("No-event microscope 21")).toBeVisible());
  });

  it("shows an empty state when a calendar search has no matches", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );

    const user = userEvent.setup();
    await renderCalendar();
    const search = await screen.findByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "No calendar match");

    expect(await screen.findByText("No records found")).toBeVisible();
  });
});
