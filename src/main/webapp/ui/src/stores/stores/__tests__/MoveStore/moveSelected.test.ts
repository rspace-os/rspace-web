/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { containerAttrs } from "../../../models/__tests__/ContainerModel/mocking";
import getRootStore from "../../RootStore";
import Search from "../../../models/Search";
import InvApiService from "../../../../common/InvApiService";
import ContainerModel from "../../../models/ContainerModel";
import MemoisedFactory from "../../../models/Factory/MemoisedFactory";
import { type AxiosResponse } from "axios";

jest.mock("../../../../common/InvApiService", () => ({
  bulk: jest.fn().mockResolvedValue({}),
  get: jest.fn().mockResolvedValue({}),
  query: jest.fn().mockResolvedValue({}),
}));

describe("action: moveSelected", () => {
  describe("Moving the contents of a location into its current location should", () => {
    test("result in new records being allocated in memory.", async () => {
      const { searchStore, moveStore } = getRootStore();

      /*
       * 1. Setup SearchStore with an activeResult that is a grid container
       * with a selected location with content.
       */
      const locationContent = containerAttrs({ id: 2, globalId: "IC2" });
      const activeResult = new ContainerModel(new MemoisedFactory(), {
        ...containerAttrs(),
        cType: "GRID",
        gridLayout: {
          columnsNumber: 1,
          rowsNumber: 1,
          columnsLabelType: "ABC",
          rowsLabelType: "ABC",
        },
        locations: [
          {
            id: 1,
            coordX: 1,
            coordY: 1,
            content: locationContent,
          },
        ],
      });
      const preMoveContent: ContainerModel = activeResult.locations?.[0]
        .content as ContainerModel;
      jest.spyOn(InvApiService, "query").mockImplementation(
        () =>
          Promise.resolve({
            data: {
              ...containerAttrs(),
              cType: "GRID",
              gridLayout: {
                columnsNumber: 1,
                rowsNumber: 1,
                columnsLabelType: "ABC",
                rowsLabelType: "ABC",
              },
              locations: [
                { id: 1, coordX: 1, coordY: 1, content: locationContent },
              ],
              attachments: [],
              barcodes: [],
              _links: [],
            },
          }) as unknown as Promise<AxiosResponse<unknown>>
      );
      await searchStore.search.setActiveResult(activeResult);
      const location = activeResult.locations?.[0];
      if (!location) throw new Error("Location should exist");
      location.toggleSelected(true);
      expect(location.selected).toBe(true);
      expect(location.content?.selected).toBe(true);

      /*
       * 2. Setup move dialog to move selected location's content into its
       * current location
       */
      jest
        .spyOn(Search.prototype, "setSearchView")
        .mockImplementation(() => Promise.resolve());
      await moveStore.setIsMoving(true);
      moveStore.setSelectedResults([preMoveContent]);

      const destination = activeResult;
      jest
        .spyOn(destination.contentSearch.fetcher, "performInitialSearch")
        .mockImplementation(() => Promise.resolve());
      await moveStore.setTargetContainer(destination);

      activeResult.locations?.[0].toggleSelected(true);

      /*
       * 3. Complete and assert move operation
       */
      jest.spyOn(InvApiService, "bulk").mockImplementation(
        () =>
          Promise.resolve({
            status: 200,
            statusText: "OK",
            headers: {},
            config: {} as unknown,
            data: {
              errorCount: 0,
              results: [
                {
                  error: null,
                  record: { ...locationContent, attachments: [], _links: [] },
                },
              ],
              status: "COMPLETED",
              successCount: 1,
              successCountBeforeFirstError: 1,
            },
          }) as unknown as Promise<AxiosResponse<unknown>>
      );
      jest
        .spyOn(searchStore.search.fetcher, "performInitialSearch")
        .mockImplementation(() => Promise.resolve());

      await moveStore.moveSelected();

      const newLocation = activeResult.locations?.[0];
      expect(newLocation?.content).not.toBe(null);
      expect(newLocation?.content?.id).toEqual(preMoveContent.id);
      expect(newLocation?.content).not.toBe(preMoveContent);
      expect(activeResult.selectedLocations?.length).toBe(0);
      expect(activeResult.locations?.map((l) => l.content?.selected)).toEqual([
        false,
      ]);
    });
  });
});
