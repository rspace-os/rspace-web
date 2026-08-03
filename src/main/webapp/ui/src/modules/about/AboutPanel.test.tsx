import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import * as React from "react";
import { describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import ErrorBoundary from "@/components/ErrorBoundary";
import AboutPanel from "./AboutPanel";

function renderAboutPanel() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ErrorBoundary>
        <React.Suspense fallback={<p>{"Loading…"}</p>}>
          <AboutPanel />
        </React.Suspense>
      </ErrorBoundary>
    </QueryClientProvider>,
  );
}

describe("AboutPanel", () => {
  it("renders version and deployment details from the public v2 config", async () => {
    server.use(
      http.get("/public/version", () => new HttpResponse("2.99.1")),
      http.get("/api/v2/config", () =>
        HttpResponse.json({
          branding: { bannerImageUrl: "/public/banner" },
          helpLinks: [],
          deploymentDescription: "Configured for advanced research teams",
          deploymentHelpEmail: "groups@example.com",
        }),
      ),
    );

    renderAboutPanel();

    expect(await screen.findByText("about:version.label")).toBeVisible();
    expect(screen.getByText("Configured for advanced research teams")).toBeVisible();
    expect(screen.getByRole("link", { name: "about:support.generalLink" })).toHaveAttribute(
      "href",
      "mailto:support@researchspace.com",
    );
    expect(screen.getByRole("link", { name: "about:support.accountsLink" })).toHaveAttribute(
      "href",
      "mailto:groups@example.com",
    );
  });

  it("keeps support information visible when the version query fails", async () => {
    // A suspense query that rejects throws to the error boundary; React logs the
    // boundary-caught error, so silence those expected console.error lines.
    const restoreConsole = silenceConsole(["error"], [/./]);

    server.use(
      http.get("/public/version", () => new HttpResponse(null, { status: 500 })),
      http.get("/api/v2/config", () =>
        HttpResponse.json({
          branding: { bannerImageUrl: "/public/banner" },
          helpLinks: [],
          deploymentDescription: "",
          deploymentHelpEmail: null,
        }),
      ),
    );

    renderAboutPanel();

    expect(await screen.findByText("about:version.unavailable")).toBeVisible();
    expect(screen.getByRole("link", { name: "about:support.generalLink" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "about:support.accountsLink" })).not.toBeInTheDocument();

    restoreConsole();
  });
});
