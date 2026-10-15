import { createRootRoute, createRoute } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { matchedRoute } from "../../routeTestHelpers";
import { createMyBookingsRoute } from "../routes";

async function matchedSearch(path: string) {
  const root = createRootRoute();
  const booking = createRoute({ getParentRoute: () => root, path: "/booking" });
  const match = await matchedRoute(root.addChildren([booking.addChildren([createMyBookingsRoute(booking)])]), path);
  return match.search;
}

describe("My Bookings route", () => {
  it("defaults absent and invalid periods to upcoming", async () => {
    await expect(matchedSearch("/booking/my-bookings")).resolves.toEqual({ period: "upcoming" });
    await expect(matchedSearch("/booking/my-bookings?period=later")).resolves.toEqual({ period: "upcoming" });
  });

  it.each([
    [
      "past",
      "/booking/my-bookings?period=past&my-bookings.q=scope&unrelated=discarded",
      {
        period: "past",
        "my-bookings.q": "scope",
        unrelated: "discarded",
      },
    ],
    ["cancelled", "/booking/my-bookings?period=cancelled", { period: "cancelled" }],
  ] as const)("keeps the %s period", async (_period, path, expected) => {
    await expect(matchedSearch(path)).resolves.toEqual(expected);
  });
});
