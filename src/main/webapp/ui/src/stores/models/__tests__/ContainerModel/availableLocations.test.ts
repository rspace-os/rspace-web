/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import { makeMockContainer } from "./mocking";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({}));

describe("computed: availableLocations", () => {
  test("List containers have infinite capacity.", () => {
    const container = makeMockContainer({ cType: "LIST" });
    expect(container.availableLocations).toEqual({
      isAccessible: true,
      value: Infinity,
    });
  });

  /*
   * Empty grid and visual containers should report the locationsCount, as
   * provided by the API, as the basis for the number of available spaces.
   */
  describe("Empty containers", () => {
    test("Empty grid containers return locationsCount, ignoring gridLayout.", () => {
      const container = makeMockContainer({
        cType: "GRID",
        gridLayout: {
          columnsNumber: 2,
          rowsNumber: 3,
          columnsLabelType: "ABC",
          rowsLabelType: "ABC",
        },
        locationsCount: 7,
      });
      expect(container.availableLocations).toEqual({
        isAccessible: true,
        value: 7,
      });
    });

    test("Empty visual containers return locationsCount, ignoring existance of locationsImage.", () => {
      const container = makeMockContainer({
        cType: "IMAGE",
        locationsCount: 7,
        //locationsImage defaults to null
      });
      expect(container.availableLocations).toEqual({
        isAccessible: true,
        value: 7,
      });
    });
  });

  /*
   * Containers which have some contents should correctly subtract that from
   * the locationsCount.
   */
  describe("Filled containers", () => {
    test("locationsCount - contentSummary.totalCount", () => {
      const container = makeMockContainer({
        cType: "GRID",
        locations: [],
        locationsCount: 7,
        contentSummary: {
          totalCount: 1,
          subSampleCount: 0,
          containerCount: 0,
        },
      });
      expect(container.availableLocations).toEqual({
        isAccessible: true,
        value: 6,
      });
    });
  });
});
