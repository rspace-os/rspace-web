/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import Search from "../../Search";
import { makeMockSubSample } from "../SubSampleModel/mocking";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({
  uiStore: {
    setVisiblePanel: () => {},
  },
}));

describe("method: setSelected", () => {
  /*
   * Branch nodes are those that have child nodes, like Containers and Samples.
   */
  describe("When a branch node is selected it should", () => {
    test("become the active result.", () => {
      const search = new Search({
        factory: mockFactory(),
      });
      const setActiveResultSpy = jest
        .spyOn(search, "setActiveResult")
        .mockImplementation(() => Promise.resolve());

      const subsample = makeMockSubSample();
      const sample = subsample.sample;
      search.fetcher.setResults([sample]);
      search.tree.setSelected(sample.globalId);
      expect(setActiveResultSpy).toHaveBeenCalledWith(sample);
    });
  });

  /*
   * Leaf nodes are those that don't have child nodes, like SubSamples.
   */
  describe("When a leaf node is selected it should", () => {
    test("become the active result.", () => {
      const search = new Search({
        factory: mockFactory(),
      });
      const setActiveResultSpy = jest
        .spyOn(search, "setActiveResult")
        .mockImplementation(() => Promise.resolve());

      const subsample = makeMockSubSample();
      search.fetcher.setResults([subsample]);
      search.tree.setSelected(subsample.globalId);
      expect(setActiveResultSpy).toHaveBeenCalledWith(subsample);
    });
  });
});
