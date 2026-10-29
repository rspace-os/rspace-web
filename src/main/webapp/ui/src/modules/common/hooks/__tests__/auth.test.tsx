import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { tokenExpiringIn } from "@/__tests__/helpers/jwt";
import { server } from "@/__tests__/mswServer";
import { fetchToken, tokenRefreshInterval, useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getStoredToken, resetUiTokenForTests, saveStoredToken } from "@/modules/common/utils/auth";

function renderTokenConsumer(options: { useRestApiV2?: boolean; queryClient?: QueryClient } = {}) {
  const queryClient = options.queryClient ?? new QueryClient({ defaultOptions: { queries: { retry: false } } });

  function Consumer() {
    const { data } = useOauthTokenQuery({ useRestApiV2: options.useRestApiV2 });
    return <output>{data}</output>;
  }

  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <Consumer />
      </Suspense>
    </QueryClientProvider>,
  );
  return { ...rendered, queryClient };
}

beforeEach(() => {
  resetUiTokenForTests();
  sessionStorage.clear();
  document.getElementById("public_document_view")?.remove();
});

describe("fetchToken", () => {
  it("issues and stores an OAuth token through the legacy endpoint by default", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.get("/userform/ajax/inventoryOauthToken", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json({ data: "legacy-token" });
      }),
    );

    await expect(fetchToken()).resolves.toBe("legacy-token");
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(getStoredToken()).toBe("legacy-token");
  });

  it("keeps REST API v2 tokens out of legacy session storage", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.post("/api/v2/oauth/tokens", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json({ accessToken: "new-token" });
      }),
    );

    await expect(fetchToken(true)).resolves.toBe("new-token");
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(getStoredToken()).toBeNull();
  });

  it("rejects malformed token responses", async () => {
    server.use(http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ data: "old-shape" })));

    await expect(fetchToken(true)).rejects.toThrow("Validation failed");
  });

  it("rejects unsuccessful responses", async () => {
    server.use(http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status: 500 })));

    await expect(fetchToken(true)).rejects.toThrow("Failed to fetch token");
  });

  it("mints a new REST API v2 token on page load instead of reusing session storage", async () => {
    saveStoredToken("token-from-previous-run-as-context");
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        return HttpResponse.json({ accessToken: "token-for-current-context" });
      }),
    );
    renderTokenConsumer({ useRestApiV2: true });

    expect(await screen.findByText("token-for-current-context")).toBeInTheDocument();
    expect(requestCount).toBe(1);
    expect(getStoredToken()).toBe("token-from-previous-run-as-context");
  });

  it("uses a session-bound token by default on authenticated pages", async () => {
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        return HttpResponse.json({ accessToken: "authenticated-token" });
      }),
    );
    const { unmount, queryClient } = renderTokenConsumer();

    expect(await screen.findByText("authenticated-token")).toBeInTheDocument();
    expect(requestCount).toBe(1);
    unmount();
    queryClient.clear();
  });

  it("retains the legacy token endpoint on public document pages by default", async () => {
    document.body.appendChild(Object.assign(document.createElement("div"), { id: "public_document_view" }));
    saveStoredToken("token-from-authenticated-session-context");
    server.use(
      http.get("/public/publicView/userform/ajax/inventoryOauthToken", () =>
        HttpResponse.json({ data: "public-token" }),
      ),
    );
    const { unmount, queryClient } = renderTokenConsumer();

    expect(await screen.findByText("public-token")).toBeInTheDocument();
    expect(getStoredToken()).toBe("public-token");
    unmount();
    queryClient.clear();
  });
});

describe("tokenRefreshInterval", () => {
  it("also bounds the query's stale timer for a cached long-lived token", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(["rspace.common.auth", "oauthToken", "v2"], tokenExpiringIn(4102444800));
    const timeout = vi.spyOn(globalThis, "setTimeout");
    const interval = vi.spyOn(globalThis, "setInterval");
    const { unmount } = renderTokenConsumer({ useRestApiV2: true, queryClient });
    try {
      for (const [, delay] of [...timeout.mock.calls, ...interval.mock.calls]) {
        expect(delay ?? 0).toBeLessThanOrEqual(2_147_483_647);
      }
    } finally {
      unmount();
      queryClient.clear();
      timeout.mockRestore();
      interval.mockRestore();
    }
  });

  it("keeps long-lived tokens within the platform timer limit", () => {
    expect(tokenRefreshInterval(tokenExpiringIn(4102444800))).toBe(2_147_483_647);
  });

  it("schedules a refresh at the expiry buffer instead of only marking the query stale", () => {
    expect(tokenRefreshInterval(tokenExpiringIn(600))).toBe(300_000);
    expect(tokenRefreshInterval()).toBe(false);
    expect(tokenRefreshInterval("opaque-test-token")).toBe(false);
  });
});
