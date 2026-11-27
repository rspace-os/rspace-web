import { beforeEach, describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../../common/InvApiService";
import { mockFactory } from "../../../../definitions/__tests__/Factory/mocking";
import type { InventoryRecord } from "../../../../definitions/InventoryRecord";
import { sampleAttrs } from "../../../__tests__/SampleModel/mocking";
import { subsampleAttrs } from "../../../__tests__/SubSampleModel/mocking";
import AlwaysNewFactory from "../../../Factory/AlwaysNewFactory";
import Search from "../../../Search";
import CoreFetcher from "../../CoreFetcher";

const { addAlert } = vi.hoisted(() => ({ addAlert: vi.fn() }));
vi.mock("../../../../stores/getRootStore", () => ({
  default: () => ({
    uiStore: { addAlert },
    trackingStore: { trackEvent: vi.fn() },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));
vi.mock("../../../../../common/InvApiService", () => ({
  default: {
    get: vi.fn(),
    query: vi.fn(),
  },
}));

describe("permalinkNotFound", () => {
  beforeEach(() => addAlert.mockClear());

  test("a failed versioned permalink fetch records which version was not found", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.reject(Object.assign(new Error("Not Found"), { response: { status: 404 } })),
    );
    const fetcher = new CoreFetcher(mockFactory(), null);

    await fetcher.search(
      {
        permalink: { type: "subsample", id: 9, version: 2 },
      },
      () => {},
    );

    expect(fetcher.permalinkNotFound).toEqual({
      type: "subsample",
      id: 9,
      version: 2,
    });
  });

  test("version 0 fetches the versioned endpoint, not the live record", async () => {
    // `version != null`, not truthiness: ?version=0 must 404, never silently
    // show the live record
    const getSpy = vi
      .spyOn(InvApiService, "get")
      .mockImplementation(() => Promise.reject(Object.assign(new Error("Not Found"), { response: { status: 404 } })));
    const fetcher = new CoreFetcher(mockFactory(), null);

    await fetcher.search(
      {
        permalink: { type: "subsample", id: 9, version: 0 },
      },
      () => {},
    );

    expect(getSpy).toHaveBeenCalledWith("subSamples", "9/versions/0");
    expect(fetcher.permalinkNotFound).toEqual({
      type: "subsample",
      id: 9,
      version: 0,
    });
  });

  test("a transient failure does not claim the record was not found", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.reject(Object.assign(new Error("Server Error"), { response: { status: 500 } })),
    );
    const fetcher = new CoreFetcher(mockFactory(), null);

    await fetcher.search(
      {
        permalink: { type: "subsample", id: 9, version: 2 },
      },
      () => {},
    );

    // the generic error alert fires, but the not-found panel must not
    expect(fetcher.permalinkNotFound).toBeNull();
    expect(addAlert).toHaveBeenCalledTimes(1);
  });

  test("a successful permalink fetch leaves no not-found state", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve({
        data: { globalId: "SS9", id: 9 },
      } as never),
    );
    const factory = mockFactory({
      newRecord: vi.fn().mockImplementation(() => ({ deleted: false })),
    });
    const fetcher = new CoreFetcher(mockFactory({ newFactory: () => factory }), null);

    await fetcher.search(
      {
        permalink: { type: "subsample", id: 9, version: 2 },
      },
      () => {},
    );

    expect(fetcher.permalinkNotFound).toBeNull();
  });

  test("a permalink 404 raises no error toast: the not-found panel says it all", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.reject(Object.assign(new Error("Not Found"), { response: { status: 404 } })),
    );
    const fetcher = new CoreFetcher(mockFactory(), null);

    await fetcher.search({ permalink: { type: "sample", id: 6, version: 7 } }, () => {});

    expect(fetcher.permalinkNotFound).toEqual({ type: "sample", id: 6, version: 7 });
    expect(addAlert).not.toHaveBeenCalled();
  });

  test("a 404 that is not a permalink still raises the error toast", async () => {
    vi.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.reject(Object.assign(new Error("Not Found"), { response: { status: 404 } })),
    );
    const fetcher = new CoreFetcher(mockFactory(), null);

    await fetcher.search({ query: "anything" }, () => {});

    expect(fetcher.permalinkNotFound).toBeNull();
    expect(addAlert).toHaveBeenCalledTimes(1);
  });
});

test.each([
  { type: "sample" as const, payload: sampleAttrs() },
  { type: "subsample" as const, payload: { ...subsampleAttrs(), sample: sampleAttrs() } },
])("selects a ready $type permalink after one request", async ({ type, payload }) => {
  const get = vi
    .spyOn(InvApiService, "get")
    .mockClear()
    .mockResolvedValue({ data: payload } as never);
  const query = vi
    .spyOn(InvApiService, "query")
    .mockClear()
    .mockResolvedValue({ data: payload } as never);
  const factory = new AlwaysNewFactory();
  const fetcher = new CoreFetcher(factory, null);
  let selected: InventoryRecord | null = null;
  await fetcher.search({ permalink: { type, id: 1, version: null } }, (records) => {
    selected = records[0] ?? null;
  });
  const search = new Search({ factory, callbacks: { setActiveResult: () => {} } });
  await search.setActiveResult(selected);
  expect(search.activeResult?.infoLoaded).toBe(true);
  if (type === "sample") {
    expect(query).toHaveBeenCalledTimes(1);
    expect(query.mock.calls[0][1].get("includeSubSamples")).toBe("false");
    expect(get).not.toHaveBeenCalled();
  } else {
    expect(get).toHaveBeenCalledTimes(1);
    expect(query).not.toHaveBeenCalled();
  }
});

test("selecting a sample summary still requests details", async () => {
  const factory = new AlwaysNewFactory();
  const summary = { ...sampleAttrs(), globalId: "SA1" };
  Reflect.deleteProperty(summary, "subSamples");
  const query = vi
    .spyOn(InvApiService, "query")
    .mockClear()
    .mockResolvedValue({ data: sampleAttrs() } as never);
  const search = new Search({ factory });
  await search.setActiveResult(factory.newRecord(summary));
  expect(search.activeResult?.infoLoaded).toBe(true);
  expect(query).toHaveBeenCalledTimes(1);
});
