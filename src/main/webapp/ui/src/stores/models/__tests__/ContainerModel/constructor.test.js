/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";

import ContainerModel from "../../ContainerModel";
import { containerAttrs } from "./mocking";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import { type Factory } from "../../../definitions/Factory";
import { type InventoryRecord } from "../../../definitions/InventoryRecord";

jest.mock("../../../stores/RootStore", () => () => ({
  peopleStore: {},
})); // break import cycle

function mockContainerWithTwoContents(factory: Factory) {
  const attrs = containerAttrs();
  attrs.locations = [
    {
      content: containerAttrs({
        id: 2,
        globalId: "IC2",
        parentContainers: [containerAttrs()],
      }),
      coordX: 1,
      coordY: 1,
      id: 1,
    },
    {
      content: containerAttrs({
        id: 3,
        globalId: "IC3",
        parentContainers: [containerAttrs()],
      }),
      coordX: 2,
      coordY: 1,
      id: 2,
    },
  ];

  return factory.newRecord(attrs);
}

describe("constructor", () => {
  /*
   * The ContainerModel's constructor is passed a Factory which is used to
   * instantiate all the contents of its locations and their respective parent
   * containers. These tests assert that ContainerModel passes around the
   * Factory it is given correctly.
   */
  describe("Factory argument", () => {
    test("should be used in the instantiation of all child records.", () => {
      let factory;
      const mockNewRecord = jest
        .fn<[any], InventoryRecord>()
        .mockImplementation((attrs) => new ContainerModel(factory, attrs));
      const f: () => Factory = () =>
        mockFactory({
          newRecord: mockNewRecord,
          newFactory: jest.fn<[], Factory>().mockImplementation(f),
        });
      factory = f();
      mockContainerWithTwoContents(factory);

      expect(mockNewRecord).toBeCalledTimes(7);

      // the root container
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        1,
        expect.objectContaining({ globalId: "IC1" })
      );

      // the first child, and it's parent (i.e. the root)
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining({ globalId: "IC2" })
      );
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        3,
        expect.objectContaining({ globalId: "IC1" })
      );
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        4,
        expect.objectContaining({ globalId: "IC1" })
      );

      // the second child and it's parent (i.e. the root)
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        5,
        expect.objectContaining({ globalId: "IC3" })
      );
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        6,
        expect.objectContaining({ globalId: "IC1" })
      );
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        7,
        expect.objectContaining({ globalId: "IC1" })
      );
    });
  });
});
