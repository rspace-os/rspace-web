/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-explicit-any, @typescript-eslint/no-unsafe-call */
import { test, describe, expect, vi } from 'vitest';
import { makeMockContainer, containerAttrs } from "../ContainerModel/mocking";
import Search from "../../Search";
import InvApiService, {
  type BulkEndpointRecordSerialisation,
} from "../../../../common/InvApiService";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import { type AxiosResponse } from "axios";
import { mkAlert } from "../../../contexts/Alert";
vi.mock("../../../contexts/Alert", () => ({
  mkAlert: vi.fn(() => ({})),
}));
vi.mock("../../../../common/InvApiService", () => ({
  default: {
    bulk: vi.fn(),
    query: vi.fn(),
  },
}));
describe("action: deleteRecords", () => {
  describe("When it is called,", () => {
    test("the search should be refreshed.", async () => {
      vi
        .spyOn(InvApiService, "bulk")
        .mockImplementation(
          (
            _records: ReadonlyArray<BulkEndpointRecordSerialisation>,
            _operationType: string,
            _rollbackOnError: boolean
          ) =>
            Promise.resolve({
              data: {
                results: [
                  {
                    error: null,
                    record: containerAttrs(),
                  },
                ],
              },
              status: 200,
              statusText: "OK",
              headers: {},
              config: {},
            } as AxiosResponse)
        );
      const factory = mockFactory({
        newFactory: () =>
          mockFactory({
            newRecord: vi
              .fn()
              .mockImplementation((attrs: unknown) =>
                makeMockContainer(
                  attrs as Readonly<Partial<typeof containerAttrs>>
                )
              ),
          }),
      });
      const search = new Search({ factory });
      const searchSpy = vi
        .spyOn(search.fetcher as any, "search")
        .mockImplementation((...args: any[]) => {
          // Call the callback with empty results if it exists
          if (args[1] && typeof args[1] === "function") {
            args[1]([]);
          }
          return Promise.resolve();
        });
      await search.deleteRecords([makeMockContainer()]);
      /*
       * Being called with null means that the last used search parameters will
       * be used again
       */
      expect(searchSpy).toHaveBeenCalledWith(null, expect.any(Function));
    });
    test("and there is an error, there should be a helpful error message.", async () => {
      vi
        .spyOn(InvApiService, "bulk")
        .mockImplementation(
          (
            _records: ReadonlyArray<BulkEndpointRecordSerialisation>,
            _operationType: string,
            _rollbackOnError: boolean
          ) =>
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
              status: 200,
              statusText: "OK",
              headers: {},
              config: {},
            } as AxiosResponse)
        );
      const factory = mockFactory({
        newFactory: () =>
          mockFactory({
            newRecord: vi
              .fn()
              .mockImplementation((attrs: unknown) =>
                makeMockContainer(
                  attrs as Readonly<Partial<typeof containerAttrs>>
                )
              ),
          }),
      });
      const search = new Search({ factory });
      vi
        .spyOn(search.fetcher as any, "search")
        .mockImplementation((...args: any[]) => {
          // Call the callback with empty results if it exists
          if (args[1] && typeof args[1] === "function") {
            args[1]([]);
          }
          return Promise.resolve();
        });
      await search.deleteRecords([makeMockContainer()]);
      expect(mkAlert).toHaveBeenCalledWith(
        expect.objectContaining({
          details: expect.arrayContaining([
            expect.objectContaining({
              title: expect.stringContaining(
                "The container 'A list container' is not empty."
              ),
            }),
          ]),
        })
      );
    });
  });
});

