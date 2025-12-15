/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import { makeMockContainer, containerAttrs } from "./mocking";
import LocationModel from "../../LocationModel";
import { type ContainerAttrs } from "../../ContainerModel";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({}));

describe("computed: paramsForBackend", () => {
  /*
   * `paramsForBackend` is used for submitting the ContainerModel to the API and
   * as such must be JSON serialisable. Most significantly, this means not
   * having any cyclical memory references, which is verified by demonstrating
   * that a call to JSON.stringify does not throw any errors and returns a
   * valid string.
   */
  describe("paramsForBackend should be JSON serialisable when", () => {
    test("the container is a list container.", () => {
      const container = makeMockContainer();
      expect(JSON.stringify(container.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });

    test("the container is a visual container with a locations image.", () => {
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
      container.locationsImage = "a blob url";
      expect(JSON.stringify(container.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });

    test("the container is a grid container.", () => {
      const container = makeMockContainer({
        cType: "GRID",
        gridLayout: {
          columnsNumber: 2,
          rowsNumber: 3,
          columnsLabelType: "ABC",
          rowsLabelType: "ABC",
        },
        locationsCount: 6,
      });
      expect(JSON.stringify(container.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });

    test("the container has a parent container.", () => {
      const parent = makeMockContainer({
        id: 2,
        globalId: "IC2",
      }).paramsForBackend as unknown as ContainerAttrs;
      parent.parentContainers = [];
      parent.parentLocation = null;
      parent.lastNonWorkbenchParent = null;
      const container = makeMockContainer({
        parentContainers: [parent],
        parentLocation: null,
      });
      expect(JSON.stringify(container.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });
  });
});
