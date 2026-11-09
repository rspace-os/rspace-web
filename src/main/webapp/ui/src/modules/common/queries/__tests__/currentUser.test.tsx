import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { currentUserResponse } from "@/__tests__/helpers/currentUserResponse";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { getCurrentUser, useCurrentUserEventSync, useCurrentUserQuery } from "@/modules/common/queries/currentUser";

describe("current-user query", () => {
  it.each(["", "legacy address", "o'brien@uni.ac.uk", "admin@localhost"])(
    "accepts server-provided email %s",
    async (email) => {
      server.use(http.get("/api/v2/users/me", () => HttpResponse.json({ ...currentUserResponse, email })));
      await expect(getCurrentUser()).resolves.toMatchObject({ email });
    },
  );

  it("rejects malformed current-user responses", async () => {
    server.use(http.get("/api/v2/users/me", () => HttpResponse.json({ username: "ada" })));

    await expect(getCurrentUser()).rejects.toThrow();
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

  it("fetches the current user with bearer authentication", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/users/me", ({ request: receivedRequest }) => {
        request = receivedRequest;
        return HttpResponse.json(currentUserResponse);
      }),
    );

    await expect(getCurrentUser()).resolves.toEqual(currentUserResponse);
    expect(request?.headers.get("Authorization")).toBe(`Bearer ${OAUTH_TOKEN}`);
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("rejects unsuccessful responses", async () => {
    server.use(http.get("/api/v2/users/me", () => new HttpResponse(null, { status: 500 })));

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
