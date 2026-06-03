import { WORKSPACE_API_BASE_URL } from "@/modules/workspace/utils";

/**
 * Fetch the bare HTML content fragment for a structured document, via
 * `GET /workspace/editor/structuredDocument/ajax/preview/{id}`. The fragment has no
 * `<head>`/CSS; callers must sanitise (DOMPurify) and style it (the ELN content
 * stylesheets) before rendering. The id is the numeric DB id of the document.
 */
export async function getStructuredDocumentPreviewHtml(
  id: number,
): Promise<string> {
  const response = await fetch(
    `${WORKSPACE_API_BASE_URL}/editor/structuredDocument/ajax/preview/${id}`,
    {
      method: "GET",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Failed to fetch document preview: ${response.statusText || response.status}`,
    );
  }

  return response.text();
}
