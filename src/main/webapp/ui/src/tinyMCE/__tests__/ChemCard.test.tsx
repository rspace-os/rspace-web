import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import { server } from "@/__tests__/mswServer";
import { FEATURE_FLAGS } from "@/featureFlags/generatedFeatureFlags";
import ChemCard from "../ChemCard";

const bondStrengthsButton = { name: "workspace:tinymce.bondStrengths.action" };

function renderCard({ bondStrengthsEnabled }: { bondStrengthsEnabled: boolean }) {
  server.use(
    http.get("/chemical/ajax/getInfo", () =>
      HttpResponse.json({ data: { reactants: [], products: [], molecules: [{ name: "toluene", formula: "C7H8" }] } }),
    ),
    http.get("/api/v2/feature-flags", () =>
      HttpResponse.json(
        v2ListEnvelope([
          {
            name: FEATURE_FLAGS.bondStrengthsEnabled,
            value: bondStrengthsEnabled,
            baselineValue: false,
            source: "USER_OVERRIDE",
            canOverride: true,
          },
        ]),
      ),
    ),
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ChemCard item={{ id: "42" }} />
    </QueryClientProvider>,
  );
  return queryClient;
}

describe("ChemCard bond strengths action", () => {
  it("offers to calculate bond strengths when the feature flag is on", async () => {
    renderCard({ bondStrengthsEnabled: true });

    expect(await screen.findByRole("button", bondStrengthsButton)).toBeVisible();
  });

  it("does not offer bond strengths when the feature flag is off", async () => {
    const queryClient = renderCard({ bondStrengthsEnabled: false });

    expect(await screen.findByText("toluene")).toBeVisible();
    await waitFor(() => {
      expect(queryClient.getQueryState(["rspace.featureFlags", "flags"])?.status).toBe("success");
    });
    expect(screen.queryByRole("button", bondStrengthsButton)).not.toBeInTheDocument();
  });
});
