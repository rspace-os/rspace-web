/*
 */
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

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
    it("a new factory should be created.", async () => {
      const mockNewFactory = vi.fn<any, any>().mockReturnValue({} as Factory);
      const factory = mockFactory({
        newFactory: mockNewFactory,
      });
      const fetcher = new CoreFetcher(factory, null);
      await fetcher.search(null, () => {});

      expect(mockNewFactory).toHaveBeenCalled();
    });
    it("and a page size is not specified, then 10 is passed in API call.", async () => {
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

      const mockNewFactory = vi.fn<any, any>().mockReturnValue({} as Factory);
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


