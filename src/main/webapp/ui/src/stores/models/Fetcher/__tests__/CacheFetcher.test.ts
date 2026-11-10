import { HttpResponse, http } from "msw";
import { expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { mockFactory } from "@/stores/definitions/__tests__/Factory/mocking";
import CacheFetcher from "../CacheFetcher";

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

test.each([10, 25])("filtering an empty container keeps the positive page size %i", async (pageSize) => {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get("*/api/inventory/v1/search", ({ request }) => {
      requests.push(new URL(request.url).searchParams);
      return HttpResponse.json({ records: [], totalHits: 0 });
    }),
  );
  const fetcher = new CacheFetcher(mockFactory(), { pageSize, parentGlobalId: "IC1" });

  await fetcher.performInitialSearch({ query: "no-matching-child*", parentGlobalId: "IC1" });

  expect(requests).toHaveLength(1);
  expect(requests[0].get("pageSize")).toBe(String(pageSize));
  expect(requests[0].get("parentGlobalId")).toBe("IC1");
  expect(fetcher.results).toEqual([]);
  expect(fetcher.error).toBe("");
});
