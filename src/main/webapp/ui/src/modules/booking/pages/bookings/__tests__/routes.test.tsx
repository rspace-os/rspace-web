import { createRootRoute } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { createBookingRoute } from "@/modules/booking/pages/BookingPage";
import { bookingFormSearch } from "../../bookingFormSearch";
import { matchedRoute } from "../../routeTestHelpers";
import { createAddBookingRoute, createBookingEventRouteTree } from "../routes";

async function matched(path: string) {
  const root = createRootRoute();
  const booking = createBookingRoute(root);
  return matchedRoute(
    root.addChildren([booking.addChildren([createAddBookingRoute(booking), createBookingEventRouteTree(booking)])]),
    path,
  );
}

describe("booking form routes", () => {
  it("keeps valid Calendar return state", async () => {
    await expect(matched("/booking/calendar/bookings/add?date=2026-10-25&target=IN42")).resolves.toMatchObject({
      search: { date: "2026-10-25", target: "IN42" },
    });
  });

  it("drops invalid dates and targets", async () => {
    expect(bookingFormSearch({ date: "2026-02-30", target: "SA42" })).toEqual({ date: undefined });
  });

  it("drops invalid dates while keeping a valid target", async () => {
    const { search } = await matched("/booking/calendar/bookings/add?date=2026-02-30&target=IN42");
    expect(search).toMatchObject({ target: "IN42" });
    expect(search).toHaveProperty("date", undefined);
  });

  it.each([
    ["/booking/calendar/bookings/41", "/booking/calendar/bookings/$id/"],
    ["/booking/calendar/bookings/41/edit", "/booking/calendar/bookings/$id/edit"],
  ])("matches %s to its own child route", async (path, routeId) => {
    await expect(matched(path)).resolves.toMatchObject({ params: { id: "41" }, routeId, search: {} });
  });
});
