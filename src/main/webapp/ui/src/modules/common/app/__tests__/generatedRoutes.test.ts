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
    ["/booking/preferences", "/booking/preferences/", "/booking"],
    ["/booking/config/settings", "/booking/config/settings/", "/booking"],
  ])("matches %s with its intended layout", (path, routeId, parentId) => {
    const router = routerAt(path);
    const matches = router.matchRoutes(path);
    expect(matches.at(-1)?.routeId).toBe(routeId);
    expect(matches.at(-2)?.routeId).toBe(parentId);
    expect(router.matchRoutes(`${path}/`).at(-1)?.routeId).toBe(routeId);
  });

  it.each(["/unknown", "/booking/unknown"])("marks an unknown path as not found: %s", async (path) => {
    const router = routerAt(path);
    await router.load();
    expect(router.state.matches.some((match) => match.globalNotFound || match.status === "notFound")).toBe(true);
  });
});
