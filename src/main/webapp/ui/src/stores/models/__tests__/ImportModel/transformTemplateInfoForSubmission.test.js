/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import { makeMockImportDataUsingExistingTemplate } from "./mocking";

jest.mock("../../../use-stores", () => () => {});
jest.mock("../../../stores/RootStore", () => () => ({
  authStore: {
    isSynchronizing: true,
  },
}));

describe("method: transformTemplateInfoForSubmission", () => {
  /*
   * When the user is importing with an existing template, the behaviour of
   * transformTemplateInfoForSubmission is substantially different as rather
   * than submit the entire definition of the new template, it is only
   * necessary to provide sufficient data for the server to identify the
   * selected template.
   */
  describe("When the user is importing with an existing template", () => {
    /*
     * `transformTemplateInfoForSubmission ` is used for submitting the
     * ImportModel to the API and as such MUST be JSON serialisable. Most
     * significantly, this means not having any cyclical memory references, which
     * is verified by demonstrating that a call to JSON.stringify does not throw
     * any errors and returns a valid string.
     */
    test("transformTemplateInfoForSubmission should be JSON serialisable", () => {
      const uploadModel = makeMockImportDataUsingExistingTemplate();
      expect(
        JSON.stringify(uploadModel.transformTemplateInfoForSubmission())
      ).toEqual(expect.any(String));
    });

    /*
     * Attaching other data MAY be permitted but the id of the selected tempalte is REQUIRED.
     */
    test("transformTemplateInfoForSubmission should return the id of the selected template.", () => {
      const uploadModel = makeMockImportDataUsingExistingTemplate();
      expect(uploadModel.transformTemplateInfoForSubmission()).toEqual(
        expect.objectContaining({ id: 1 })
      );
    });
  });
});
