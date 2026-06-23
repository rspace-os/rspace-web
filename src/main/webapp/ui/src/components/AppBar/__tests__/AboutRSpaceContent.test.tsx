import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type React from "react";
import { describe, expect, it } from "vitest";
import { AboutRSpaceContent } from "../AboutRSpaceDialog";

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("AboutRSpaceContent", () => {
  it("wires the static link labels and copyright keys", () => {
    render(<AboutRSpaceContent />, { wrapper });

    expect(screen.getByText("links.website")).toBeInTheDocument();
    expect(screen.getByText("links.changelog")).toBeInTheDocument();
    expect(screen.getByText("links.sourceCode")).toBeInTheDocument();
    expect(screen.getByText("copyright")).toBeInTheDocument();
  });

  it("wires the general support label key", () => {
    render(<AboutRSpaceContent />, { wrapper });

    expect(screen.getByText("support.generalLabel")).toBeInTheDocument();
    expect(screen.getByText("support@researchspace.com")).toBeInTheDocument();
  });
});
