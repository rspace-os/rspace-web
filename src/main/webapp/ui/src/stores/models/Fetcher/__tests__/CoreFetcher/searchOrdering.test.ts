import type { AxiosResponse } from "axios";
import { describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../../common/InvApiService";
import { mockFactory } from "../../../../definitions/__tests__/Factory/mocking";
import type { Factory } from "../../../../definitions/Factory";
import CoreFetcher from "../../CoreFetcher";

vi.mock("../../../../stores/getRootStore", () => ({
  default: () => ({ uiStore: { addAlert: () => {} } }),
}));
vi.mock("../../../../../common/InvApiService", () => ({
  default: { query: vi.fn() },
}));

/** A search response carrying `totalHits` containers, with no records to instantiate. */
function containersResponse(totalHits: number): AxiosResponse {
  return {
    data: { containers: [], totalHits },
    status: 200,
    statusText: "OK",
    headers: {},
    // biome-ignore lint/suspicious/noExplicitAny: matches the sibling search test's mock shape
    config: {} as any,
  };
}

function fetcherWithMockFactory(): CoreFetcher {
  const factory = mockFactory({
    newFactory: vi.fn<() => Factory>().mockReturnValue({} as Factory),
  });
  return new CoreFetcher(factory, null);
}

/**
 * The results a consumer sees come from this shared store, not from the promise it awaited, so a
 * component cannot impose response ordering itself. Without a request token the LAST RESPONSE TO
 * ARRIVE wins, which for a per-keystroke picker means a slow early query can overwrite the correct
 * results for what the user has actually typed.
 */
describe("search request ordering", () => {
  test("a slower earlier search does not overwrite the results of a later one", async () => {
    let resolveFirst: (r: AxiosResponse) => void = () => {};
    const first = new Promise<AxiosResponse>((resolve) => {
      resolveFirst = resolve;
    });
    vi.spyOn(InvApiService, "query")
      .mockImplementationOnce(() => first)
      .mockImplementationOnce(() => Promise.resolve(containersResponse(2)));

    const fetcher = fetcherWithMockFactory();
    const storedByFirst: Array<number> = [];
    const storedBySecond: Array<number> = [];

    // A is issued first but resolves second; B is issued second and resolves first.
    const a = fetcher.search({ pageNumber: 0 }, (r) => storedByFirst.push(r.length));
    const b = fetcher.search({ pageNumber: 1 }, (r) => storedBySecond.push(r.length));
    await b;
    resolveFirst(containersResponse(9));
    await a;

    expect(storedBySecond).toEqual([0]);
    // A's response arrived last and must be discarded: it was superseded before it landed.
    expect(storedByFirst).toEqual([]);
    expect(fetcher.count).toEqual(2);
  });

  test("a stale search does not clear the spinner while the live one is still running", async () => {
    let resolveSecond: (r: AxiosResponse) => void = () => {};
    const second = new Promise<AxiosResponse>((resolve) => {
      resolveSecond = resolve;
    });
    vi.spyOn(InvApiService, "query")
      .mockImplementationOnce(() => Promise.resolve(containersResponse(1)))
      .mockImplementationOnce(() => second);

    const fetcher = fetcherWithMockFactory();
    const a = fetcher.search({ pageNumber: 0 }, () => {});
    const b = fetcher.search({ pageNumber: 1 }, () => {});
    await a;

    // A has finished, but B is the live search, so the UI must still read as loading.
    expect(fetcher.loading).toBe(true);
    resolveSecond(containersResponse(3));
    await b;
    expect(fetcher.loading).toBe(false);
  });

  test("responses arriving in issue order still store normally", async () => {
    vi.spyOn(InvApiService, "query")
      .mockImplementationOnce(() => Promise.resolve(containersResponse(1)))
      .mockImplementationOnce(() => Promise.resolve(containersResponse(2)));

    const fetcher = fetcherWithMockFactory();
    const stored: Array<string> = [];
    await fetcher.search({ pageNumber: 0 }, () => stored.push("first"));
    await fetcher.search({ pageNumber: 1 }, () => stored.push("second"));

    expect(stored).toEqual(["first", "second"]);
    expect(fetcher.count).toEqual(2);
  });

  test("a stale FAILURE does not reset the search or alert about a search already replaced", async () => {
    // The gate on the catch block. Without it a superseded 404 called resetSearch() and raised an
    // alert about a query the user had already moved on from, wiping the live results (parallel
    // review, I19).
    let rejectFirst: (e: unknown) => void = () => {};
    const first = new Promise<AxiosResponse>((_resolve, reject) => {
      rejectFirst = reject;
    });
    vi.spyOn(InvApiService, "query")
      .mockImplementationOnce(() => first)
      .mockImplementationOnce(() => Promise.resolve(containersResponse(4)));

    const fetcher = fetcherWithMockFactory();
    const a = fetcher.search({ pageNumber: 0 }, () => {});
    const b = fetcher.search({ pageNumber: 1 }, () => {});
    await b;
    expect(fetcher.count).toEqual(4);

    rejectFirst(new Error("too late"));
    await a;

    // B's results survive A's failure, and the spinner stays off.
    expect(fetcher.count).toEqual(4);
    expect(fetcher.loading).toBe(false);
  });

  test("a live failure still resets the search", async () => {
    // The companion: the gate must not swallow the failure of the search that IS current.
    vi.spyOn(InvApiService, "query").mockImplementationOnce(() => Promise.reject(new Error("down")));
    const fetcher = fetcherWithMockFactory();
    await fetcher.search({ pageNumber: 0 }, () => {});
    expect(fetcher.loading).toBe(false);
    expect(fetcher.count).toEqual(0);
  });
});
