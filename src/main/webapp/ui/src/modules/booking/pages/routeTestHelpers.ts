import { type AnyRoute, createMemoryHistory, createRouter } from "@tanstack/react-router";

export async function matchedRoute(routeTree: AnyRoute, path: string) {
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await router.load();
  const match = router.state.matches.at(-1);
  return { params: match?.params, routeId: match?.routeId, search: match?.search };
}
