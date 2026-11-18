import "@/stores/stores/RootStore";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import JwtService from "@/common/JwtService";
import AuthStore from "@/stores/stores/AuthStore";
import type { RootStore } from "@/stores/stores/RootStore";

const initialLocation = window.location.href;
const initialOrigin = window.location.origin;

const tokenExpiringIn = (seconds: number) =>
  `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds }))}.signature`;
let store: AuthStore;

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal("location", { href: initialLocation, origin: initialOrigin });
  sessionStorage.clear();
  store = new AuthStore({} as RootStore);
});

afterEach(() => {
  store.signOut();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const failWith = (status: number | "network") =>
  server.use(
    http.get("*/userform/ajax/inventoryOauthToken", () =>
      status === "network" ? HttpResponse.error() : new HttpResponse(null, { status }),
    ),
  );

describe("AuthStore refresh recovery", () => {
  it.each([429, 500, 503, "network"] as const)("retains the mounted session and recovers after %s", async (status) => {
    const authenticate = vi.spyOn(store, "authenticate");
    const token = tokenExpiringIn(3600);
    JwtService.saveToken(token);
    await store.synchronizeWithSessionStorage();
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
    server.use(http.get("*/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: fresh })));
    await store.authenticate();
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
      http.get("*/userform/ajax/inventoryOauthToken", () => {
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
      http.get("*/userform/ajax/inventoryOauthToken", () => {
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
      http.get("*/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: tokenExpiringIn(3600) })),
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
      http.get("*/userform/ajax/inventoryOauthToken", () => {
        started.resolve();
        return response.promise;
      }),
    );
    const pending = store.authenticate();
    await started.promise;
    store.signOut();
    response.resolve(HttpResponse.json({ data: tokenExpiringIn(3600) }));
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
    server.use(http.get("*/userform/ajax/inventoryOauthToken", () => new Promise<Response>(() => {})));
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
      http.get("*/userform/ajax/inventoryOauthToken", () => {
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
