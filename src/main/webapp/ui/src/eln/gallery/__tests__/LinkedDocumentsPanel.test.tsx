import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/resizeObserver";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import galleryCatalog from "@/modules/common/i18n/locales/en-US/gallery.json";
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

  it("still shows the no-rows label when the response is genuinely empty", async () => {
    // Guards the other side of the noRowsLabel ternary: without this, collapsing the label to
    // a constant "" would keep the suite green while silently removing the empty state.
    server.use(
      http.get("/gallery/ajax/getLinkedDocuments/42", () =>
        HttpResponse.json({ data: [], error: null, success: true }),
      ),
    );

    renderPanel(file);

    expect(await screen.findByText("gallery:linkedDocumentsPanel.noRows")).toBeInTheDocument();
    expect(screen.queryByText(/linkedDocumentsPanel\.privateDocs/)).not.toBeInTheDocument();
  });

  // The suite runs i18n in cimode, where t() returns the bare key, so these render through a
  // real English i18n instance instead: the ICU plural and the ownerFullName interpolation in
  // linkedDocumentsPanel.privateDocs are only actually formatted this way.
  describe("private-row wording (real i18n)", () => {
    async function renderPanelWithRealI18n(galleryFile: GalleryFile) {
      const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      return renderWithRealI18n(
        <QueryClientProvider client={queryClient}>
          <LinkedDocumentsPanel file={galleryFile} />
        </QueryClientProvider>,
        { resources: { gallery: galleryCatalog }, defaultNS: "gallery" },
      );
    }

    it("names an unknown owner rather than trailing off after 'belonging to'", async () => {
      // splitLinkedRecords buckets a row with no ownerFullName under "", which would otherwise
      // render as "1 private doc belonging to " and use "" as the React key
      server.use(
        http.get("/gallery/ajax/getLinkedDocuments/42", () =>
          HttpResponse.json({ data: [{}], error: null, success: true }),
        ),
      );

      await renderPanelWithRealI18n(file);

      expect(await screen.findByText("1 private doc belonging to an unknown user")).toBeInTheDocument();
    });

    it("pluralises the per-owner private count", async () => {
      server.use(
        http.get("/gallery/ajax/getLinkedDocuments/42", () =>
          HttpResponse.json({
            data: [{ ownerFullName: "Grace Hopper" }, { ownerFullName: "Grace Hopper" }],
            error: null,
            success: true,
          }),
        ),
      );

      await renderPanelWithRealI18n(file);

      expect(await screen.findByText("2 private docs belonging to Grace Hopper")).toBeInTheDocument();
    });
  });
});
