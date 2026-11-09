import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { GranteePicker } from "../GranteePicker";
import { accessResource } from "./fixtures/accessResource";

it("searches through an injected registry entry and omits already assigned grantees", async () => {
  const user = userEvent.setup();
  const request = vi.fn(async () => ({
    docs: [
      { kind: "USER" as const, id: 1, key: "user:1", name: "Assigned user" },
      { kind: "USER" as const, id: 2, key: "user:2", name: "Available user" },
    ],
  }));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const resource = {
    ...accessResource,
    grantees: {
      request,
      queryKey: (id: number, params?: { q?: string; limit?: number }) => ["/fake/grantees", id, params] as const,
    },
  };
  render(
    <QueryClientProvider client={client}>
      <GranteePicker resource={resource} resourceId={9} assignedKeys={new Set(["user:1"])} onPick={vi.fn()} />
    </QueryClientProvider>,
  );
  await user.type(screen.getByRole("combobox"), "av");
  expect(await screen.findByRole("option", { name: /Available user/ })).toBeVisible();
  expect(screen.queryByRole("option", { name: /Assigned user/ })).not.toBeInTheDocument();
  expect(request).toHaveBeenCalledWith(
    9,
    { q: "av", limit: 20 },
    expect.objectContaining({ signal: expect.any(AbortSignal) }),
  );
});
