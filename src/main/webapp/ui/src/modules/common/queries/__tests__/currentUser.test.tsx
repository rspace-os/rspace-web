import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { currentUserResponse } from "@/__tests__/helpers/currentUserResponse";
import { server } from "@/__tests__/mswServer";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getCurrentUser, useCurrentUserEventSync, useCurrentUserQuery } from "@/modules/common/queries/currentUser";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);

beforeEach(() => {
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
});

describe("current-user query", () => {
  it.each(["", "legacy address", "o'brien@uni.ac.uk", "admin@localhost"])(
    "accepts server-provided email %s",
    async (email) => {
      server.use(http.get("/api/v2/users/me", () => HttpResponse.json({ ...currentUserResponse, email })));
      await expect(getCurrentUser("token")).resolves.toMatchObject({ email });
    },
  );

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
