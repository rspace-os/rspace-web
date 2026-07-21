import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { createElement, Suspense } from "react";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getCurrentUser, useCurrentUserEventSync, useCurrentUserQuery } from "@/modules/common/queries/currentUser";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);

const currentUserResponse = {
  id: 123,
  username: "ada",
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  homeFolderId: 456,
  workbenchId: 789,
  hasPiRole: true,
  hasSysAdminRole: false,
  profileImageUrl: null,
  orcid: { available: true, id: null },
  capabilities: {
    canUseInventory: true,
    canPublish: false,
    canViewSystem: false,
  },
  session: {
    operatedAs: false,
    lastSession: null,
  },
};

const server = setupServer();

beforeAll(() => {
  fetchMock.disableMocks();
  server.listen({ onUnhandledRequest: "error" });
});

beforeEach(() => {
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
});

afterEach(() => server.resetHandlers());

afterAll(() => {
  server.close();
  fetchMock.enableMocks();
});

describe("current-user query", () => {
  it("rejects malformed current-user responses", async () => {
    server.use(http.get("/api/v2/users/me", () => HttpResponse.json({ username: "ada" })));

    await expect(getCurrentUser("token")).rejects.toThrow("Validation failed");
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

    await expect(getCurrentUser("token")).rejects.toThrow("Validation failed");
  });

  it("fetches the current user with bearer authentication", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/users/me", ({ request: receivedRequest }) => {
        request = receivedRequest;
        return HttpResponse.json(currentUserResponse);
      }),
    );

    await expect(getCurrentUser("token")).resolves.toEqual(currentUserResponse);
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("rejects unsuccessful responses", async () => {
    server.use(http.get("/api/v2/users/me", () => new HttpResponse(null, { status: 500 })));

    await expect(getCurrentUser("token")).rejects.toThrow("Failed to fetch current user");
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
      return createElement("output", null, data.email);
    }

    function SyncedConsumer() {
      useCurrentUserEventSync();
      return createElement(Consumer);
    }

    render(
      createElement(
        QueryClientProvider,
        { client: queryClient },
        createElement(Suspense, { fallback: null }, createElement(SyncedConsumer), createElement(Consumer)),
      ),
    );

    expect(await screen.findAllByText("before@example.com")).toHaveLength(2);
    expect(requestCount).toBe(1);

    window.dispatchEvent(new CustomEvent("USER_SET_EMAIL", { detail: { email: "after@example.com" } }));

    expect(await screen.findAllByText("after@example.com")).toHaveLength(2);
    expect(requestCount).toBe(2);
  });
});
