import { createMemoryHistory, createRootRoute, createRoute, createRouter } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { createMyBookingsRoute } from "../routes";

async function matchedSearch(path: string) {
  const root = createRootRoute();
  const booking = createRoute({ getParentRoute: () => root, path: "/booking" });
  const router = createRouter({
    routeTree: root.addChildren([booking.addChildren([createMyBookingsRoute(booking)])]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await router.load();
  return router.state.matches.at(-1)?.search;
}

describe("My Bookings route", () => {
  it("defaults absent and invalid periods to upcoming", async () => {
    await expect(matchedSearch("/booking/my-bookings")).resolves.toEqual({ period: "upcoming" });
    await expect(matchedSearch("/booking/my-bookings?period=later")).resolves.toEqual({ period: "upcoming" });
  });

  it("keeps the past period", async () => {
    await expect(
      matchedSearch("/booking/my-bookings?period=past&my-bookings.q=scope&unrelated=discarded"),
    ).resolves.toEqual({ period: "past", "my-bookings.q": "scope", unrelated: "discarded" });
  });
});
