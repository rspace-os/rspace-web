import { beforeEach, describe, expect, it, vi } from "vitest";
import { getStructuredDocumentPreviewHtml } from "@/modules/workspace/documentPreview";

beforeEach(() => {
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

describe("getStructuredDocumentPreviewHtml", () => {
  it("requests the structured document preview endpoint with the numeric id", async () => {
    fetchMock.mockResponseOnce("<p>hello</p>");

    await getStructuredDocumentPreviewHtml(42);

    expect(fetchMock).toHaveBeenCalledWith(
      "/workspace/editor/structuredDocument/ajax/preview/42",
      expect.objectContaining({
        method: "GET",
        headers: { "X-Requested-With": "XMLHttpRequest" },
      }),
    );
  });

  it("returns the raw HTML fragment from the endpoint", async () => {
    fetchMock.mockResponseOnce("<div><table><tr><td>cell</td></tr></table></div>");

    const result = await getStructuredDocumentPreviewHtml(42);

    expect(result).toBe("<div><table><tr><td>cell</td></tr></table></div>");
  });

  it("throws when the response is not OK", async () => {
    fetchMock.mockResponseOnce("denied", {
      status: 403,
      statusText: "Forbidden",
    });

    await expect(getStructuredDocumentPreviewHtml(42)).rejects.toThrow();
  });
});
