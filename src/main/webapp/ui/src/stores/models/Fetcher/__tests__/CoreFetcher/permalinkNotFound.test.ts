import { describe, expect, test, vi } from "vitest";
import CoreFetcher from "../../CoreFetcher";
import { mockFactory } from "../../../../definitions/__tests__/Factory/mocking";
import InvApiService from "../../../../../common/InvApiService";

vi.mock("../../../../stores/RootStore", () => ({
  default: () => ({
    uiStore: {
      addAlert: () => {},
    },
  }),
}));
vi.mock("../../../../../common/InvApiService", () => ({
  default: {
    get: vi.fn(),
    query: vi.fn(),
  },
}));

describe("permalinkNotFound", () => {
  test("a failed versioned permalink fetch records which version was not found", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.reject(
        Object.assign(new Error("Not Found"), { response: { status: 404 } }),
      ),
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
      .mockImplementation(() =>
        Promise.reject(
          Object.assign(new Error("Not Found"), { response: { status: 404 } }),
        ),
      );
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
      Promise.reject(
        Object.assign(new Error("Server Error"), { response: { status: 500 } }),
      ),
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
    const fetcher = new CoreFetcher(
      mockFactory({ newFactory: () => factory }),
      null,
    );

    await fetcher.search(
      {
        permalink: { type: "subsample", id: 9, version: 2 },
      },
      () => {},
    );

    expect(fetcher.permalinkNotFound).toBeNull();
  });
});
