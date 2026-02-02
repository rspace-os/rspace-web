import { test, describe, expect, vi } from 'vitest';
import Search from "../../Search";
import ApiServiceBase from "../../../../common/ApiServiceBase";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import "../../../../__tests__/assertUrlSearchParams";

vi.mock("../../../stores/RootStore", () => ({
  default: () => ({
  authStore: {
    isSynchronizing: false,
  },
  uiStore: {
    addAlert: vi.fn(),
  },
})
})); // break import cycle
vi.mock("../../../stores/SearchStore", () => ({ default: class {} })); // break import cycle

describe("action: setPageSize", () => {
  describe("When called with any value it should", () => {
    test(" set the page number to 0.", () => {
      const search = new Search({
        factory: mockFactory(),
      });
      const querySpy = vi
        .spyOn(ApiServiceBase.prototype, "query")
        .mockImplementation(() =>
          Promise.resolve({
            data: { containers: [] },
            status: 200,
            statusText: "OK",
            headers: {},
            // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/no-unsafe-assignment
            config: {} as any,
          })
        );

      void search.setPage(1);
      expect(querySpy).toHaveBeenCalledTimes(1);
      expect(querySpy).toHaveBeenCalledWith(
        "containers",
        expect.urlSearchParamContaining({ pageNumber: "1" })
      );

      search.setPageSize(1);
      expect(querySpy).toHaveBeenCalledTimes(2);
      expect(querySpy).toHaveBeenCalledWith(
        "containers",
        expect.urlSearchParamContaining({ pageNumber: "0" })
      );
    });
  });
});

