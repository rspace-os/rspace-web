import "@/stores/stores/RootStore";
import { runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import getRootStore from "../../getRootStore";

const NOW = 1_700_000_000_000;
const TOKEN_URL = "/userform/ajax/inventoryOauthToken";

const fakeJwt = (secondsToExpiry: number) => {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode({ exp: NOW / 1000 + secondsToExpiry })}.signature`;
};

const countTokenRequests = (secondsToExpiry: number) => {
  const requests = { count: 0 };
  server.use(
    http.get(TOKEN_URL, () => {
      requests.count++;
      return HttpResponse.json({ data: fakeJwt(secondsToExpiry) });
    }),
  );
  return requests;
};

const useFakeClock = () => vi.useFakeTimers({ now: NOW, toFake: ["setTimeout", "clearTimeout", "Date"] });

afterEach(() => {
  const { authStore } = getRootStore();
  if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
  runInAction(() => {
    authStore.isAuthenticated = false;
  });
  sessionStorage.clear();
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("AuthStore token refresh", () => {
  test("stays authenticated while a refresh is in flight", async () => {
    let releaseResponse = () => {};
    const responseGate = new Promise<void>((resolve) => {
      releaseResponse = resolve;
    });
    server.use(
      http.get(TOKEN_URL, async () => {
        await responseGate;
        return HttpResponse.json({ data: fakeJwt(3600) });
      }),
    );
    const store = getRootStore().authStore;
    runInAction(() => {
      store.isAuthenticated = true;
    });

    const refresh = store.authenticate();
    expect(store.isAuthenticated).toBe(true);

    releaseResponse();
    await refresh;
    expect(store.isAuthenticated).toBe(true);
  });

  test.each(["authenticate", "synchronizeWithSessionStorage"] as const)(
    "%s refreshes the token five minutes before it expires",
    async (method) => {
      useFakeClock();
      sessionStorage.setItem("id_token", fakeJwt(3600));
      const requests = countTokenRequests(3600);
      const store = getRootStore().authStore;

      await store[method]();
      const requestsBeforeRefresh = requests.count;
      expect(store.isAuthenticated).toBe(true);

      await vi.advanceTimersByTimeAsync((3600 - 300 - 1) * 1000);
      expect(requests.count).toBe(requestsBeforeRefresh);

      await vi.advanceTimersByTimeAsync(1000);
      await vi.waitFor(() => expect(requests.count).toBe(requestsBeforeRefresh + 1));
    },
  );

  test("waits at least a minute when a fresh token already looks close to expiry", async () => {
    useFakeClock();
    // A client clock running ~56 minutes fast sees every new one-hour token as expiring in 240 s.
    const requests = countTokenRequests(240);

    await getRootStore().authStore.authenticate();
    expect(requests.count).toBe(1);

    await vi.advanceTimersByTimeAsync(59 * 1000);
    expect(requests.count).toBe(1);

    await vi.advanceTimersByTimeAsync(1000);
    await vi.waitFor(() => expect(requests.count).toBe(2));
  });

  test("stops being authenticated when the refresh fails", async () => {
    const restoreConsole = silenceConsole(["error"], ["Not implemented: navigation"]);
    server.use(http.get(TOKEN_URL, () => new HttpResponse(null, { status: 500 })));
    const store = getRootStore().authStore;
    runInAction(() => {
      store.isAuthenticated = true;
    });

    await store.authenticate();

    expect(store.isAuthenticated).toBe(false);
    restoreConsole();
  });
});
