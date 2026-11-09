import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from "vitest";
import { tokenExpiringIn } from "@/__tests__/helpers/jwt";
import { server } from "@/__tests__/mswServer";
import JwtService from "@/common/JwtService";
import { createUiOAuthToken } from "@/modules/common/api/v2/generated/oauth/oauth";
import { ApiV2IdentityChangedError, setApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { resetUiTokenForTests } from "@/modules/common/utils/auth";
import AuthStore from "@/stores/stores/AuthStore";
import type { RootStore } from "@/stores/stores/RootStore";

beforeEach(() => {
  onTestFinished(
    setApiV2TokenProvider({
      getToken: async () => (await createUiOAuthToken()).accessToken,
      recover: async () => (await createUiOAuthToken()).accessToken,
    }),
  );
  vi.stubGlobal("location", { href: "http://localhost:3000/inventory", origin: "http://localhost:3000" });
  resetUiTokenForTests();
  sessionStorage.clear();
  document.getElementById("public_document_view")?.remove();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  document.getElementById("public_document_view")?.remove();
});

describe("AuthStore", () => {
  it("retries scheduled failures while the token is valid and stops after expiry", async () => {
    vi.useFakeTimers();
    const token = tokenExpiringIn(3600);
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        if (requestCount === 2 || requestCount > 3) return new HttpResponse(null, { status: 500 });
        return HttpResponse.json({ accessToken: requestCount === 1 ? token : tokenExpiringIn(3600) });
      }),
    );
    const authStore = new AuthStore({} as RootStore);
    try {
      await authStore.refreshAuthentication();
      await vi.advanceTimersByTimeAsync(3300_000);
      await vi.waitFor(() => expect(requestCount).toBe(2));
      expect(authStore.isAuthenticated).toBe(true);
      expect(JwtService.getToken()).toBe(token);
      await vi.advanceTimersByTimeAsync(1000);
      await vi.waitFor(() => expect(JwtService.getToken()).not.toBe(token));
      expect(requestCount).toBe(3);
      expect(authStore.isAuthenticated).toBe(true);
      expect(authStore.isSynchronizing).toBe(false);
      JwtService.saveToken(tokenExpiringIn(-1));
      resetUiTokenForTests();
      await vi.advanceTimersByTimeAsync(3300_000);
      await vi.waitFor(() => expect(requestCount).toBe(4));
      expect(authStore.isAuthenticated).toBe(false);
      expect(window.location.href).toBe("/login");
      expect(authStore.timeoutId).toBeNull();
      await vi.advanceTimersByTimeAsync(60_000);
      expect(requestCount).toBe(4);
    } finally {
      if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
    }
  });

  it("retries at token expiry when the backoff would outlast the token", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-01-01T00:00:00Z"));
    JwtService.saveToken(tokenExpiringIn(20));
    const authStore = new AuthStore({} as RootStore);
    authStore.isAuthenticated = true;
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        return new HttpResponse(null, { status: 500 });
      }),
    );

    try {
      await expect(authStore.authenticate()).rejects.toMatchObject({ status: 500 });
      for (const [index, delay] of [1000, 2000, 4000, 8000].entries()) {
        await vi.advanceTimersByTimeAsync(delay);
        await vi.waitFor(() => expect(requestCount).toBe(index + 2));
      }
      await vi.advanceTimersByTimeAsync(new Date("2026-01-01T00:00:20Z").getTime() - Date.now() - 1);
      expect(requestCount).toBe(5);
      expect(authStore.isAuthenticated).toBe(true);

      await vi.advanceTimersByTimeAsync(1);
      await vi.waitFor(() => expect(requestCount).toBe(6));
      expect(authStore.isAuthenticated).toBe(false);
      expect(window.location.href).toBe("/login");
      expect(authStore.timeoutId).toBeNull();
    } finally {
      if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
    }
  });

  it("clears authentication when a pending refresh fails after token expiry", async () => {
    vi.useFakeTimers();
    const token = tokenExpiringIn(10);
    JwtService.saveToken(token);
    const authStore = new AuthStore({} as RootStore);
    authStore.isAuthenticated = true;
    const response = Promise.withResolvers<Response>();
    const started = Promise.withResolvers<void>();
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        started.resolve();
        return response.promise;
      }),
    );

    const authentication = authStore.authenticate();
    const rejection = expect(authentication).rejects.toMatchObject({ status: 500 });
    await started.promise;
    expect(authStore.isAuthenticated).toBe(true);
    await vi.advanceTimersByTimeAsync(11_000);
    response.resolve(new HttpResponse(null, { status: 500 }));
    await rejection;

    expect(authStore.isAuthenticated).toBe(false);
    expect(window.location.href).toBe("/login");
    expect(authStore.isSynchronizing).toBe(false);
    expect(authStore.timeoutId).toBeNull();
  });

  it("preserves a usable session and retries a failed refresh", async () => {
    const token = tokenExpiringIn(3600);
    server.use(http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: token })));
    const authStore = new AuthStore({} as RootStore);
    await authStore.refreshAuthentication();
    resetUiTokenForTests();
    server.use(http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status: 500 })));
    try {
      await expect(authStore.authenticate()).rejects.toMatchObject({ status: 500 });
      expect(authStore.isAuthenticated).toBe(true);
      expect(JwtService.getToken()).toBe(token);
      expect(authStore.timeoutId).not.toBeNull();
    } finally {
      if (authStore.timeoutId) clearTimeout(authStore.timeoutId);
    }
  });

  it.each([429, 500])("keeps the page and retries with a valid token after %s", async (status) => {
    vi.useFakeTimers();
    JwtService.saveToken(tokenExpiringIn(20));
    const location = { href: "http://localhost:3000/inventory", origin: "http://localhost:3000" };
    vi.stubGlobal("location", location);
    server.use(http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status })));
    const authStore = new AuthStore({} as RootStore);

    await expect(authStore.refreshAuthentication()).rejects.toMatchObject({ status });

    expect(window.location).toBe(location);
    expect(location.href).toBe("http://localhost:3000/inventory");
    expect(authStore.isSynchronizing).toBe(false);
    expect(authStore.timeoutId).not.toBeNull();
    await vi.advanceTimersByTimeAsync(21_000);
    await vi.waitFor(() => expect(window.location.href).toBe("/login"));
  });

  it("redirects on an authentication failure", async () => {
    vi.stubGlobal("location", { href: "http://localhost:3000/inventory", origin: "http://localhost:3000" });
    server.use(http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status: 401 })));
    const authStore = new AuthStore({} as RootStore);

    await authStore.refreshAuthentication();

    expect(window.location.href).toBe("/login");
  });

  it("mints a fresh session-bound token on authenticated initialization", async () => {
    JwtService.saveToken(tokenExpiringIn(3600));
    const freshToken = tokenExpiringIn(3600);
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
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
