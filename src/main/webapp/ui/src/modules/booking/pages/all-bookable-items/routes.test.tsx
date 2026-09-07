import { createMemoryHistory, createRootRoute, createRoute, createRouter } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { allBookableItemsSearch, createAllBookableItemsRoute } from "./routes";

async function matchedSearch(path: string) {
  const root = createRootRoute();
  const booking = createRoute({ getParentRoute: () => root, path: "/booking" });
  const router = createRouter({
    routeTree: root.addChildren([booking.addChildren([createAllBookableItemsRoute(booking)])]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await router.load();
  return router.state.matches.at(-1)?.search;
}

describe("All Bookable Items route", () => {
  it.each(["available-now", "free-later-today"])('keeps the valid availability mode "%s"', async (availability) => {
    await expect(matchedSearch(`/booking/all-items?date=2026-08-17&availability=${availability}`)).resolves.toEqual({
      date: "2026-08-17",
      availability,
    });
  });

  it("drops absent and invalid availability modes", () => {
    expect(allBookableItemsSearch({ date: "2026-08-17" })).toEqual({ date: "2026-08-17" });
    expect(allBookableItemsSearch({ date: "2026-08-17", availability: "tomorrow" })).toEqual({
      date: "2026-08-17",
    });
  });

  it("drops the removed location filter from the URL", () => {
    expect(allBookableItemsSearch({ date: "2026-08-17", locations: ["IC456"] })).toEqual({
      date: "2026-08-17",
    });
  });
});
