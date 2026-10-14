import "@/stores/stores/RootStore";
import { runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import getRootStore from "../../getRootStore";

const NOW = 1_700_000_000_000;

const fakeJwt = (secondsToExpiry: number) => {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode({ exp: NOW / 1000 + secondsToExpiry })}.signature`;
};

afterEach(() => {
  const { authStore } = getRootStore();
  if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
  runInAction(() => {
    authStore.isAuthenticated = false;
  });
  sessionStorage.clear();
  vi.restoreAllMocks();
});

describe("AuthStore token refresh", () => {
  test("stays authenticated while a refresh is in flight", async () => {
    let releaseResponse = () => {};
    const responseGate = new Promise<void>((resolve) => {
      releaseResponse = resolve;
    });
    server.use(
      http.get("/userform/ajax/inventoryOauthToken", async () => {
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

  test("schedules the refresh five minutes before the token expires", async () => {
    vi.spyOn(Date, "now").mockReturnValue(NOW);
    const setTimeoutSpy = vi.spyOn(globalThis, "setTimeout");
    sessionStorage.setItem("id_token", fakeJwt(3600));
    const store = getRootStore().authStore;

    await store.synchronizeWithSessionStorage();

    expect(store.isAuthenticated).toBe(true);
    expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), (3600 - 300) * 1000);
  });
});
