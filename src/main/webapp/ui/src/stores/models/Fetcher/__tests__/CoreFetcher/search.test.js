/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";

import CoreFetcher from "../../CoreFetcher";
import { mockFactory } from "../../../../definitions/__tests__/Factory/mocking";
import { type Factory } from "../../../../definitions/Factory";
import InvApiService from "../../../../../common/InvApiService";
import "../../../../../__tests__/assertUrlSearchParams";

jest.mock("../../../../stores/RootStore", () => () => ({
  uiStore: {
    addAlert: () => {},
  },
}));
jest.mock("../../../../../common/InvApiService", () => ({
  query: jest.fn(
    () =>
      new Promise((resolve) =>
        resolve({
          data: {
            containers: [],
            totalHits: 0,
          },
        })
      )
  ),
}));

describe("search", () => {
  describe("When a new search is performed,", () => {
    test("a new factory should be created.", async () => {
      const mockNewFactory = jest.fn<[], Factory>();
      const factory = mockFactory({
        newFactory: mockNewFactory,
      });
      const fetcher = new CoreFetcher(factory, null);
      await fetcher.search(null, () => {});

      expect(mockNewFactory).toHaveBeenCalled();
    });
    test("and a page size is not specified, then 10 is passed in API call.", async () => {
      const querySpy = jest
        .spyOn(InvApiService, "query")
        .mockImplementation(() =>
          Promise.resolve({ data: { containers: [] } })
        );

      const mockNewFactory = jest.fn<[], Factory>();
      const factory = mockFactory({
        newFactory: mockNewFactory,
      });
      const fetcher = new CoreFetcher(factory, null);

      await fetcher.search({}, () => {});
      expect(querySpy).toHaveBeenCalledWith(
        "containers",
        // $FlowExpectedError[prop-missing]
        expect.urlSearchParamContaining({ pageSize: "10" })
      );
    });
  });
});
