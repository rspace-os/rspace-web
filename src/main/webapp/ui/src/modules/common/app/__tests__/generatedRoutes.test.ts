import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { routeTree } from "@/routeTree.gen";

function routerAt(path: string, basepath = "/") {
  return createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [path] }),
    basepath,
  });
}

describe("generated application routes", () => {
  it.each([
    ["/about", "/about/", "__root__"],
    ["/public/maintenanceInProgress", "/public/maintenanceInProgress/", "__root__"],
    ["/public/apiDocs", "/public/apiDocs/", "__root__"],
    ["/booking", "/booking/", "/booking"],
    ["/booking/calendar", "/booking/calendar/", "/booking"],
    ["/booking/calendar/bookings/add", "/booking/calendar/bookings/add/", "/booking"],
    ["/booking/calendar/bookings/41", "/booking/calendar/bookings/$id/", "/booking/calendar/bookings/$id"],
    ["/booking/calendar/bookings/41/edit", "/booking/calendar/bookings/$id/edit/", "/booking/calendar/bookings/$id"],
    ["/booking/my-bookings", "/booking/my-bookings/", "/booking"],
    ["/booking/preferences", "/booking/preferences/", "/booking"],
    ["/booking/config/bookable-items", "/booking/config/bookable-items/", "/booking"],
    ["/booking/config/settings", "/booking/config/settings/", "/booking"],
    ["/booking/bookable-items/add", "/booking/bookable-items/add/", "/booking"],
    ["/booking/bookable-items/IN42", "/booking/bookable-items/$globalId/{-$tab}/", "/booking"],
    ["/booking/bookable-items/IN42/availability", "/booking/bookable-items/$globalId/{-$tab}/", "/booking"],
  ])("matches %s with its intended layout", (path, routeId, parentId) => {
    const router = routerAt(path);
    const matches = router.matchRoutes(path);
    expect(matches.at(-1)?.routeId).toBe(routeId);
    expect(matches.at(-2)?.routeId).toBe(parentId);
    expect(router.matchRoutes(`${path}/`).at(-1)?.routeId).toBe(routeId);
  });

  it("keeps event pages outside the Calendar page and its search validator", async () => {
    const router = routerAt("/booking/calendar/bookings/41/edit");
    await router.load();
    expect(router.state.matches.map((match) => match.routeId)).toEqual([
      "__root__",
      "/booking",
      "/booking/calendar/bookings/$id",
      "/booking/calendar/bookings/$id/edit/",
    ]);
    expect(router.state.matches.at(-1)?.search).toEqual({});
    expect(router.state.matches.at(-1)?.params).toMatchObject({ id: "41" });
    expect(router.state.matches.at(-1)?.context).toMatchObject({ appBar: { currentPage: "booking" } });
  });

  it.each([
    ["/booking/calendar?date=2026-02-30&target=IN42", { date: undefined, target: "IN42" }],
    ["/booking/calendar/bookings/add?date=2026-10-25&target=IN42", { date: "2026-10-25", target: "IN42" }],
    ["/booking/bookable-items/add?target=IN42", { target: "IN42" }],
    ["/booking/bookable-items/IN42?edit=true", { edit: true }],
    ["/booking/my-bookings?period=invalid", { period: "upcoming" }],
  ])("attaches search validation to %s", async (path, search) => {
    const router = routerAt(path);
    await router.load();
    expect(router.state.matches.at(-1)?.search).toMatchObject(search);
  });

  it.each(["/unknown", "/booking/unknown", "/booking/calendar/bookings/41/unknown"])(
    "marks an unknown path as not found: %s",
    async (path) => {
      const router = routerAt(path);
      await router.load();
      expect(router.state.matches.some((match) => match.globalNotFound || match.status === "notFound")).toBe(true);
    },
  );

  it("matches dynamic and optional parameters under a deployment context path", async () => {
    const router = routerAt("/rspace/booking/bookable-items/IN42/availability", "/rspace");
    await router.load();
    const match = router.state.matches.at(-1);
    expect(match?.routeId).toBe("/booking/bookable-items/$globalId/{-$tab}/");
    expect(match?.params).toMatchObject({ globalId: "IN42", tab: "availability" });
  });
});
