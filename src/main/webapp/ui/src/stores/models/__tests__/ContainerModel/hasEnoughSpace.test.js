/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import { makeMockContainer, containerAttrs } from "./mocking";
import LocationModel from "../../LocationModel";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({
  moveStore: {
    selectedResults: [
      {
        globalId: "IC2",
      },
    ],
  },
}));

describe("computed: hasEnoughSpace", () => {
  /*
   * If an item is just being moved from one location to another inside a given
   * container then there is always going to be enough space for it.
   */
  test("There is enough space when moving an item around inside a container.", () => {
    const container = makeMockContainer({
      name: "A visual container",
      locations: [],
      cType: "IMAGE",
      locationsCount: 2,
      contentSummary: {
        totalCount: 2,
        subSampleCount: 0,
        containerCount: 2,
      },
    });
    const two = makeMockContainer({
      globalId: "IC2",
      id: 2,
      parentContainers: [containerAttrs({ globalId: "IC1" })],
    });
    const three = makeMockContainer({
      globalId: "IC3",
      id: 3,
      parentContainers: [containerAttrs({ globalId: "IC1" })],
    });
    container.locations = [
      new LocationModel({
        id: null,
        coordX: 1,
        coordY: 1,
        content: two,
        parentContainer: container,
      }),
      new LocationModel({
        id: null,
        coordX: 2,
        coordY: 2,
        content: three,
        parentContainer: container,
      }),
    ];
    expect(container.hasEnoughSpace).toBe(true);
  });

  test("There is enough space when adding an item to a container with a free slot.", () => {
    const container = makeMockContainer({
      name: "A visual container",
      cType: "IMAGE",
      locationsCount: 2,
      contentSummary: {
        totalCount: 1,
        subSampleCount: 0,
        containerCount: 1,
      },
    });
    const child = makeMockContainer({
      globalId: "IC3",
      id: 3,
      parentContainers: [containerAttrs({ globalId: "IC1" })],
    });
    container.locations = [
      new LocationModel({
        id: null,
        coordX: 2,
        coordY: 2,
        content: null,
        parentContainer: container,
      }),
      new LocationModel({
        id: null,
        coordX: 2,
        coordY: 2,
        content: child,
        parentContainer: container,
      }),
    ];
    expect(container.hasEnoughSpace).toBe(true);
  });

  test("There is not enough space when adding an item to a full container.", () => {
    const container = makeMockContainer({
      name: "A visual container",
      cType: "IMAGE",
      locationsCount: 2,
      contentSummary: {
        totalCount: 2,
        subSampleCount: 0,
        containerCount: 2,
      },
    });
    const three = makeMockContainer({
      globalId: "IC3",
      id: 3,
      parentContainers: [containerAttrs({ globalId: "IC1" })],
    });
    const four = makeMockContainer({
      globalId: "IC4",
      id: 4,
      parentContainers: [containerAttrs({ globalId: "IC1" })],
    });
    container.locations = [
      new LocationModel({
        id: null,
        coordX: 2,
        coordY: 2,
        content: three,
        parentContainer: container,
      }),
      new LocationModel({
        id: null,
        coordX: 2,
        coordY: 2,
        content: four,
        parentContainer: container,
      }),
    ];
    expect(container.hasEnoughSpace).toBe(false);
  });
});
