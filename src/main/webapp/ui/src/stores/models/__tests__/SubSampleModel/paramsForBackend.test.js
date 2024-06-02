/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import {
  makeMockSubSample,
  makeMockSubSampleWithParentContainer,
} from "./mocking";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({}));

describe("computed: paramsForBackend", () => {
  /*
   * `paramsForBackend` is used for submitting the SubSampleModel to the API and
   * as such must be JSON serialisable. Most significantly, this means not
   * having any cyclical memory references, which is verified by demonstrating
   * that a call to JSON.stringify does not throw any errors and returns a
   * valid string.
   */
  describe("paramsForBackend should be JSON serialisable when", () => {
    test("the subSample is on the bench.", () => {
      const subSample = makeMockSubSample();
      expect(JSON.stringify(subSample.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });

    test("the subSample has a parent container.", () => {
      const subSample = makeMockSubSampleWithParentContainer();
      expect(JSON.stringify(subSample.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });
  });
});
