import "@/stores/stores/RootStore";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, onTestFinished as restoreAfterTest, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import JwtService from "@/common/JwtService";
import { createUiOAuthToken } from "@/modules/common/api/v2/generated/oauth/oauth";
import { ApiV2IdentityChangedError, setApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { resetUiTokenForTests } from "@/modules/common/utils/auth";
import AuthStore from "@/stores/stores/AuthStore";
import type { RootStore } from "@/stores/stores/RootStore";

const initialLocation = window.location.href;
const initialOrigin = window.location.origin;

const tokenExpiringIn = (seconds: number) =>
  `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds }))}.signature`;
let store: AuthStore;

const MINT_URL = "*/api/v2/oauth/tokens";

beforeEach(() => {
  restoreAfterTest(
    setApiV2TokenProvider({
      // Like the session provider, an aborted caller rejects at once while the shared mint carries on.
      getToken: (signal?: AbortSignal) =>
        new Promise<string>((resolve, reject) => {
          signal?.addEventListener("abort", () => reject(signal.reason), { once: true });
          createUiOAuthToken().then((token) => resolve(token.accessToken), reject);
        }),
      recover: async () => (await createUiOAuthToken()).accessToken,
    }),
  );
  resetUiTokenForTests();
  document.getElementById("public_document_view")?.remove();
  vi.useFakeTimers();
  vi.stubGlobal("location", { href: initialLocation, origin: initialOrigin });
  sessionStorage.clear();
  store = new AuthStore({} as RootStore);
});

afterEach(() => {
  store.signOut();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  document.getElementById("public_document_view")?.remove();
});

const failWith = (status: number | "network") =>
  server.use(
    http.post(MINT_URL, () =>
      status === "network" ? HttpResponse.error() : new HttpResponse(null, { status }),
    ),
  );

describe("AuthStore refresh recovery", () => {
  it.each([429, 500, 503, "network"] as const)("retains the mounted session and recovers after %s", async (status) => {
    const authenticate = vi.spyOn(store, "authenticate");
    const token = tokenExpiringIn(3600);
    server.use(http.post(MINT_URL, () => HttpResponse.json({ accessToken: token })));
    await store.refreshAuthentication();
    authenticate.mockClear();
    failWith(status);
    await store.authenticate();
    expect(store.isAuthenticated).toBe(true);
    expect(JwtService.getToken()).toBe(token);
    expect(window.location.href).toBe(initialLocation);
    authenticate.mockResolvedValueOnce();
    await vi.advanceTimersByTimeAsync(1000);
    expect(authenticate).toHaveBeenCalledTimes(2);
    authenticate.mockRestore();
    const fresh = tokenExpiringIn(7200);
    server.use(http.post(MINT_URL, () => HttpResponse.json({ accessToken: fresh })));
    // msw's fetch interception needs a fake-timer tick once a retry timer has run.
    const refreshed = store.authenticate();
    await vi.advanceTimersByTimeAsync(0);
    await refreshed;
    expect(JwtService.getToken()).toBe(fresh);
    expect(store.isAuthenticated).toBe(true);
    expect(store.isSynchronizing).toBe(false);
  });

  it("bounds retries by token validity and redirects when it expires", async () => {
    const authenticate = vi.spyOn(store, "authenticate");
    JwtService.saveToken(tokenExpiringIn(20));
    store.isAuthenticated = true;
    let requests = 0;
    server.use(
      http.post(MINT_URL, () => {
        requests++;
        return new HttpResponse(null, { status: 500 });
      }),
    );
    await store.authenticate();
    for (const [index, delay] of [1000, 2000, 4000, 8000, 5000].entries()) {
      await vi.advanceTimersByTimeAsync(delay);
      await authenticate.mock.results.at(-1)?.value.catch(() => undefined);
      expect(requests).toBe(index + 2);
    }
    expect(window.location.href).toBe("/login");
    expect(store.isAuthenticated).toBe(true);
    expect(JwtService.getToken()).toBeNull();
    expect(store.timeoutId).toBeNull();
  });

  it("caps the backoff at 32 seconds and resets it after a success", async () => {
    const authenticate = vi.spyOn(store, "authenticate");
    JwtService.saveToken(tokenExpiringIn(3600));
    store.isAuthenticated = true;
    let requests = 0;
    failWith(500);
    server.use(
      http.post(MINT_URL, () => {
        requests++;
        return undefined;
      }),
    );
    await store.authenticate();
    for (const [index, delay] of [1000, 2000, 4000, 8000, 16_000, 32_000, 32_000].entries()) {
      await vi.advanceTimersByTimeAsync(delay - 1);
      expect(requests).toBe(index + 1);
      await vi.advanceTimersByTimeAsync(1);
      await authenticate.mock.results.at(-1)?.value;
      expect(requests).toBe(index + 2);
    }
    server.use(
      http.post(MINT_URL, () => HttpResponse.json({ accessToken: tokenExpiringIn(3600) })),
    );
    await store.authenticate();
    failWith(500);
    await store.authenticate();
    authenticate.mockClear();
    await vi.advanceTimersByTimeAsync(1000);
    expect(authenticate).toHaveBeenCalledTimes(1);
    expect(window.location.href).toBe(initialLocation);
  });

  it.each([401, 500])("clears an expired token and redirects after %s", async (status) => {
    JwtService.saveToken(tokenExpiringIn(-1));
    store.isAuthenticated = true;
    failWith(status);
    await expect(store.authenticate()).rejects.toBeDefined();
    expect(window.location.href).toBe("/login");
    expect(store.isAuthenticated).toBe(true);
    expect(JwtService.getToken()).toBeNull();
    expect(store.timeoutId).toBeNull();
  });

  it("redirects on 401 even with a valid stored token", async () => {
    JwtService.saveToken(tokenExpiringIn(3600));
    store.isAuthenticated = true;
    failWith(401);
    await expect(store.authenticate()).rejects.toBeDefined();
    expect(window.location.href).toBe("/login");
    expect(JwtService.getToken()).toBeNull();
  });

  it("cancels retries on sign-out", async () => {
    JwtService.saveToken(tokenExpiringIn(3600));
    failWith(500);
    await store.authenticate();
    store.signOut();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(store.timeoutId).toBeNull();
    expect(store.isAuthenticated).toBe(false);
  });

  it("does not restore authentication when a pending refresh completes after sign-out", async () => {
    const response = Promise.withResolvers<Response>();
    const started = Promise.withResolvers<void>();
    server.use(
      http.post(MINT_URL, () => {
        started.resolve();
        return response.promise;
      }),
    );
    const pending = store.authenticate();
    await started.promise;
    store.signOut();
    response.resolve(HttpResponse.json({ accessToken: tokenExpiringIn(3600) }));
    await pending;
    expect(store.isAuthenticated).toBe(false);
    expect(JwtService.getToken()).toBeNull();
    expect(store.timeoutId).toBeNull();
  });
  it.each([20, 1])("redirects at token expiry when a retry never responds (token valid for %ss)", async (seconds) => {
    // On a whole second, a 1s token makes the first retry start exactly at expiry.
    vi.setSystemTime(1_700_000_000_000);
    JwtService.saveToken(tokenExpiringIn(seconds));
    store.isAuthenticated = true;
    failWith(503);
    await store.authenticate();
    server.use(http.post(MINT_URL, () => new Promise<Response>(() => {})));
    await vi.advanceTimersByTimeAsync(20_000);
    expect(window.location.href).toBe("/login");
    expect(store.isAuthenticated).toBe(true);
    expect(JwtService.getToken()).toBeNull();
    expect(store.timeoutId).toBeNull();
  });

  it("rejects when the stored token expires while a refresh is pending", async () => {
    JwtService.saveToken(tokenExpiringIn(1));
    store.isAuthenticated = true;
    const response = Promise.withResolvers<Response>();
    const started = Promise.withResolvers<void>();
    server.use(
      http.post(MINT_URL, () => {
        started.resolve();
        return response.promise;
      }),
    );
    const pending = store.authenticate();
    const rejected = expect(pending).rejects.toBeDefined();
    await started.promise;
    await vi.advanceTimersByTimeAsync(1000);
    response.resolve(new HttpResponse(null, { status: 503 }));
    await rejected;
    expect(store.isAuthenticated).toBe(true);
    expect(JwtService.getToken()).toBeNull();
    expect(window.location.href).toBe("/login");
    expect(store.timeoutId).toBeNull();
  });
});

describe("AuthStore initialization", () => {
  it("mints a fresh session-bound token on authenticated initialization", async () => {
    JwtService.saveToken(tokenExpiringIn(3600));
    const freshToken = tokenExpiringIn(3600);
    let requestCount = 0;
    server.use(
      http.post(MINT_URL, () => {
        requestCount += 1;
        return HttpResponse.json({ accessToken: freshToken });
      }),
    );
    const authStore = new AuthStore({} as RootStore);

    try {
      await authStore.refreshAuthentication();

      expect(requestCount).toBe(1);
      expect(JwtService.getToken()).toBe(freshToken);
      expect(authStore.isAuthenticated).toBe(true);
      expect(authStore.isSynchronizing).toBe(false);
    } finally {
      if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
    }
  });

  it("mints a fresh legacy token on public document initialization", async () => {
    document.body.appendChild(Object.assign(document.createElement("div"), { id: "public_document_view" }));
    JwtService.saveToken(tokenExpiringIn(3600));
    let requestCount = 0;
    server.use(
      http.get("/public/publicView/userform/ajax/inventoryOauthToken", () => {
        requestCount += 1;
        return HttpResponse.json({ data: "public-legacy-token" });
      }),
    );
    const authStore = new AuthStore({} as RootStore);

    try {
      await authStore.refreshAuthentication();

      expect(requestCount).toBe(1);
      expect(JwtService.getToken()).toBe("public-legacy-token");
      expect(authStore.isAuthenticated).toBe(true);
    } finally {
      if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
    }
  });
});

it("does not replace a provider identity reload with the login page", async () => {
  const location = { href: "http://localhost:3000/inventory", origin: "http://localhost:3000", reload: vi.fn() };
  vi.stubGlobal("location", location);
  JwtService.saveToken(tokenExpiringIn(60));
  const changed = new ApiV2IdentityChangedError();
  const restore = setApiV2TokenProvider({
    getToken: async () => {
      JwtService.destroyToken();
      location.reload();
      throw changed;
    },
    recover: async () => {
      throw changed;
    },
  });
  try {
    const authStore = new AuthStore({} as RootStore);
    await expect(authStore.authenticate()).rejects.toBe(changed);
    expect(location.reload).toHaveBeenCalledOnce();
    expect(location.href).toBe("http://localhost:3000/inventory");
  } finally {
    restore();
  }
});
