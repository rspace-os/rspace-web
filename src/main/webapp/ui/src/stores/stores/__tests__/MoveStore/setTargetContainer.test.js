/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockContainer } from "../../../models/__tests__/ContainerModel/mocking";
import MoveStore from "../../MoveStore";
import Search from "../../../models/Search";

jest.mock("../../../../common/InvApiService", () => ({
  query: jest.fn(),
})); // break import cycle

describe("action: setTargetContainer", () => {
  describe("When called, setTargetContainer should", () => {
    /*
     * Some buttons in the UI call setTargetContainer to set the selected
     * destination of a move operation. Through some chain of function
     * invocations, clearLocationsWithContentBeingMovedOut should be called so
     * that the locations in the target container that currently have items
     * being moved are cleared to make it clear to the user that they are able
     * to simply swap the locations of the items being moved.
     */
    test("cause clearLocationsWithContentBeingMovedOut to be called.", async () => {
      const container = makeMockContainer();
      // $FlowExpectedError[incompatible-call]
      const moveStore = new MoveStore({
        peopleStore: {
          currentUser: {
            bench: container,
          },
        },
        uiStore: {
          setDialogVisiblePanel: () => {},
        },
      });
      const clearLocationsSpy = jest.spyOn(
        moveStore,
        "clearLocationsWithContentBeingMovedOut"
      );
      jest
        .spyOn(Search.prototype, "setSearchView")
        .mockImplementation(() => {});
      await moveStore.setIsMoving(true);
      jest.spyOn(container, "fetchAdditionalInfo").mockImplementation(() => {});
      jest
        .spyOn(container.contentSearch.fetcher, "performInitialSearch")
        .mockImplementation(() => Promise.resolve());
      await moveStore.setTargetContainer(container);

      expect(clearLocationsSpy).toHaveBeenCalledWith(container);
    });
  });
});
