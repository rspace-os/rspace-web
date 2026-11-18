import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import CoreFetcher, { parseCoreFetcherArgsFromUrl } from "../../Fetcher/CoreFetcher";
import Search, { DEFAULT_SEARCH_MODULES } from "../../Search";

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

vi.mock("../../../stores/SearchStore", () => ({ default: class {} }));

const BENZENE = { chemQuery: "c1ccccc1", chemSearchType: "SUBSTRUCTURE" as const };

const captureSearchRequests = (response: Record<string, unknown> = {}) => {
  const requests: URL[] = [];
  server.use(
    http.get("*/api/inventory/v1/search", ({ request }) => {
      requests.push(new URL(request.url));
      return HttpResponse.json({ totalHits: 0, records: [], ...response });
    }),
  );
  return requests;
};

describe("Structure search filter in the fetcher", () => {
  test("sends the structure to /search without narrowing a structure-only search to containers", async () => {
    const requests = captureSearchRequests();
    const fetcher = new CoreFetcher(mockFactory(), { resultType: "ALL", ...BENZENE });

    await fetcher.search(null, () => {});

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("chemQuery")).toBe("c1ccccc1");
    expect(requests[0].searchParams.get("chemSearchType")).toBe("SUBSTRUCTURE");
    expect(requests[0].searchParams.get("resultType")).toBeNull();
    expect(fetcher.resultType).toBe("ALL");
  });

  test("uses /search even when a type alone would pick a listing endpoint", () => {
    const fetcher = new CoreFetcher(mockFactory(), { resultType: "SAMPLE", ...BENZENE });
    fetcher.setEndpoint();
    expect(fetcher.endpoint).toBe("search");

    fetcher.setChemicalFilter(null);
    fetcher.setEndpoint();
    expect(fetcher.endpoint).toBe("samples");
  });

  test.each([true, false])("records textSearchLimitReached=%s from the response", async (limitReached) => {
    captureSearchRequests(limitReached ? { textSearchLimitReached: true } : {});
    const fetcher = new CoreFetcher(mockFactory(), { query: "buffer", ...BENZENE });

    await fetcher.search(null, () => {});

    expect(fetcher.textSearchLimitReached).toBe(limitReached);
  });

  test.each([
    [
      { chemQuery: "c1ccccc1", chemSearchType: "EXACT" },
      { chemQuery: "c1ccccc1", chemSearchType: "EXACT" },
    ],
    [{ chemQuery: "c1ccccc1" }, { chemQuery: "c1ccccc1", chemSearchType: "SUBSTRUCTURE" }],
    [
      { chemQuery: "c1ccccc1", chemSearchType: "nonsense" },
      { chemQuery: "c1ccccc1", chemSearchType: "SUBSTRUCTURE" },
    ],
  ])("parses %j from the URL", (urlParams, expected) => {
    expect(parseCoreFetcherArgsFromUrl(new URLSearchParams(urlParams))).toMatchObject(expected);
  });

  test("ignores a match type in the URL without a structure", () => {
    const args = parseCoreFetcherArgsFromUrl(new URLSearchParams({ chemSearchType: "EXACT" }));
    expect(args).not.toHaveProperty("chemQuery");
    expect(args).not.toHaveProperty("chemSearchType");
  });

  test("keeps the structure in the URL and in saved searches", () => {
    const fetcher = new CoreFetcher(mockFactory(), { chemQuery: "c1ccccc1", chemSearchType: "EXACT" });

    const query = fetcher.generateQuery({});
    expect(query.get("chemQuery")).toBe("c1ccccc1");
    expect(query.get("chemSearchType")).toBe("EXACT");
    expect(fetcher.serialize).toMatchObject({ chemQuery: "c1ccccc1", chemSearchType: "EXACT" });
  });

  test("leaves the URL clean without a structure filter", () => {
    const query = new CoreFetcher(mockFactory()).generateQuery({});
    expect(query.has("chemQuery")).toBe(false);
    expect(query.has("chemSearchType")).toBe(false);
  });
});

