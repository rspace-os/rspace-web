import { describe, expect, test, vi } from 'vitest';
import { makeMockSample, makeMockSampleWithASubsample } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
}));

describe("computed: paramsForBackend", () => {
  /*
   * `paramsForBackend` is used for submitting the SampleModel to the API and as
   * such must be JSON serialisable. Most significantly, this means not having
   * any cyclical memory references, which is verified by demonstrating that a
   * call to JSON.stringify does not throw any errors and returns a valid
   * string.
   */
  describe("paramsForBackend should be JSON serialisable when", () => {
    test("the Sample has no subsamples.", () => {
      const Sample = makeMockSample();
      expect(JSON.stringify(Sample.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });

    test("the Sample has a subsample.", () => {
      const Sample = makeMockSampleWithASubsample();
      expect(JSON.stringify(Sample.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });
  });
});


