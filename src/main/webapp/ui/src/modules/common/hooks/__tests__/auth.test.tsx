import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, onTestFinished, vi } from "vitest";
import { tokenExpiringIn } from "@/__tests__/helpers/jwt";
import { server } from "@/__tests__/mswServer";
import { createUiOAuthToken } from "@/modules/common/api/v2/generated/oauth/oauth";
import { setApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { tokenRefreshInterval, useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getStoredToken, saveStoredToken } from "@/modules/common/utils/auth";

function renderTokenConsumer(options: { queryClient?: QueryClient } = {}) {
  const queryClient = options.queryClient ?? new QueryClient({ defaultOptions: { queries: { retry: false } } });

  function Consumer() {
    const { data } = useOauthTokenQuery();
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
  onTestFinished(
    setApiV2TokenProvider({
      getToken: async () => (await createUiOAuthToken()).accessToken,
      recover: async () => (await createUiOAuthToken()).accessToken,
    }),
  );
  sessionStorage.clear();
});

describe("useOauthTokenQuery", () => {
  it("mints a new REST API v2 token on page load instead of reusing session storage", async () => {
    saveStoredToken("token-from-previous-run-as-context");
    let requestCount = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => {
        requestCount += 1;
        return HttpResponse.json({ accessToken: "token-for-current-context" });
      }),
    );
    renderTokenConsumer();

    expect(await screen.findByText("token-for-current-context")).toBeInTheDocument();
    expect(requestCount).toBe(1);
    expect(getStoredToken()).toBe("token-from-previous-run-as-context");
  });
});

describe("tokenRefreshInterval", () => {
  it("also bounds the query's stale timer for a cached long-lived token", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(["rspace.common.auth", "oauthToken"], tokenExpiringIn(4102444800));
    const timeout = vi.spyOn(globalThis, "setTimeout");
    const interval = vi.spyOn(globalThis, "setInterval");
    const { unmount } = renderTokenConsumer({ queryClient });
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
