import type { MockInstance } from "@vitest/spy";
import { describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { sampleAttrs } from "../SampleModel/mocking";
import { makeMockSubSample, subsampleAttrs } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(() => ({})),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
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
  }),
}));
describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const subsample = makeMockSubSample();
    (vi.spyOn(InvApiService, "query") as MockInstance).mockImplementation(() =>
      Promise.resolve({
        data: {
          sample: sampleAttrs(),
          ...subsampleAttrs(),
        },
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      } as any),
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

  test("A refresh that failed does not poison later refreshes: the next call issues a new request.", async () => {
    // Code review, finding 2: the in-progress promise used to be cleared only on success, so after
    // one rejection every later call awaited the same rejected promise instead of re-fetching.
    const subsample = makeMockSubSample();
    const query = vi.spyOn(InvApiService, "query") as MockInstance;
    query.mockImplementationOnce(() => Promise.reject(new Error("network down")));
    query.mockImplementation(() =>
      Promise.resolve({
        data: {
          sample: sampleAttrs(),
          ...subsampleAttrs(),
        },
        // biome-ignore lint/suspicious/noExplicitAny: matches the existing mock shape
      } as any),
    );
    await expect(subsample.fetchAdditionalInfo(true)).rejects.toThrow();
    const requestsAfterFailure = query.mock.calls.length;
    await subsample.fetchAdditionalInfo(true);
    expect(query.mock.calls.length).toBeGreaterThan(requestsAfterFailure);
  });
});
