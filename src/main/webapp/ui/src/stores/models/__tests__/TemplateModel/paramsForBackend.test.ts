/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import { makeMockTemplate } from "./mocking";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({}));

describe("computed: paramsForBackend", () => {
  /*
   * `paramsForBackend` is used for submitting the TemplateModel to the API and as
   * such must be JSON serialisable. Most significantly, this means not having
   * any cyclical memory references, which is verified by demonstrating that a
   * call to JSON.stringify does not throw any errors and returns a valid
   * string.
   */
  describe("paramsForBackend should be JSON serialisable when", () => {
    test("a Template exists.", () => {
      const Template = makeMockTemplate();
      expect(JSON.stringify(Template.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });
  });
});
