/*
 * @jest-environment jsdom
 */

/* eslint-env jest */
import { makeMockTemplate } from "./mocking";
import { sampleAttrs } from "../SampleModel/mocking";

import getRootStore from "../../../stores/RootStore";
import InvApiService from "../../../../common/InvApiService";
import {
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "@/common/axios";

/*
 * We define the root store here so that every call to getRootStore gets a
 * reference to the same object. It is stored using var to make sure that the
 * mocks below aren't hoiseted above.
 */
const mockRootStore = {
  uiStore: {
    confirm: jest.fn(() => Promise.resolve(true)),
    addAlert: jest.fn(() => {}),
  },
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
};

jest.mock("../../../stores/RootStore", () => () => mockRootStore);
jest.mock("../../../../common/InvApiService", () => ({
  post: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

describe("action: updateSampleToLatest", () => {
  describe("When there are two samples, of which only one can be updated, there should", () => {
    beforeEach(() => {
      jest.spyOn(InvApiService, "post").mockImplementation(
        (): Promise<AxiosResponse<unknown>> =>
          Promise.resolve({
            data: {
              errorCount: 1,
              status: "COMPLETED",
              successCount: 1,
              successCountBeforeFirstError: 1,
              results: [
                {
                  error: null,
                  record: sampleAttrs(),
                },
                {
                  error: {
                    errors: ["There was an error."],
                  },
                  record: null,
                },
              ],
            },
            status: 200,
            statusText: "OK",
            headers: {},
            config: {} as InternalAxiosRequestConfig,
          })
      );
    });
    test("Be two toasts, detailing the error and the success.", async () => {
      const addAlertSpy = jest.spyOn(getRootStore().uiStore, "addAlert");
      const template = makeMockTemplate();
      await template.updateSamplesToLatest();

      expect(addAlertSpy).toHaveBeenCalledWith(
        expect.objectContaining({ variant: "success" })
      );
      expect(addAlertSpy).toHaveBeenCalledWith(
        expect.objectContaining({ variant: "error" })
      );
    });
  });
});
