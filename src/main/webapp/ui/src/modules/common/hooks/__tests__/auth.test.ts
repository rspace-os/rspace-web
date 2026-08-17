import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, Suspense } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { fetchToken, useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getStoredToken, saveStoredToken } from "@/modules/common/utils/auth";

beforeEach(() => {
  sessionStorage.clear();
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
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    function Consumer() {
      const { data } = useOauthTokenQuery({ useRestApiV2: true });
      return createElement("output", null, data);
    }

    render(
      createElement(
        QueryClientProvider,
        { client: queryClient },
        createElement(Suspense, { fallback: null }, createElement(Consumer)),
      ),
    );

    expect(await screen.findByText("token-for-current-context")).toBeInTheDocument();
    expect(requestCount).toBe(1);
    expect(getStoredToken()).toBe("token-from-previous-run-as-context");
  });
});
