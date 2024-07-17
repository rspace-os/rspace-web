/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import Search from "../../Search";
import ApiServiceBase from "../../../../common/ApiServiceBase";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import "../../../../__tests__/assertUrlSearchParams";

jest.mock("../../../stores/RootStore", () => () => ({
  authStore: {
    isSynchronizing: false,
  },
  uiStore: {
    addAlert: jest.fn(),
  },
})); // break import cycle
jest.mock("../../../stores/SearchStore", () => {}); // break import cycle

describe("action: setPageSize", () => {
  describe("When called with any value it should", () => {
    test(" set the page number to 0.", () => {
      const search = new Search({
        factory: mockFactory(),
      });
      const querySpy = jest
        .spyOn(ApiServiceBase.prototype, "query")
        .mockImplementation(() =>
          Promise.resolve({ data: { containers: [] } })
        );

      void search.setPage(1);
      expect(querySpy).toHaveBeenCalledTimes(1);
      expect(querySpy).toHaveBeenCalledWith(
        "containers",
        // $FlowExpectedError[prop-missing]
        expect.urlSearchParamContaining({ pageNumber: "1" })
      );

      search.setPageSize(1);
      expect(querySpy).toHaveBeenCalledTimes(2);
      expect(querySpy).toHaveBeenCalledWith(
        "containers",
        // $FlowExpectedError[prop-missing]
        expect.urlSearchParamContaining({ pageNumber: "0" })
      );
    });
  });
});
