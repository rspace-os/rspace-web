import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookableItemsHandlers, bookableItemsOpenApi } from "../../bookable-items/mocks/bookableItemsMocks";
import { collectionResponse, ownBooking } from "../../mocks/bookingCalendarFixtures";
import { bookingsOpenApi } from "../../my-bookings/mocks/bookingMocks";
import { restoreCalendarScrollTo, setupCalendarBookableItems, setupCalendarScrollTo } from "./calendarPageTestUtils";
import { renderCalendar } from "./calendarTestHarness";

type User = ReturnType<typeof userEvent.setup>;

/** Turns a quick filter on or off in the Filters menu, then closes it. */
async function toggleQuickFilter(user: User, name: string) {
  await user.click(await screen.findByRole("button", { name: /^Filters(?:$|,)/ }));
  await user.click(await screen.findByRole("switch", { name }));
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("switch", { name })).not.toBeInTheDocument());
}

beforeAll(() => setupCalendarScrollTo());
beforeEach(() => setupCalendarBookableItems());
afterAll(() => restoreCalendarScrollTo());

describe("CalendarPage", () => {
  it("preselects the filtered item for a new maintenance event", async () => {
    const user = userEvent.setup();
    await renderCalendar("/booking/calendar?date=2026-08-17&target=IN123");
    await screen.findByRole("region", { name: "Resource booking schedule" });
    await user.click(await screen.findByRole("button", { name: "More event creation options" }));
    await user.click(await screen.findByRole("menuitem", { name: "New Maintenance Event" }));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByRole("combobox", { name: "Bookable item" })).toHaveValue(
      "Confocal microscope (IN123)",
    );
  });

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

    await toggleQuickFilter(user, "Owned Items");

    await waitFor(() => {
      expect(catalogueRequests.at(-1)?.searchParams.get("mine")).toBe("true");
      expect(eventRequests.some((request) => request.searchParams.get("mine") === "true")).toBe(true);
      expect(new URLSearchParams(window.location.search).has("myItemsOnly")).toBe(true);
    });
  });

  it("shows every bookable item by default when the period has no bookings", async () => {
    server.use(http.get("/api/v2/booking-calendar/events", () => HttpResponse.json(collectionResponse([]))));

    await renderCalendar();

    expect(await screen.findByRole("region", { name: "Resource booking schedule" })).toBeVisible();
    expect(screen.getByRole("button", { name: "View: By Item · Day" })).toBeVisible();
    expect(await screen.findByText("Mass spectrometer")).toBeVisible();
    expect(screen.queryByText("No records found")).not.toBeInTheDocument();
  });
});
