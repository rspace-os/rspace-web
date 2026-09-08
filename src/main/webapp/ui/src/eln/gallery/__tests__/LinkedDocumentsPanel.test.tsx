import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/resizeObserver";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { LinkedDocumentsPanel } from "../components/LinkedDocumentsPanel";
import type { GalleryFile } from "../useGalleryListing";

const file = { id: 42 } as GalleryFile;

function renderPanel(galleryFile: GalleryFile) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <LinkedDocumentsPanel file={galleryFile} />
    </QueryClientProvider>,
  );
}

describe("LinkedDocumentsPanel", () => {
  it("does not show the no-rows label when only private linked documents exist", async () => {
    server.use(
      http.get("/gallery/ajax/getLinkedDocuments/42", () =>
        HttpResponse.json({
          data: [{ ownerFullName: "Grace Hopper" }],
          error: null,
          success: true,
        }),
      ),
    );

    renderPanel(file);

    // the per-owner private count renders...
    await screen.findByText(/linkedDocumentsPanel\.privateDocs/);
    // ...so the empty-state label must not simultaneously claim there are no linked documents
    expect(screen.queryByText(/linkedDocumentsPanel\.noRows/)).not.toBeInTheDocument();
  });
});
