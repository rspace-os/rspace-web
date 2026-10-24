import "@/__tests__/__mocks__/matchMedia";
import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookableItemsHandlers, bookableItemsOpenApi } from "../../bookable-items/mocks/bookableItemsMocks";
import { busyBooking, collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import {
  renderCalendar,
  restoreCalendarScrollTo,
  setupCalendarBookableItems,
  setupCalendarScrollTo,
} from "./calendarPageTestUtils";

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

describe("CalendarPage", () => {
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

    expect(await screen.findByRole("alert")).toHaveTextContent("Booking events are unavailable.");
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
