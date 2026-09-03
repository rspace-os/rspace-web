import "@/__tests__/__mocks__/matchMedia";
import { cleanup, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { bookableItemsHandlers } from "../../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, currentUser, ownBooking, renderCalendar } from "./calendarTestHarness";

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
      oauthTokenHandler(),
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

  it("offers a retry when booking events cannot be loaded", async () => {
    let requests = 0;
    server.use(
      oauthTokenHandler(),
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
        oauthTokenHandler(),
        http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
        http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([booking]))),
      );
      await renderCalendar();
      await userEvent
        .setup()
        .click(await screen.findByRole("button", { name: /^Show details for Confocal microscope/ }));
    };

    await showCalendarWith(ownBooking);
    expect(await screen.findByRole("button", { name: /^\.ics file for Confocal microscope/ })).toBeVisible();

    cleanup();
    // The download endpoint requires the configuration read this row has lost.
    await showCalendarWith(roleLost);
    expect(await screen.findByRole("link", { name: "View details" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /^\.ics file for/ })).not.toBeInTheDocument();
  });

  it("keeps bookable items and calendar controls when booking search has no matches", async () => {
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/bookings", () => HttpResponse.json(collectionResponse([ownBooking]))),
    );
    const user = userEvent.setup();
    await renderCalendar();
    await screen.findByRole("article", { name: /Confocal microscope · Ada Lovelace/ });

    await user.type(screen.getByRole("textbox", { name: "Search Calendar" }), "no matching booking");

    expect(screen.getByRole("button", { name: "Jump to date" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Time grid" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });
});
