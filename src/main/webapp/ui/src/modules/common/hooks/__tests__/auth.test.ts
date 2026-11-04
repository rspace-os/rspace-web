import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, Suspense } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getStoredToken } from "@/modules/common/utils/auth";

beforeEach(() => {
  sessionStorage.clear();
  document.getElementById("public_document_view")?.remove();
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

  it("keeps the legacy token endpoint on public document pages", async () => {
    document.body.appendChild(Object.assign(document.createElement("div"), { id: "public_document_view" }));
    server.use(
      http.get("/public/publicView/userform/ajax/inventoryOauthToken", () =>
        HttpResponse.json({ data: "public-token" }),
      ),
    );

    renderTokenConsumer();

    expect(await screen.findByText("public-token")).toBeInTheDocument();
  });
});
