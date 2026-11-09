import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, Suspense } from "react";
import { beforeEach, describe, expect, it, onTestFinished as restoreAfterTest } from "vitest";
import { server } from "@/__tests__/mswServer";
import { createUiOAuthToken } from "@/modules/common/api/v2/generated/oauth/oauth";
import { setApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getStoredToken } from "@/modules/common/utils/auth";

beforeEach(() => {
  restoreAfterTest(
    setApiV2TokenProvider({
      getToken: async () => (await createUiOAuthToken()).accessToken,
      recover: async () => (await createUiOAuthToken()).accessToken,
    }),
  );
  sessionStorage.clear();
});

function renderTokenConsumer() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Consumer() {
    const { data } = useOauthTokenQuery();
    return createElement("output", null, data);
  }
  return render(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(Suspense, { fallback: null }, createElement(Consumer)),
    ),
  );
}

describe("useOauthTokenQuery", () => {
  it("mints a session-bound token on authenticated pages, so operate-as sessions work", async () => {
    server.use(http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "authenticated-token" })));

    renderTokenConsumer();

    expect(await screen.findByText("authenticated-token")).toBeInTheDocument();
    expect(getStoredToken()).toBeNull();
  });
});
