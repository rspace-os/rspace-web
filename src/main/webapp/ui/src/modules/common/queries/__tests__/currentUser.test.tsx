import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { currentUserResponse } from "@/__tests__/helpers/currentUserResponse";
import { server } from "@/__tests__/mswServer";
import { getCurrentUser, useCurrentUserEventSync, useCurrentUserQuery } from "@/modules/common/queries/currentUser";

describe("current-user query", () => {
  // The contract leaves `email` without `format: email`, so the server may send text that is not an address.
  it("accepts a server-provided email that is not an address", async () => {
    server.use(
      http.get("/api/v2/users/me", () => HttpResponse.json({ ...currentUserResponse, email: "legacy address" })),
    );
    await expect(getCurrentUser()).resolves.toMatchObject({ email: "legacy address" });
  });

  it("rejects a non-ISO last-session timestamp", async () => {
    server.use(
      http.get("/api/v2/users/me", () =>
        HttpResponse.json({
          ...currentUserResponse,
          session: { ...currentUserResponse.session, lastSession: "15 July 2026" },
        }),
      ),
    );

    await expect(getCurrentUser()).rejects.toThrow();
  });

  it("deduplicates consumers and refetches after a profile event", async () => {
    let requestCount = 0;
    server.use(
      http.get("/api/v2/users/me", () => {
        requestCount += 1;
        return HttpResponse.json({
          ...currentUserResponse,
          email: requestCount === 1 ? "before@example.com" : "after@example.com",
        });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    function Consumer() {
      const { data } = useCurrentUserQuery();
      return <output>{data.email}</output>;
    }

    function SyncedConsumer() {
      useCurrentUserEventSync();
      return <Consumer />;
    }

    render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <SyncedConsumer />
          <Consumer />
        </Suspense>
      </QueryClientProvider>,
    );

    expect(await screen.findAllByText("before@example.com")).toHaveLength(2);
    expect(requestCount).toBe(1);

    window.dispatchEvent(new CustomEvent("USER_SET_EMAIL", { detail: { email: "after@example.com" } }));

    expect(await screen.findAllByText("after@example.com")).toHaveLength(2);
    expect(requestCount).toBe(2);
  });
});
