import {
 makeMockSubSample,
 subsampleAttrs } from "./mocking";
import { sampleAttrs } from "../SampleModel/mocking";
import InvApiService from "../../../../common/InvApiService";
import { describe, expect, vi, test } from 'vitest';
import type { MockInstance } from "@vitest/spy";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  query: vi.fn(() => ({})),

  }}));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  uiStore: {
    addAlert: () => {},
    setPageNavigationConfirmation: () => {},
    setDirty: () => {},
  },
  trackingStore: {
    trackEvent: () => {},
  },
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
}));

describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const subsample = makeMockSubSample();
    (vi.spyOn(InvApiService, "query") as MockInstance).mockImplementation(
      () =>
        Promise.resolve({
          data: {
            sample: sampleAttrs(),
            ...subsampleAttrs(),
          },
        } as any)
    );

    let firstCallDone = false;
    await subsample.fetchAdditionalInfo().then(() => {
      firstCallDone = true;
    });

    await subsample.fetchAdditionalInfo();
    /*
     * The second call should not have resolved until the first resolved and
     * set firstCallDone to true
     */
    expect(firstCallDone).toBe(true);
  });
});