describe("Failed structure search", () => {
  test("is reported in the fetcher's error, not as a transient alert", async () => {
    server.use(
      http.get("*/api/inventory/v1/search", () =>
        HttpResponse.json({ message: "Structure search is not available" }, { status: 422 }),
      ),
    );
    const fetcher = new CoreFetcher(mockFactory(), BENZENE);

    await fetcher.search(null, () => {});

    expect(fetcher.error).toBe("inventory:search.feedback.chemicalSearchFailed");
    expect(fetcher.loading).toBe(false);
  });

  test("clears the error once a search succeeds", async () => {
    captureSearchRequests();
    const fetcher = new CoreFetcher(mockFactory(), { ...BENZENE, error: "earlier failure" });

    await fetcher.search(null, () => {});

    expect(fetcher.error).toBe("");
  });
});

describe("Structure search filter in the Search model", () => {
  test("sets and clears the filter on every fetcher", () => {
    const search = new Search({ factory: mockFactory() });

    search.setChemicalFilter(BENZENE, false);
    for (const fetcher of [search.staticFetcher, search.dynamicFetcher, search.cacheFetcher]) {
      expect(fetcher.chemQuery).toBe("c1ccccc1");
      expect(fetcher.chemSearchType).toBe("SUBSTRUCTURE");
    }

    search.setChemicalFilter(null, false);
    for (const fetcher of [search.staticFetcher, search.dynamicFetcher, search.cacheFetcher]) {
      expect(fetcher.chemQuery).toBeNull();
      expect(fetcher.chemSearchType).toBeNull();
    }
  });

  test("does not clear Requestable, which the UI blocks instead", () => {
    const search = new Search({ factory: mockFactory() });
    search.setRequestable(true, false);

    search.setChemicalFilter(BENZENE, false);

    expect(search.fetcher.requestable).toBe(true);
    expect(search.chemicalFilterConflict).toBe(true);
  });

  test.each(["IC1", "BA1", "SA1"])("reports a conflict while the search is scoped to %s", (parentGlobalId) => {
    const search = new Search({ factory: mockFactory() });
    expect(search.chemicalFilterConflict).toBe(false);

    search.setParentGlobalId(parentGlobalId, false);

    expect(search.chemicalFilterConflict).toBe(true);
  });

  test("treats a bench scope as no conflict, and clears it when a structure is applied", () => {
    const search = new Search({ factory: mockFactory() });
    search.setParentGlobalId("BE1", false);
    expect(search.chemicalFilterConflict).toBe(false);

    search.setChemicalFilter(BENZENE, false);

    // the Image and Grid views' fetcher ignores bench filters altogether (see CacheFetcher)
    for (const fetcher of [search.staticFetcher, search.dynamicFetcher]) {
      expect(fetcher.parentGlobalId).toBeNull();
      expect(fetcher.chemQuery).toBe("c1ccccc1");
    }
  });

  test("keeps a container scope when a structure is applied", () => {
    const search = new Search({ factory: mockFactory() });
    search.setParentGlobalId("IC1", false);

    search.setChemicalFilter(BENZENE, false);

    expect(search.fetcher.parentGlobalId).toBe("IC1");
  });

  test("is offered only where the CHEMISTRY module is enabled", () => {
    expect(DEFAULT_SEARCH_MODULES).not.toContain("CHEMISTRY");
    expect(new Search({ factory: mockFactory() }).showChemicalFilter).toBe(false);

    const mainSearch = new Search({
      factory: mockFactory(),
      uiConfig: { allowedSearchModules: new Set([...DEFAULT_SEARCH_MODULES, "CHEMISTRY"]) },
    });
    expect(mainSearch.showChemicalFilter).toBe(true);
  });
});

describe("Location column for structure hits", () => {
  test("replaces the third column while a structure filter is set", () => {
    const search = new Search({ factory: mockFactory() });
    expect(search.displayedAdjustableColumns).toEqual(["globalId", "owner", "lastModified"]);

    search.setChemicalFilter(BENZENE, false);
    expect(search.displayedAdjustableColumns).toEqual(["globalId", "owner", "currentLocation"]);

    search.setChemicalFilter(null, false);
    expect(search.displayedAdjustableColumns).toEqual(["globalId", "owner", "lastModified"]);
  });

  test("keeps a third column the user chose", () => {
    const search = new Search({ factory: mockFactory() });
    search.setAdjustableColumn("quantity", 2);

    search.setChemicalFilter(BENZENE, false);

    expect(search.displayedAdjustableColumns[2]).toBe("quantity");
  });
});
