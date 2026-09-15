import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";

import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import galleryEn from "@/modules/common/i18n/locales/en-US/gallery.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import materialTheme from "../../../../../theme";

import RelatedInventoryItems from "../RelatedInventoryItems";

async function renderSection() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return renderWithRealI18n(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>
        <RelatedInventoryItems globalId="SD1" recordType="document" />
      </QueryClientProvider>
    </ThemeProvider>,
    { resources: { gallery: galleryEn, inventory: inventoryEn }, defaultNS: "inventory" },
  );
}

describe("RelatedInventoryItems", () => {
  afterEach(() => vi.restoreAllMocks());

  it("renders one row per referencing item", async () => {
    server.use(
      http.get("/workspace/getReferencingInventoryItems/SD1", () =>
        HttpResponse.json({
          referencingItems: [
            { sourceGlobalId: "SA7", sourceName: "sample A", sourceType: "SAMPLE", relationType: "References" },
            { sourceGlobalId: "IC9", sourceName: "box B", sourceType: "CONTAINER", relationType: "Cites" },
          ],
        }),
      ),
    );

    await renderSection();

    expect(await screen.findAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("SA7")).toBeInTheDocument();
    expect(screen.getByText("IC9")).toBeInTheDocument();
  });

  it("keeps row keys unique when one source links to the record twice", async () => {
    // the endpoint returns one row per link FIELD, so a single source item
    // linking through two fields legitimately repeats its sourceGlobalId;
    // keying rows on globalId alone would collide
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    server.use(
      http.get("/workspace/getReferencingInventoryItems/SD1", () =>
        HttpResponse.json({
          referencingItems: [
            { sourceGlobalId: "SA7", sourceName: "sample A", sourceType: "SAMPLE", relationType: "References" },
            { sourceGlobalId: "SA7", sourceName: "sample A", sourceType: "SAMPLE", relationType: "Cites" },
          ],
        }),
      ),
    );

    await renderSection();

    expect(await screen.findAllByRole("listitem")).toHaveLength(2);
    expect(errorSpy).not.toHaveBeenCalled();
  });
});
