import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type React from "react";
import { describe, expect, it, vi } from "vitest";
import { AboutRSpaceContent } from "../AboutRSpaceDialog";

vi.mock("@/modules/common/queries/applicationVersion", () => ({
  useApplicationVersionQuery: () => ({ data: "1.2.3" }),
}));

vi.mock("../../../hooks/api/useDeploymentProperty", () => ({
  useDeploymentProperty: () => ({ tag: "loading" }),
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("AboutRSpaceContent", () => {
  it("wires the static link labels and copyright keys", () => {
    render(<AboutRSpaceContent />, { wrapper });

    expect(screen.getByText("about:links.website")).toBeInTheDocument();
    expect(screen.getByText("about:links.changelog")).toBeInTheDocument();
    expect(screen.getByText("about:links.sourceCode")).toBeInTheDocument();
    expect(screen.getByText("about:copyright")).toBeInTheDocument();
  });

  it("wires the general support label key", () => {
    render(<AboutRSpaceContent />, { wrapper });

    expect(screen.getByText("about:support.generalLabel")).toBeInTheDocument();
    expect(screen.getByText("support@researchspace.com")).toBeInTheDocument();
  });
});
