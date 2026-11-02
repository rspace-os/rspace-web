import "@/stores/stores/RootStore";
import { runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, onTestFinished, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import getRootStore from "../../getRootStore";

const NOW = 1_700_000_000_000;
const TOKEN_URL = "/userform/ajax/inventoryOauthToken";

const fakeJwt = (secondsToExpiry: number) => {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode({ exp: NOW / 1000 + secondsToExpiry })}.signature`;
};

const serveTokens = (secondsToExpiry: number) => {
  const tokenEndpoint = vi.fn(() => HttpResponse.json({ data: fakeJwt(secondsToExpiry) }));
  server.use(http.get(TOKEN_URL, tokenEndpoint));
  return tokenEndpoint;
};

beforeEach(() => {
  vi.useFakeTimers({ now: NOW });
});

afterEach(() => {
  vi.useRealTimers();
  // The store is a singleton shared by every test in this file.
  runInAction(() => {
    getRootStore().authStore.isAuthenticated = false;
  });
});

describe("AuthStore token refresh", () => {
  test("stays authenticated while a refresh is in flight", async () => {
    const response = Promise.withResolvers<void>();
    server.use(
      http.get(TOKEN_URL, async () => {
        await response.promise;
        return HttpResponse.json({ data: fakeJwt(3600) });
      }),
    );
    const store = getRootStore().authStore;
    runInAction(() => {
      store.isAuthenticated = true;
    });

    const refresh = store.authenticate();
    expect(store.isAuthenticated).toBe(true);

    response.resolve();
    await refresh;
    expect(store.isAuthenticated).toBe(true);
  });

  test.each(["authenticate", "synchronizeWithSessionStorage"] as const)(
    "%s refreshes the token five minutes before it expires",
    async (method) => {
      sessionStorage.setItem("id_token", fakeJwt(3600));
      const tokenEndpoint = serveTokens(3600);
      const store = getRootStore().authStore;

      await store[method]();
      expect(store.isAuthenticated).toBe(true);
      tokenEndpoint.mockClear();

      await vi.advanceTimersByTimeAsync((3600 - 300 - 1) * 1000);
      expect(tokenEndpoint).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(1000);
      expect(tokenEndpoint).toHaveBeenCalledTimes(1);
    },
  );

  test("waits at least a minute when a fresh token already looks close to expiry", async () => {
    // A client clock running ~56 minutes fast sees every new one-hour token as expiring in 240 s.
    const tokenEndpoint = serveTokens(240);

    await getRootStore().authStore.authenticate();
    await vi.advanceTimersByTimeAsync(59 * 1000);
    expect(tokenEndpoint).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1000);
    expect(tokenEndpoint).toHaveBeenCalledTimes(2);
  });

  // The page stays mounted behind the redirect to /login, so staying at a "leave page?" prompt keeps unsaved work.
  test.each([
    ["an error status", () => new HttpResponse(null, { status: 403 })],
    ["the login page", () => HttpResponse.html("<html>login</html>")],
  ])("stays authenticated when the refresh gets %s", async (_, response) => {
    onTestFinished(silenceConsole(["error"], ["Not implemented: navigation"]));
    server.use(http.get(TOKEN_URL, response));
    const store = getRootStore().authStore;
    runInAction(() => {
      store.isAuthenticated = true;
    });

    await store.authenticate();

    expect(store.isAuthenticated).toBe(true);
  });
});
