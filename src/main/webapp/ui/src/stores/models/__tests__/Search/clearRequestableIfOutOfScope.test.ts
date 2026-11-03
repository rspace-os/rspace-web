import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import ApiServiceBase from "../../../../common/ApiServiceBase";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import type { Person } from "../../../definitions/Person";
import Search from "../../Search";

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
    authStore: {
      isSynchronizing: false,
    },
    uiStore: {
      addAlert: vi.fn(),
    },
    moveStore: {
      setTargetContainer: vi.fn(),
    },
  }),
})); // break import cycle

vi.mock("../../../stores/SearchStore", () => ({ default: class {} })); // break import cycle

/**
 * Covers every shape CoreFetcher.search reads from, regardless of which endpoint a test ends up
 * calling (that varies with resultType/parentGlobalId, which is exactly what these tests change).
 */
function newSearchWithMockedQuery(): Search {
  const search = new Search({ factory: mockFactory() });
  vi.spyOn(ApiServiceBase.prototype, "query").mockImplementation(() =>
    Promise.resolve({
      data: { totalHits: 0, records: [], containers: [], samples: [], subSamples: [], templates: [], instruments: [] },
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      config: {} as any,
    }),
  );
  return search;
}

describe("Requestable is cleared once the search moves out of the scope it requires", () => {
  // Each change below starts its own search while setRequestable's own one (the mocked query
  // resolves on a microtask, after this describe block's synchronous body has already returned)
  // is still in flight, tripping CoreFetcher's own (expected, pre-existing) warning for that race
  // - restoring per-test wouldn't reliably outlast that microtask, so this silences it for the
  // file's duration instead.
  let restoreConsole: () => void;
  beforeEach(() => {
    restoreConsole = silenceConsole(["warn"], ["search.endpoint has changed"]);
  });
  afterEach(() => {
    restoreConsole();
  });

  test("switching the type filter away from Sample clears it", () => {
    const search = newSearchWithMockedQuery();
    search.setRequestable(true);
    expect(search.fetcher.requestable).toBe(true);

    search.setTypeFilter("ALL");
    expect(search.fetcher.requestable).toBeNull();
  });

  test("switching the type filter to Sample (a no-op for scope) leaves it set", () => {
    const search = newSearchWithMockedQuery();
    search.setRequestable(true);

    search.setTypeFilter("SAMPLE");
    expect(search.fetcher.requestable).toBe(true);
  });

  test("setting a parentGlobalId (e.g. opening a container) clears it", () => {
    const search = newSearchWithMockedQuery();
    search.setRequestable(true);

    search.setParentGlobalId("IC1");
    expect(search.fetcher.requestable).toBeNull();
  });

  test("choosing a bench clears it, since that also sets a parentGlobalId", () => {
    const search = newSearchWithMockedQuery();
    search.setRequestable(true);
    const user = {
      workbenchId: 5,
      username: "jsmith",
      bench: null,
      getBench: () => Promise.resolve(null),
    } as unknown as Person;

    search.setBench(user);
    expect(search.fetcher.requestable).toBeNull();
  });

  test("does nothing when requestable is already unset", () => {
    const search = newSearchWithMockedQuery();
    expect(search.fetcher.requestable).toBeNull();

    search.setTypeFilter("ALL");
    expect(search.fetcher.requestable).toBeNull();
  });
});
