import "@/__tests__/__mocks__/matchMedia";
import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookableItemFixtures, bookableItemsHandlers } from "../../bookable-items/mocks/bookableItemsMocks";
import { busyBooking, collectionResponse, currentUser, ownBooking, renderCalendar } from "./calendarTestHarness";

const scrollToDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTo");

beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, "scrollTo", { configurable: true, value: vi.fn() });
});

beforeEach(() => {
  server.use(...bookableItemsHandlers(() => undefined));
});

afterAll(() => {
  if (scrollToDescriptor) Object.defineProperty(HTMLElement.prototype, "scrollTo", scrollToDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
});

describe("CalendarPage", () => {
  it("shows every bookable item by default when the period has no bookings", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([]))),
    );

    await renderCalendar();

    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Resources" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Day" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });

  it("keeps viewer events visible while disabling resource creation", async () => {
    const item = bookableItemFixtures[0];
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue", () =>
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

  it("offers a retry when booking events cannot be loaded", async () => {
    let requests = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => {
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
        http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([booking]))),
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

  it("uses one search for calendar events and bookable items", async () => {
    const catalogueSearches: string[] = [];
    server.use(
      ...bookableItemsHandlers((request) => {
        const url = new URL(request.url);
        if (url.pathname === "/api/v2/booking-catalogue") catalogueSearches.push(url.searchParams.get("q") ?? "");
      }),
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    expect(screen.queryByRole("textbox", { name: "Search Bookable Items" })).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "Mass");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Time grid" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    await waitFor(() => expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument());
    expect(screen.getByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
    expect(catalogueSearches.filter(Boolean)).toEqual(["Mass"]);
  });

  it("keeps event matches from resources outside the current catalogue page", async () => {
    const offPageEvent: BookingListDocument = {
      ...ownBooking,
      id: 99,
      target: {
        ...ownBooking.target,
        globalId: "IN999",
        value: { ...ownBooking.target.value, id: 999, name: "Off-page microscope" },
      },
      purpose: "QuasarPurposeMarker",
    };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", ({ request }) => {
        const where = new URL(request.url).searchParams.get("where") ?? "";
        return HttpResponse.json(
          collectionResponse(where.includes("target=in=") ? [ownBooking] : [ownBooking, offPageEvent]),
        );
      }),
    );

    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("region", { name: "Resource booking schedule" }, { timeout: 3_000 });
    const search = screen.getByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "QuasarPurposeMarker");

    expect(await screen.findByRole("article", { name: /Off-page microscope/ }, { timeout: 3_000 })).toBeVisible();
    expect(await screen.findByText(/additional resource with a matching event/)).toBeVisible();
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
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
      http.get("/api/v2/booking-catalogue", ({ request }) => {
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
    expect(await screen.findByText("No-event microscope 21")).toBeVisible();
  });

  it("shows an empty state when a calendar search has no matches", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );

    const user = userEvent.setup();
    await renderCalendar();
    const search = await screen.findByRole("textbox", { name: "Search Calendar" });
    await user.clear(search);
    await user.type(search, "No calendar match");

    expect(await screen.findByText("No records found")).toBeVisible();
  });
});
