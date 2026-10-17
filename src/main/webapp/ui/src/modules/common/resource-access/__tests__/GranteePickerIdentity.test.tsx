import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { GranteePicker } from "@/modules/common/resource-access/GranteePicker";
import type { ResourceGranteeDirectoryEntry } from "@/modules/common/resource-access/schemas";

const resource = "test-collections";
const resourceId = 9;
const assignedKeys = new Set<string>();

function directoryEntry(id: number, name: string): ResourceGranteeDirectoryEntry {
  return { kind: "USER", id, key: `user:${id}`, name, detail: `${name.toLowerCase()}@example.org` };
}

function picker(queryClient: QueryClient, token: string) {
  return (
    <QueryClientProvider client={queryClient}>
      <GranteePicker
        resource={resource}
        resourceId={resourceId}
        token={token}
        assignedKeys={assignedKeys}
        onPick={() => {}}
      />
    </QueryClientProvider>
  );
}

describe("GranteePicker query identity", () => {
  it("does not reuse a grantee result when the same search moves to a new token", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const authorizationHeaders: string[] = [];
    const searchTerms: string[] = [];
    let releaseTokenB: () => void = () => {};
    const tokenBResponse = new Promise<void>((resolve) => {
      releaseTokenB = resolve;
    });

    server.use(
      http.get("/api/v2/test-collections/9/access/grantees", async ({ request }) => {
        const authorization = request.headers.get("Authorization") ?? "";
        authorizationHeaders.push(authorization);
        searchTerms.push(new URL(request.url).searchParams.get("query") ?? "");
        if (authorization === "Bearer token-a") {
          return HttpResponse.json([directoryEntry(11, "Alice")]);
        }
        if (authorization === "Bearer token-b") {
          await tokenBResponse;
          return HttpResponse.json([directoryEntry(22, "Bob")]);
        }
        return new HttpResponse(null, { status: 403 });
      }),
    );

    const view = render(picker(queryClient, "token-a"));
    try {
      const search = await screen.findByLabelText("common:resourceAccess.addUserOrGroup");
      await user.type(search, "al");
      const aliceOption = await screen.findByRole("option");
      expect(within(aliceOption).getByText("Alice")).toBeVisible();
      expect(authorizationHeaders).toEqual(["Bearer token-a"]);

      view.rerender(picker(queryClient, "token-b"));
      await waitFor(() => expect(authorizationHeaders).toContain("Bearer token-b"));

      expect(screen.queryByRole("option")).not.toBeInTheDocument();
      expect(screen.queryByText("Alice")).not.toBeInTheDocument();

      releaseTokenB();
      const bobOption = await screen.findByRole("option");
      expect(within(bobOption).getByText("Bob")).toBeVisible();
      expect(screen.queryByText("Alice")).not.toBeInTheDocument();
      expect(authorizationHeaders).toEqual(["Bearer token-a", "Bearer token-b"]);
      expect(searchTerms).toEqual(["al", "al"]);
    } finally {
      releaseTokenB();
    }
  });
});
