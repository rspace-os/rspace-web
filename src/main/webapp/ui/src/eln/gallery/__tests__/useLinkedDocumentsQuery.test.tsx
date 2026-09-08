import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { useLinkedDocumentsQuery } from "../queries";
import type { GalleryFile } from "../useGalleryListing";

const ENDPOINT = "/gallery/ajax/getLinkedDocuments/:id";
const file = { id: 42 } as GalleryFile;

function renderLinkedDocuments(galleryFile: GalleryFile) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderHook(() => useLinkedDocumentsQuery(galleryFile), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe("useLinkedDocumentsQuery", () => {
  it("splits readable rows from owner-only private placeholders (RSDEV-1329)", async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json({
          data: [
            { id: 11, oid: { idString: "SD11" }, name: "Doc one", ownerFullName: "Ada Lovelace" },
            { ownerFullName: "Grace Hopper" },
            { ownerFullName: "Grace Hopper" },
          ],
          error: null,
          success: true,
        }),
      ),
    );

    const { result } = renderLinkedDocuments(file);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.documents).toEqual([
      expect.objectContaining({ id: 11, globalId: "SD11", name: "Doc one", permalinkHref: "/globalId/SD11" }),
    ]);
    expect(result.current.data?.privateByOwner).toEqual([{ ownerFullName: "Grace Hopper", count: 2 }]);
  });

  it("skips the request entirely for folders and snippets", async () => {
    // The endpoint rejects non-media-file ids (fail-closed, RSDEV-1329), so querying it for a
    // folder or snippet would surface a spurious error where an empty state belongs. No MSW
    // handler is registered here: any request would fail the strict unhandled-request check.
    const folder = { id: 42, isFolder: true } as GalleryFile;

    const { result } = renderLinkedDocuments(folder);

    await waitFor(() => expect(result.current.fetchStatus).toBe("idle"));
    expect(result.current.isError).toBe(false);
    expect(result.current.data).toBeUndefined();
  });
});
