import { test, describe, expect, vi } from 'vitest';
import {
  makeMockContainer,
  containerAttrs,
} from "../../../models/__tests__/ContainerModel/mocking";
import MoveStore from "../../MoveStore";
import Search from "../../../models/Search";
import type { RootStore } from "../../RootStore";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  query: vi.fn(),
  }})); // break import cycle
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
      const container = makeMockContainer({
        parentContainers: [containerAttrs({ globalId: "IC2" })],
      });
      const moveStore = new MoveStore({
        peopleStore: {
          currentUser: {
            bench: container,
          },
        },
        uiStore: {
          setDialogVisiblePanel: () => {},
        },
      } as unknown as RootStore);
      const clearLocationsSpy = vi.spyOn(
        moveStore,
        "clearLocationsWithContentBeingMovedOut"
      );
      moveStore.search = {
        setActiveResult: async (record: typeof container | null) => {
          if (record) moveStore.clearLocationsWithContentBeingMovedOut(record);
        },
      } as unknown as Search;

      await moveStore.setTargetContainer(container);
      expect(clearLocationsSpy).toHaveBeenCalledWith(container);
    });
  });
});
