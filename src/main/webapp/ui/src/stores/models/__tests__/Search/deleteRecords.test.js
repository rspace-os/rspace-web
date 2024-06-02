/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockContainer, containerAttrs } from "../ContainerModel/mocking";
import Search from "../../Search";
import InvApiService from "../../../../common/InvApiService";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import { type InventoryRecord } from "../../../definitions/InventoryRecord";

jest.mock("../../../../common/InvApiService", () => ({
  bulk: jest.fn(),
  query: jest.fn(),
}));

describe("action: deleteRecords", () => {
  describe("When it is called,", () => {
    test("the search should be refreshed.", async () => {
      jest.spyOn(InvApiService, "bulk").mockImplementation(() =>
        Promise.resolve({
          data: {
            results: [
              {
                error: null,
                record: containerAttrs(),
              },
            ],
          },
        })
      );

      const factory = mockFactory({
        newFactory: () =>
          mockFactory({
            newRecord: jest
              .fn<[any], InventoryRecord>()
              .mockImplementation((attrs) => makeMockContainer(attrs)),
          }),
      });

      const search = new Search({ factory });

      const searchSpy = jest
        .spyOn(search.fetcher, "search")
        .mockImplementation(() => Promise.resolve());

      await search.deleteRecords([makeMockContainer()]);

      /*
       * Being called with null means that the last used search parameters will
       * be used again
       */
      expect(searchSpy).toHaveBeenCalledWith(null, expect.any(Function));
    });
  });
});
