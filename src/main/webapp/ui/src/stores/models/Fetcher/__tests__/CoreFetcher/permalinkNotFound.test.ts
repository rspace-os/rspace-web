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
      Promise.reject({ response: { status: 404 } }),
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
