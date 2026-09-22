import { createMemoryHistory, createRootRoute, createRouter } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { createBookingRoute } from "../BookingPage";
import { createCalendarRoute } from "./routes";

async function matchedSearch(path: string) {
  const root = createRootRoute();
  const booking = createBookingRoute(root);
  const router = createRouter({
    routeTree: root.addChildren([booking.addChildren([createCalendarRoute(booking)])]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await router.load();
  return router.state.matches.at(-1)?.search;
}

describe("Calendar route", () => {
  it("keeps valid dates", async () => {
    await expect(matchedSearch("/booking/calendar?date=2026-08-17")).resolves.toEqual({ date: "2026-08-17" });
  });

  it("drops invalid dates while keeping unrelated filters", async () => {
    const search = await matchedSearch("/booking/calendar?date=2026-02-30&target=IN42");
    expect(search).toMatchObject({ target: "IN42" });
    expect(search).toHaveProperty("date", undefined);
  });
});
