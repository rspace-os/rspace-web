/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import InvApiService from "../../../../common/InvApiService";
import getRootStore from "../../../stores/RootStore";
import ImportModel from "../../ImportModel";
import "@testing-library/jest-dom";
import { templateAttrs } from "../TemplateModel/mocking";
import { runInAction } from "mobx";
import {
  AxiosResponse,
  AxiosRequestHeaders,
  InternalAxiosRequestConfig,
} from "@/common/axios";

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
      jest
        .spyOn(InvApiService, "post")
        .mockImplementation((_resource: string, _params: object | FormData) => {
          const mockResponse: AxiosResponse = {
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
            status: 200,
            statusText: "OK",
            headers: {} as AxiosRequestHeaders,
            config: {
              headers: {} as AxiosRequestHeaders,
            } as InternalAxiosRequestConfig,
          };
          return Promise.resolve(mockResponse);
        });
      jest
        .spyOn(ImportModel.prototype, "transformTemplateInfoForSubmission")
        .mockImplementation(() => ({ fields: [], name: "Template" }));
      jest
        .spyOn(ImportModel.prototype, "makeMappingsObject")
        .mockImplementation(() => ({}));
    });

    test("they should have the correct index.", async () => {
      const uploadModel = new ImportModel("SAMPLES");

      const addAlertSpy = jest.fn();
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
