import { HttpResponse, http } from "msw";

const ALL_USER_TAGS_ENDPOINT = "/system/users/allUserTags";

/** Build `count` simple tag-value strings: tag-000, tag-001, ... (zero-padded). */
export function makeTags(count: number, start = 0): Array<string> {
  return Array.from({ length: count }, (_, i) => `tag-${String(start + i).padStart(3, "0")}`);
}

/**
 * MSW handler for the sysadmin user-tags endpoint (`/system/users/allUserTags`).
 * Returns a bare `string[]` (axios unwraps it into `response.data`), filtered
 * server-side by the `tagFilter` query param. Records each requested filter in
 * `requestedFilters` so a spec can assert that typing triggered a fetch.
 */
export function userTagsHandler({
  allTags,
  requestedFilters,
}: {
  allTags: Array<string>;
  requestedFilters: Array<string>;
}) {
  return http.get(ALL_USER_TAGS_ENDPOINT, ({ request }) => {
    const tagFilter = new URL(request.url).searchParams.get("tagFilter") ?? "";
    requestedFilters.push(tagFilter);
    return HttpResponse.json(allTags.filter((t) => t.includes(tagFilter)));
  });
}
