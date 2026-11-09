import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { expect, it, vi } from "vitest";
import { currentUserResponse } from "@/__tests__/helpers/currentUserResponse";
import { getGetCurrentUserQueryKey } from "@/modules/common/api/v2/generated/users/users";
import { useQueryIdentityScope } from "../queryIdentityScope";

it("defers identity notifications when another consumer changes identity during render", async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const errors = vi.spyOn(console, "error").mockImplementation(() => {});
  function ExistingConsumer() {
    const [subject] = useQueryIdentityScope();
    return <output aria-label="Current subject">{subject ?? "anonymous"}</output>;
  }
  function NewConsumer() {
    const [mounted, setMounted] = useState(false);
    if (!mounted) {
      client.setQueryData(getGetCurrentUserQueryKey(), { ...currentUserResponse, id: 84 });
      setMounted(true);
    }
    useQueryIdentityScope();
    return null;
  }
  try {
    const view = render(
      <QueryClientProvider client={client}>
        <ExistingConsumer />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("status", { name: "Current subject" })).toHaveTextContent("anonymous");
    view.rerender(
      <QueryClientProvider client={client}>
        <ExistingConsumer />
        <NewConsumer />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(screen.getByRole("status", { name: "Current subject" })).toHaveTextContent("84"));
    expect(errors.mock.calls.flat().join(" ")).not.toContain("Cannot update a component");
  } finally {
    errors.mockRestore();
    client.clear();
  }
});
