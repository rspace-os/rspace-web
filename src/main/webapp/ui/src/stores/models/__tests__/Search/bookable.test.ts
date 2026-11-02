import { autorun, runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import CoreFetcher, { parseCoreFetcherArgsFromUrl } from "../../Fetcher/CoreFetcher";
import Search from "../../Search";

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

vi.mock("../../../stores/SearchStore", () => ({ default: class {} }));

describe("Inventory bookable filter", () => {
  test.each([true, false])("retains all item types when bookable=%s is the only filter", async (bookable) => {
    const requests: URL[] = [];
    server.use(
      http.get("*/api/inventory/v1/search", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json({ totalHits: 0, records: [] });
      }),
    );
    const fetcher = new CoreFetcher(mockFactory(), { resultType: "ALL", bookable });
    await fetcher.search(null, () => {});
    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get("resultType")).toBeNull();
    expect(requests[0].searchParams.get("bookable")).toBe(String(bookable));
    expect(fetcher.resultType).toBe("ALL");
  });

  test("resets observed pagination inside an action when changing filters", () => {
    const search = new Search({ factory: mockFactory(), fetcherParams: { pageNumber: 2 } });
    runInAction(() => {
      search.overrideSearchOnFilter = vi.fn();
    });
    const stopObserving = autorun(() => {
      void search.fetcher.pageNumber;
    });
    const warning = vi.spyOn(console, "warn").mockImplementation(() => {});
    try {
      search.setBookable(true);
      expect(search.fetcher.pageNumber).toBe(0);
      expect(warning).not.toHaveBeenCalled();
    } finally {
      stopObserving();
      warning.mockRestore();
    }
  });

  test.each([true, false])("uses the filtering endpoint for bookable=%s without a text query", (bookable) => {
    const fetcher = new CoreFetcher(mockFactory(), { resultType: "INSTRUMENT", bookable });
    fetcher.setEndpoint();
    expect(fetcher.endpoint).toBe("search");
    fetcher.setBookable(null);
    fetcher.setEndpoint();
    expect(fetcher.endpoint).toBe("instruments");
  });

  test.each([
    ["true", true],
    ["false", false],
    ["invalid", undefined],
  ])("parses bookable=%s without treating false as absent", (value, expected) => {
    expect(parseCoreFetcherArgsFromUrl(new URLSearchParams({ bookable: value })).bookable).toBe(expected);
  });

  test("preserves false and explicit clearing when replacing a previous filter", () => {
    const fetcher = new CoreFetcher(mockFactory());
    expect(fetcher.applySearchParams({ bookable: true }).bookable).toBe(true);
    expect(fetcher.applySearchParams({ bookable: false }).bookable).toBe(false);
    expect(fetcher.applySearchParams({ bookable: null }).bookable).toBeNull();
  });
});

describe("Bookable and Requestable filters", () => {
  test.each([true, false])("setting bookable=%s clears Requestable", (bookable) => {
    const search = new Search({ factory: mockFactory() });
    search.setRequestable(true, false);

    search.setBookable(bookable, false);

    for (const fetcher of [search.staticFetcher, search.dynamicFetcher, search.cacheFetcher]) {
      expect(fetcher.bookable).toBe(bookable);
      expect(fetcher.requestable).toBeNull();
    }
  });

  test("clearing Bookable leaves Requestable set", () => {
    const search = new Search({ factory: mockFactory() });
    search.setRequestable(true, false);

    search.setBookable(null, false);

    expect(search.fetcher.requestable).toBe(true);
  });

  test.each([true, false])("setting Requestable clears bookable=%s", (bookable) => {
    const search = new Search({ factory: mockFactory() });
    search.setBookable(bookable, false);

    search.setRequestable(true, false);

    for (const fetcher of [search.staticFetcher, search.dynamicFetcher, search.cacheFetcher]) {
      expect(fetcher.requestable).toBe(true);
      expect(fetcher.bookable).toBeNull();
    }
  });

  test.each(["true", "false"])("keeps Requestable when a URL also carries bookable=%s", (bookable) => {
    const args = parseCoreFetcherArgsFromUrl(new URLSearchParams({ requestable: "true", bookable }));

    expect(args.requestable).toBe(true);
    expect(args).not.toHaveProperty("bookable");
  });
});
