import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { tokenExpiringIn } from "@/__tests__/helpers/jwt";
import { server } from "@/__tests__/mswServer";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getStoredToken, mintUiToken, resetUiTokenForTests, saveStoredToken } from "@/modules/common/utils/auth";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import AuthStore from "@/stores/stores/AuthStore";
import type { RootStore } from "@/stores/stores/RootStore";

const MAX_SET_TIMEOUT_DELAY_MS = 2_147_483_647;
let cleanupSetTimeoutSpy = () => {};

beforeEach(() => {
  resetUiTokenForTests();
  sessionStorage.clear();
  document.getElementById("public_document_view")?.remove();
  const setTimeoutSpy = vi.spyOn(globalThis, "setTimeout");
  cleanupSetTimeoutSpy = () => {
    setTimeoutSpy.mock.results.forEach((result, index) => {
      if (setTimeoutSpy.mock.calls[index]?.[1] === MAX_SET_TIMEOUT_DELAY_MS) {
        clearTimeout(result.value as ReturnType<typeof setTimeout>);
      }
    });
    setTimeoutSpy.mockRestore();
  };
});

afterEach(() => {
  cleanupSetTimeoutSpy();
  cleanupSetTimeoutSpy = () => {};
  document.getElementById("public_document_view")?.remove();
});

describe("useOauthToken", () => {
  it("mints a fresh REST API v2 token on authenticated pages", async () => {
    saveStoredToken("token-from-an-earlier-session-context");
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        return HttpResponse.json({ accessToken: "current-session-token" });
      }),
    );
    const { result, unmount } = renderHook(() => useOauthToken());

    try {
      let token: string | undefined;
      await act(async () => {
        token = await result.current.getToken();
      });

      expect(token).toBe("current-session-token");
      expect(requestCount).toBe(1);
      expect(getStoredToken()).toBe("token-from-an-earlier-session-context");
    } finally {
      unmount();
    }
  });

  it("shares a settled mint across hooks, query and AuthStore", async () => {
    let count = 0;
    const token = tokenExpiringIn(3600);
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        count++;
        return HttpResponse.json({ accessToken: token });
      }),
    );
    const first = renderHook(() => useOauthToken());
    expect(await first.result.current.getToken()).toBe(token);
    const second = renderHook(() => useOauthToken());
    expect(await second.result.current.getToken()).toBe(token);
    const client = new QueryClient();
    const query = renderHook(() => useOauthTokenQuery(), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={client}>
          <Suspense>{children}</Suspense>
        </QueryClientProvider>
      ),
    });
    await waitFor(() => expect(query.result.current?.data).toBe(token));
    const store = new AuthStore({} as RootStore);
    try {
      await store.authenticate();
      expect(count).toBe(1);
    } finally {
      if (store.timeoutId) clearTimeout(store.timeoutId);
      client.clear();
    }
  });

  it("coalesces overlapping refreshes after the shared token approaches expiry", async () => {
    let count = 0;
    const token = tokenExpiringIn(3600);
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        count++;
        return HttpResponse.json({ accessToken: token });
      }),
    );
    const first = renderHook(() => useOauthToken());
    await first.result.current.getToken();
    const now = Date.now();
    const clock = vi.spyOn(Date, "now").mockReturnValue(now + 3400 * 1000);
    try {
      const second = renderHook(() => useOauthToken());
      const third = renderHook(() => useOauthToken());
      await Promise.all([second.result.current.getToken(), third.result.current.getToken()]);
      expect(count).toBe(2);
    } finally {
      clock.mockRestore();
    }
  });

  it("replaces the shared token after recovery rejects the previous token", async () => {
    let count = 0;
    const oldToken = `header.${btoa(JSON.stringify({ exp: 9999999999, refreshTokenHash: "same", iat: 1 }))}.signature`;
    const replacement = `header.${btoa(JSON.stringify({ exp: 9999999999, refreshTokenHash: "same", iat: 2 }))}.signature`;
    server.use(
      http.post("/api/v2/oauth/tokens", () =>
        HttpResponse.json({ accessToken: ++count === 1 ? oldToken : replacement }),
      ),
      http.get("/api/v1/userDetails/whoami", ({ request }) =>
        request.headers.get("Authorization") === `Bearer ${oldToken}`
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json({ username: "user" }),
      ),
    );
    const first = renderHook(() => useOauthToken());
    await first.result.current.getToken();
    await fetchWithUiToken("/api/v1/userDetails/whoami", { headers: { Authorization: `Bearer ${oldToken}` } });
    const second = renderHook(() => useOauthToken());
    expect(await second.result.current.getToken()).toBe(replacement);
    expect(count).toBe(2);
  });

  it("retries a failed mint instead of caching its rejection", async () => {
    let count = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        count++;
        return count === 1
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json({ accessToken: "retried-token" });
      }),
    );
    await expect(mintUiToken()).rejects.toThrow();
    await expect(mintUiToken()).resolves.toBe("retried-token");
    expect(count).toBe(2);
  });

  it("keeps the legacy token endpoint and storage on public document pages", async () => {
    document.body.appendChild(Object.assign(document.createElement("div"), { id: "public_document_view" }));
    saveStoredToken("token-from-authenticated-session-context");
    server.use(
      http.get("/public/publicView/userform/ajax/inventoryOauthToken", () =>
        HttpResponse.json({ data: "public-token" }),
      ),
    );
    const { result, unmount } = renderHook(() => useOauthToken());

    try {
      let token: string | undefined;
      await act(async () => {
        token = await result.current.getToken();
      });

      expect(token).toBe("public-token");
      expect(getStoredToken()).toBe("public-token");
    } finally {
      unmount();
    }
  });
});
