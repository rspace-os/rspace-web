import { createMemoryHistory, createRootRouteWithContext, createRoute, createRouter } from "@tanstack/react-router";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2SessionEndedError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { ApiV2IdentityChangedError, SessionTokenProvider } from "@/modules/common/api/v2/tokenProvider";
import type { AppBarRouteContext } from "../AppShell";
import { appShellSessionHandlers } from "../sessionHandlers";

// Relative fetch URLs still resolve against the stubbed location.
const location = { href: window.location.href, origin: window.location.origin, assign: vi.fn(), reload: vi.fn() };
const token = (hash: string) =>
  `e30.${btoa(JSON.stringify({ exp: Date.now() / 1000 + 1000, refreshTokenHash: hash }))}.x`;

beforeEach(() => {
  vi.stubGlobal("location", location);
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetAllMocks();
});

async function routerAt(path: string) {
  const root = createRootRouteWithContext<AppBarRouteContext>()({});
  const routes = [
    createRoute({
      getParentRoute: () => root,
      path: "/workspace",
      beforeLoad: () => ({ appBar: { currentPage: "workspace" } }) satisfies AppBarRouteContext,
    }),
    createRoute({
      getParentRoute: () => root,
      path: "/about",
      beforeLoad: () => ({ appBar: { currentPage: "aboutRSpace", authenticated: false } }) satisfies AppBarRouteContext,
    }),
    createRoute({
      getParentRoute: () => root,
      path: "/apiDocs",
      beforeLoad: () => ({ appBar: false }) satisfies AppBarRouteContext,
    }),
    createRoute({ getParentRoute: () => root, path: "/bare" }),
  ];
  const router = createRouter({
    routeTree: root.addChildren(routes),
    context: {},
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await router.load();
  return router;
}

const refuseTokens = () =>
  server.use(
    http.post("/api/v2/oauth/tokens", () =>
      HttpResponse.json(
        { code: "unauthenticated", status: 401, title: "Unauthorized" },
        { status: 401, headers: { "Content-Type": "application/problem+json" } },
      ),
    ),
  );

it("navigates to /login when the session ends on a route that needs authentication", async () => {
  refuseTokens();
  const provider = new SessionTokenProvider(appShellSessionHandlers(await routerAt("/workspace")));
  await expect(provider.getToken()).rejects.toBeInstanceOf(ApiV2SessionEndedError);
  expect(location.assign).toHaveBeenCalledExactlyOnceWith("/login");
});

it.each(["/about", "/apiDocs", "/bare"])(
  "does not navigate when the session ends on auth-optional %s",
  async (path) => {
    refuseTokens();
    const provider = new SessionTokenProvider(appShellSessionHandlers(await routerAt(path)));
    await expect(provider.getToken()).rejects.toBeInstanceOf(ApiV2SessionEndedError);
    expect(location.assign).not.toHaveBeenCalled();
  },
);

it("reloads when the identity changes", async () => {
  let minted = 0;
  server.use(http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: token(`${++minted}`) })));
  const provider = new SessionTokenProvider(appShellSessionHandlers(await routerAt("/about")));
  const first = await provider.getToken();
  await expect(provider.recover(first)).rejects.toBeInstanceOf(ApiV2IdentityChangedError);
  expect(location.reload).toHaveBeenCalledOnce();
});
