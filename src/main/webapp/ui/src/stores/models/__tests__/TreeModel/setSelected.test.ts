import { test, describe, expect, vi } from 'vitest';
import Search from "../../Search";
import { makeMockSubSample } from "../SubSampleModel/mocking";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../stores/RootStore", () => ({
  default: () => ({
    uiStore: {
      setVisiblePanel: () => {},
    },
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
    peopleStore: {
      currentUser: { username: "user" },
    },
  }),
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
      const setActiveResultSpy = vi
        .spyOn(search, "setActiveResult")
        .mockImplementation(() => Promise.resolve());
      const subsample = makeMockSubSample();
      const sample = subsample.sample;
      sample.newSampleSubSamplesCount = 1;
      sample.newSampleSubSampleTargetLocations = [
        { containerId: 1, location: { id: 1 } },
      ];
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
      const setActiveResultSpy = vi
        .spyOn(search, "setActiveResult")
        .mockImplementation(() => Promise.resolve());
      const subsample = makeMockSubSample();
      search.fetcher.setResults([subsample]);
      search.tree.setSelected(subsample.globalId);
      expect(setActiveResultSpy).toHaveBeenCalledWith(subsample);
    });
  });
});
});
