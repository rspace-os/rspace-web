import "@/__tests__/__mocks__/matchMedia";
import { createMemoryHistory } from "@tanstack/react-router";
import { describe, expect, it, vi } from "vitest";

vi.mock("@scalar/api-reference-react", () => ({ ApiReferenceReact: () => null }));

describe("production router servlet context", () => {
  it.each(["", "/rspace"])("matches entry URLs and keeps navigation inside context %s", async (contextPath) => {
    vi.resetModules();
    const appElement = document.createElement("div");
    appElement.id = "app";
    appElement.dataset.contextPath = contextPath;
    document.body.append(appElement);
    try {
      const { router } = await import("../router");
      router.history.destroy();
      const history = createMemoryHistory({ initialEntries: [`${contextPath}/public/apiDocs`] });
      router.update({ history });
      try {
        await router.load();
        expect(router.state.matches.map((match) => match.routeId)).toEqual(["__root__", "/public/apiDocs"]);

        for (const path of ["/about", "/public/maintenanceInProgress"] as const) {
          await router.navigate({ to: path });
          expect(history.location.pathname).toBe(`${contextPath}${path}`);
          expect(router.state.matches.map((match) => match.routeId)).toEqual(["__root__", path]);
        }
      } finally {
        history.destroy();
      }
    } finally {
      appElement.remove();
      vi.resetModules();
    }
  });
});
