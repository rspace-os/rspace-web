import { createMemoryHistory, createRootRoute, createRouter } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { createBookingRoute } from "@/modules/booking/pages/BookingPage";
import { bookingFormSearch, createAddBookingRoute, createBookingEventRouteTree } from "../routes";

async function matched(path: string) {
  const root = createRootRoute();
  const booking = createBookingRoute(root);
  const router = createRouter({
    routeTree: root.addChildren([
      booking.addChildren([createAddBookingRoute(booking), createBookingEventRouteTree(booking)]),
    ]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await router.load();
  const match = router.state.matches.at(-1);
  return { params: match?.params, routeId: match?.routeId, search: match?.search };
}

describe("booking form routes", () => {
  it("keeps valid Calendar return state", async () => {
    await expect(matched("/booking/calendar/bookings/add?date=2026-10-25&target=IN42")).resolves.toMatchObject({
      search: { date: "2026-10-25", target: "IN42" },
    });
  });

  it("drops invalid dates and targets", async () => {
    expect(bookingFormSearch({ date: "2026-02-30", target: "SA42" })).toEqual({});
  });

  it.each([
    ["/booking/calendar/bookings/41", "/booking/calendar/bookings/$id/"],
    ["/booking/calendar/bookings/41/edit", "/booking/calendar/bookings/$id/edit"],
  ])("matches %s to its own child route", async (path, routeId) => {
    await expect(matched(path)).resolves.toMatchObject({ params: { id: "41" }, routeId, search: {} });
  });
});
