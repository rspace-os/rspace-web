import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
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
import { bookingPagesHandlers } from "../../mocks/bookingPagesMocks";
import { busyBooking, collectionResponse, currentUser, ownBooking, renderCalendar } from "./calendarTestHarness";

const scrollToDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTo");

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

/** Turns a quick filter on or off in the Filters popover, then closes it. */
async function toggleQuickFilter(user: User, name: string) {
  await user.click(await screen.findByRole("button", { name: /^Filters(?:$|,)/ }));
  await user.click(await screen.findByRole("switch", { name }));
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("switch", { name })).not.toBeInTheDocument());
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
});

beforeEach(() => {
  server.use(...bookingPagesHandlers());
});

afterAll(() => {
  if (scrollToDescriptor) Object.defineProperty(HTMLElement.prototype, "scrollTo", scrollToDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
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
    expect(screen.getByRole("button", { name: "View: Resources · Day" })).toBeVisible();
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

  it("opens the week when a link asks for Month in Resources", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendarAt("/booking/calendar?view=month");

    expect(await screen.findByRole("button", { name: "View: Resources · Week" })).toBeVisible();
  });

  it("hides item filter controls and keeps Calendar controls available", async () => {
    const user = userEvent.setup();
    await renderCalendar();
    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /^Bookable items(?:,|$)/ })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Search Calendar" })).toBeVisible();
    for (const name of ["Jump to date", "View: Resources · Day"]) {
      expect(screen.getByRole("button", { name })).toBeVisible();
    }

    await user.click(screen.getByRole("button", { name: /^Filters(?:$|,)/ }));
    expect(await screen.findByRole("switch", { name: "My Bookings" })).toHaveAccessibleDescription("Bookings you made");
    expect(screen.getByRole("switch", { name: "Owned Items" })).toHaveAccessibleDescription(
      "Bookings on items you own",
    );
    expect(screen.getByRole("button", { name: "Edit filters" })).toBeVisible();
  });

  it("shows quick filters that are on as removable chips", async () => {
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("region", { name: "Resource booking schedule" });

    await toggleQuickFilter(user, "My Bookings");
    expect(screen.getByRole("button", { name: "Filters, 1 applied" })).toBeVisible();
    const remove = screen.getByRole("button", { name: "Remove My Bookings filter" });

    await user.click(remove);
    expect(screen.queryByRole("button", { name: "Remove My Bookings filter" })).not.toBeInTheDocument();
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

  it("explains why Month is unavailable in the Resources layout", async () => {
    const user = userEvent.setup();
    await renderCalendar();
    await user.click(await screen.findByRole("button", { name: "View: Resources · Day" }));
    const month = await screen.findByRole("menuitemradio", { name: "Month" });
    expect(month).toHaveAttribute("aria-disabled", "true");
    expect(month).toHaveAccessibleDescription(
      "Month isn't available in Resources. Use Time grid or Agenda for a month overview.",
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

  it("starts a booking for the one bookable item in scope from a Time grid drag", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendarAt("/booking/calendar?date=2026-08-17&target=IN123&layout=time-grid");

    const timeGrid = await screen.findByRole("region", { name: "Time grid" });
    expect(await within(timeGrid).findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    const canvas = within(timeGrid).getByTestId("day-timeline-canvas");
    await waitFor(() => expect(canvas).not.toHaveAttribute("data-creation-disabled"));
    // One pixel per minute: 10:00 to 11:30 on this (non-DST) day in the viewer's zone.
    vi.spyOn(canvas, "getBoundingClientRect").mockReturnValue({
      x: 0,
      y: 0,
      left: 0,
      top: 0,
      right: 1_440,
      bottom: 200,
      width: 1_440,
      height: 200,
      toJSON: () => ({}),
    });
    await user.pointer([
      { target: canvas, coords: { clientX: 600 } },
      { keys: "[MouseLeft>]", target: canvas },
      { target: canvas, coords: { clientX: 690 } },
      { keys: "[/MouseLeft]", target: canvas },
    ]);

    const dialog = await screen.findByRole("dialog", { name: "New Booking" });
    // The item comes from the Time grid's scope, so the form does not ask for one.
    expect(within(dialog).queryByRole("button", { name: "Choose a bookable item" })).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText("Start time")).toHaveValue("10:00");
    expect(within(dialog).getByLabelText("End time")).toHaveValue("11:30");
    expect(canvas).toHaveAttribute("data-creation-disabled", "true");
  });

  it("offers no Time grid creation to a viewer who cannot book the one item in scope", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue/calendar", () =>
        HttpResponse.json(
          cataloguePage([
            catalogueItem(bookableItemFixtures[0], { capabilities: readOnlyCapabilities, effectiveRole: "VIEWER" }),
          ]),
        ),
      ),
    );
    await renderCalendarAt("/booking/calendar?date=2026-08-17&target=IN123&layout=time-grid");

    const timeGrid = await screen.findByRole("region", { name: "Time grid" });
    expect(await within(timeGrid).findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
    await waitFor(() => expect(timeGrid).toHaveAttribute("aria-busy", "false"));
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
    await renderCalendar();

    expect(await screen.findByRole("alert")).toHaveTextContent("Booking events are unavailable.");
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ })).toBeVisible();
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

  it("discards a stale inline edit and loads the latest version of the booking", async () => {
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
          return HttpResponse.json({ status: 412, code: "errors.api.v2.preconditionFailed" }, { status: 412 });
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
    serverBooking = { ...serverBooking, purpose: "Changed elsewhere", version: 1 };
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    const discard = await screen.findByRole("button", { name: "Discard my changes and load the latest" });
    await waitFor(() => expect(discard).toBeEnabled());
    await user.click(discard);

    const purpose = await screen.findByRole("textbox", { name: "Purpose" });
    await waitFor(() => expect(purpose).toHaveValue("Changed elsewhere"));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await user.type(purpose, " again");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(saves.map(({ ifMatch }) => ifMatch)).toEqual(['"0"', '"1"']));
    expect(saves[1].payload).toEqual({ purpose: "Changed elsewhere again" });
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
      http.get("/api/v2/openapi.json", () =>
        HttpResponse.json({ paths: { ...bookableItemsOpenApi.paths, ...bookingsOpenApi.paths } }),
      ),
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
    expect(screen.getByRole("button", { name: "View: Resources · Day" })).toBeVisible();
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
        const page = Number(url.searchParams.get("page") ?? "1");
        const pageSize = Number(url.searchParams.get("limit") ?? "20");
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

    expect(await screen.findByText("No-event microscope 1")).toBeVisible();
    expect(screen.getByText("1–20 of 21 records")).toBeVisible();
    const nextPage = screen.getByRole("button", { name: "Next page" });
    expect(nextPage).toBeEnabled();
    await user.click(nextPage);
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
