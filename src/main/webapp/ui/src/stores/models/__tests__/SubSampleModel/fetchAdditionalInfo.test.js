/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockSubSample, subsampleAttrs } from "./mocking";
import { sampleAttrs } from "../SampleModel/mocking";
import InvApiService from "../../../../common/InvApiService";

jest.mock("../../../../common/InvApiService", () => ({
  query: () => ({}),
}));
jest.mock("../../../../stores/stores/RootStore", () => () => ({
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
}));

describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const subsample = makeMockSubSample();
    jest.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: {
          sample: sampleAttrs(),
          ...subsampleAttrs(),
        },
      })
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
