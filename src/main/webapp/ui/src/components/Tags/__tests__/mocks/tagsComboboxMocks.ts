import { HttpResponse, http } from "msw";
import { FINAL_DATA_SIGNAL } from "../../ParseEncodedTagStrings";

const ONTOLOGY_ENDPOINT = "/workspace/editor/structuredDocument/userTagsAndOntologies";

/** Build `count` simple tag-value strings: tag-000, tag-001, ... (zero-padded). */
export function makeTags(count: number, start = 0): Array<string> {
  return Array.from({ length: count }, (_, i) => `tag-${String(start + i).padStart(3, "0")}`);
}

/**
 * MSW handler for the ontologies tag endpoint
 * (`/workspace/editor/structuredDocument/userTagsAndOntologies`). It serves a
 * paginated, server-side-filtered view of `allTags`, appending FINAL_DATA_SIGNAL
 * on the last page (which the component reads to stop requesting more pages).
 *
 * Every requested `pos` is pushed onto `requestedPositions` so a spec can assert
 * that scrolling near the end triggered a next-page fetch.
 */
export function ontologyTagsHandler({
  allTags,
  pageSize = 40,
  requestedPositions,
}: {
  allTags: Array<string>;
  pageSize?: number;
  requestedPositions: Array<number>;
}) {
  return http.get(ONTOLOGY_ENDPOINT, ({ request }) => {
    const url = new URL(request.url);
    const pos = Number(url.searchParams.get("pos") ?? "0");
    const tagFilter = url.searchParams.get("tagFilter") ?? "";
    requestedPositions.push(pos);

    const matching = tagFilter ? allTags.filter((t) => t.includes(tagFilter)) : allTags;
    const pageStart = pos * pageSize;
    const pageTags = matching.slice(pageStart, pageStart + pageSize);
    const isLastPage = pageStart + pageSize >= matching.length;
    // The component reads `data` off the JSON body: `.then(({ data }) => ...)`.
    return HttpResponse.json({ data: isLastPage ? [...pageTags, FINAL_DATA_SIGNAL] : pageTags });
  });
}
