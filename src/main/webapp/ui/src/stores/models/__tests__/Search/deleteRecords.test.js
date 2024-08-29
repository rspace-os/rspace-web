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
import { mkAlert } from "../../../contexts/Alert";

jest.mock("../../../contexts/Alert", () => ({
  mkAlert: jest.fn(() => ({})),
}));

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
    test("and there is an error, there should be a helpful error message.", async () => {
      jest.spyOn(InvApiService, "bulk").mockImplementation(() =>
        Promise.resolve({
          data: {
            results: [
              {
                record: null,
                error: {
                  status: "BAD_REQUEST",
                  httpCode: 400,
                  internalCode: 40002,
                  message: "Errors detected : 1",
                  messageCode: null,
                  errors: ["container.deletion.failure.not.empty"],
                  iso8601Timestamp: "2024-08-29T08:57:41.817794Z",
                  data: null,
                },
              },
            ],
            successCount: 0,
            successCountBeforeFirstError: 0,
            errorCount: 1,
            status: "COMPLETED",
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

      jest
        .spyOn(search.fetcher, "search")
        .mockImplementation(() => Promise.resolve());

      await search.deleteRecords([makeMockContainer()]);

      expect(mkAlert).toHaveBeenCalledWith(
        expect.objectContaining({
          details: expect.arrayContaining([
            expect.objectContaining({
              title: "The container 'A list container' is not empty.",
            }),
          ]),
        })
      );
    });
  });
});
