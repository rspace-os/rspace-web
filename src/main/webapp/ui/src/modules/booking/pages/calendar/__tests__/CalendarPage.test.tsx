import "@/__tests__/__mocks__/matchMedia";
import { cleanup, screen, waitFor, within } from "@testing-library/react";
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
import { busyBooking, collectionResponse, otherBooking, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import {
  renderCalendar,
  restoreCalendarScrollTo,
  setupCalendarBookableItems,
  setupCalendarScrollTo,
} from "./calendarPageTestUtils";
import { currentUser } from "./calendarTestHarness";

// 2026-08-17 is a Monday.
const closedOnMonday = { openingHours: bookableItemFixtures[0].openingHours.filter((entry) => entry.dayOfWeek !== 1) };
const readOnlyCapabilities = {
  ...bookableItemFixtures[0].capabilities,
  canCreateBooking: false,
  canEditConfiguration: false,
};

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
    await renderCalendar("/booking/calendar?date=2026-08-17");
    await user.click(await screen.findByRole("button", { name: "Week" }));
    if (layout !== "resources")
      await user.click(screen.getByRole("button", { name: layout === "agenda" ? "Agenda" : "Time grid" }));
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
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendarAt("/booking/calendar?layout=time-grid&view=week");

    expect(await screen.findByRole("button", { name: "Time grid" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Week" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Agenda" }));
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("layout")).toBe("agenda"));
    expect(new URLSearchParams(window.location.search).get("view")).toBe("week");
  });

  it("opens the week when a link asks for Month in Resources", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendarAt("/booking/calendar?view=month");

    expect(await screen.findByRole("button", { name: "Resources" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Week" })).toHaveAttribute("aria-pressed", "true");
  });

  it("hides item filter controls and keeps Calendar controls available", async () => {
    await renderCalendar();
    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /^Bookable items(?:,|$)/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Filters(?:$|,)/ })).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Search Calendar" })).toBeVisible();
    for (const name of ["Jump to date", "Time grid", "Day", "Resources", "My Bookings", "Owned Items"]) {
      expect(screen.getByRole("button", { name: new RegExp(`^${name}$`) })).toBeVisible();
    }
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
      expect(screen.getByRole("button", { name: "Day" })).toHaveAttribute("aria-pressed", "true");
      expect(screen.getByRole("button", { name: "Resources" })).toHaveAttribute("aria-pressed", "true");
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
      expect(screen.getByRole("button", { name: "Day" })).toHaveAttribute("aria-pressed", "true");
      expect(screen.getByRole("button", { name: "Resources" })).toHaveAttribute("aria-pressed", "true");
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

  it("explains why Month is unavailable in the Resources layout", async () => {
    await renderCalendar();
    const month = await screen.findByRole("button", { name: "Month" });
    expect(month).toBeDisabled();
    expect(month).toHaveAccessibleDescription(
      "Month isn't available in Resources. Use Time grid or Agenda for a month overview.",
    );

    await userEvent.setup().click(screen.getByRole("button", { name: "Agenda" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Month" })).toBeEnabled());
    expect(screen.getByRole("button", { name: "Month" })).not.toHaveAccessibleDescription();
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

  it("shades a closed weekday for read-only viewers in the Resources and Time grid day views", async () => {
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
              openingHours: item.openingHours.filter((entry) => [2, 3, 4, 5, 6, 7].includes(entry.dayOfWeek)),
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

    await userEvent.setup().click(screen.getByRole("button", { name: "Time grid" }));
    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    expect(screen.getByTestId("day-timeline-closed-hours")).toHaveStyle({ left: "0%", width: "100%" });
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

    await userEvent.setup().click(screen.getByRole("button", { name: "Time grid" }));
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

    await userEvent.setup().click(screen.getByRole("button", { name: "Time grid" }));
    const timeGrid = await screen.findByRole("region", { name: "Time grid" });
    expect(await within(timeGrid).findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    await waitFor(() => expect(timeGrid).toHaveAttribute("aria-busy", "false"));
    expect(screen.queryAllByTestId("day-timeline-closed-hours")).toHaveLength(0);
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
      http.get("/api/v2/booking-calendar/events", () => {
        requests += 1;
        return requests === 1
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    expect(await screen.findByRole("alert")).toMatchTextContent("Booking events are unavailable.");
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
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
      await userEvent.setup().click(await screen.findByRole("button", { name: "Time grid" }));
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
