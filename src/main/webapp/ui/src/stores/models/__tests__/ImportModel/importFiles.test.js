/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import InvApiService from "../../../../common/InvApiService";
import getRootStore from "../../../stores/RootStore";
import ImportModel from "../../ImportModel";
import "@testing-library/jest-dom";
import { templateAttrs } from "../TemplateModel/mocking";
import { type Alert } from "../../../contexts/Alert";
import { runInAction } from "mobx";

jest.mock("../../../../common/InvApiService", () => ({
  post: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

const mockErrorMsg =
  "Unexpected number of values in CSV line, expected: 2, was: 3";

describe("method: importFile", () => {
  describe("When the server responds with some errors,", () => {
    beforeEach(() => {
      jest.spyOn(InvApiService, "post").mockImplementation(() =>
        Promise.resolve({
          data: {
            sampleResults: {
              errorCount: 3,
              results: [
                { error: null, record: {} },
                { error: { errors: [mockErrorMsg] }, record: null },
                { error: { errors: [mockErrorMsg] }, record: null },
              ],
              status: "PREVALIDATION_ERROR",
              successCount: 0,
              successCountBeforeFirstError: 1,
              templateResult: {
                error: null,
                record: templateAttrs(),
              },
              type: "SAMPLE",
            },
            containerResults: null,
          },
        })
      );
      jest
        .spyOn(ImportModel.prototype, "transformTemplateInfoForSubmission")
        .mockImplementation(() => null);
      jest
        .spyOn(ImportModel.prototype, "makeMappingsObject")
        .mockImplementation(() => null);
    });

    test("they should have the correct index.", async () => {
      const uploadModel = new ImportModel("SAMPLES");

      const addAlertSpy = jest.fn<[Alert], void>();
      runInAction(() => {
        getRootStore().uiStore.addAlert = addAlertSpy;
      });

      jest
        .spyOn(uploadModel.state, "transitionTo")
        .mockImplementation(() => {});

      await uploadModel.importFiles();
      expect(addAlertSpy).toBeCalledWith(
        expect.objectContaining({
          variant: "error",
          details: [
            {
              variant: "error",
              title: "Row 2",
              help: mockErrorMsg,
            },
            {
              variant: "error",
              title: "Row 3",
              help: mockErrorMsg,
            },
          ],
        })
      );
    });
  });
});
