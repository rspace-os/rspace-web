import { beforeEach, describe, expect, test, vi } from "vitest";
import type { AxiosResponse, InternalAxiosRequestConfig } from "@/common/axios";
import InvApiService from "../../../../common/InvApiService";
import getRootStore from "../../../stores/getRootStore";
import { sampleAttrs } from "../SampleModel/mocking";
import { makeMockTemplate } from "./mocking";

const mockRootStore = {
  uiStore: {
    confirm: vi.fn(() => Promise.resolve(true)),
    addAlert: vi.fn(() => {}),
  },
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
};

vi.mock("../../../stores/getRootStore", () => ({
  default: () => mockRootStore,
}));
vi.mock("../../../../common/InvApiService", () => ({
  default: {
    post: vi.fn(),
  },
}));
describe("action: updateSampleToLatest", () => {
  describe("When there are two samples, of which only one can be updated, there should", () => {
    beforeEach(() => {
      vi.spyOn(InvApiService, "post").mockImplementation(
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
          }),
      );
    });
    test("Be two toasts, detailing the error and the success.", async () => {
      const addAlertSpy = vi.spyOn(getRootStore().uiStore, "addAlert");
      const template = makeMockTemplate();

      await template.updateSamplesToLatest();
      expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
      expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ variant: "error" }));
    });
  });
});
