import { describe, expect, test, vi } from 'vitest';
import CoreFetcher from "../../CoreFetcher";
import { mockFactory } from "../../../../definitions/__tests__/Factory/mocking";
import { type Factory } from "../../../../definitions/Factory";
import InvApiService from "../../../../../common/InvApiService";
import "../../../../../__tests__/assertUrlSearchParams";
import { AxiosResponse } from "axios";
vi.mock("../../../../stores/RootStore", () => ({
  default: () => ({
  default: {
  uiStore: {
    addAlert: () => {},
  },
  }})
}));
vi.mock("../../../../../common/InvApiService", () => ({
  default: {
  query: vi.fn(
    () =>
      new Promise<AxiosResponse>((resolve) =>
        resolve({
          data: {
            containers: [],
            totalHits: 0,
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {} as any,
        })
      )
  ),
  }}));
describe("search", () => {
  describe("When a new search is performed,", () => {
    test("a new factory should be created.", async () => {
      const mockNewFactory = vi.fn().mockReturnValue({} as Factory);
      const factory = mockFactory({
        newFactory: mockNewFactory,
      });
      const fetcher = new CoreFetcher(factory, null);
      await fetcher.search(null, () => {});
      expect(mockNewFactory).toHaveBeenCalled();
    });
    test("and a page size is not specified, then 10 is passed in API call.", async () => {
      const querySpy = vi
        .spyOn(InvApiService, "query")
        .mockImplementation(() =>
          Promise.resolve({
            data: { containers: [] },
            status: 200,
            statusText: "OK",
            headers: {},
            config: {} as any,
          })
        );
      const mockNewFactory = vi.fn<() => Factory>().mockReturnValue({} as Factory);
      const factory = mockFactory({
        newFactory: mockNewFactory,
      });
      const fetcher = new CoreFetcher(factory, null);
      await fetcher.search({}, () => {});
      expect(querySpy).toHaveBeenCalledWith(
        "containers",
        expect.urlSearchParamContaining({ pageSize: "10" })
      );
    });
  });
});

