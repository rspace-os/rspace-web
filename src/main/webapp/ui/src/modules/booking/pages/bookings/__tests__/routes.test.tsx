import { createMemoryHistory, createRootRoute, createRouter } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { createBookingRoute } from "@/modules/booking/pages/BookingPage";
import { bookingFormSearch, createAddBookingRoute, createEditBookingRoute } from "../routes";

async function matchedSearch(path: string) {
  const root = createRootRoute();
  const booking = createBookingRoute(root);
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([createAddBookingRoute(booking), createEditBookingRoute(booking)]),
    ]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await router.load();
  return router.state.matches.at(-1)?.search;
}

describe("booking form routes", () => {
  it("keeps valid Calendar return state", async () => {
    await expect(matchedSearch("/booking/calendar/bookings/add?date=2026-10-25&target=IN42")).resolves.toEqual({
      date: "2026-10-25",
      target: "IN42",
    });
    await expect(matchedSearch("/booking/calendar/bookings/41?date=2026-10-25&target=IN42")).resolves.toEqual({
      date: "2026-10-25",
      target: "IN42",
    });
  });

  it("drops invalid dates and targets", async () => {
    expect(bookingFormSearch({ date: "2026-02-30", target: "SA42" })).toEqual({});
  });
});
