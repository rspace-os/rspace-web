import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookableItemsHandlers, bookableItemsOpenApi } from "../../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import { restoreCalendarScrollTo, setupCalendarBookableItems, setupCalendarScrollTo } from "./calendarPageTestUtils";
import { renderCalendar } from "./calendarTestHarness";

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

describe("CalendarPage", () => {
  it("filters calendar resources and events to Owned Items", async () => {
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
        return HttpResponse.json(collectionResponse([ownBooking]));
      }),
    );
    const user = userEvent.setup();
    await renderCalendar();

    await user.click(await screen.findByRole("button", { name: "Owned Items" }));

    await waitFor(() => {
      expect(catalogueRequests.at(-1)?.searchParams.get("mine")).toBe("true");
      expect(eventRequests.some((request) => request.searchParams.get("mine") === "true")).toBe(true);
    });
  });

  it("shows every bookable item by default when the period has no bookings", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))));

    await renderCalendar();

    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Resources" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Day" })).toHaveAttribute("aria-pressed", "true");
    expect(await screen.findByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });
});
