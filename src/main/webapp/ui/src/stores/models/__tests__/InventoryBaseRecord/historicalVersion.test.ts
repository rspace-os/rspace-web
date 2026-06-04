import { describe, expect, test, vi } from "vitest";
import type { MockInstance } from "@vitest/spy";
import InvApiService from "../../../../common/InvApiService";
import { makeMockSubSample, subsampleAttrs } from "../SubSampleModel/mocking";
import { sampleAttrs } from "../SampleModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(() => ({})),
  },
}));
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
  }),
}));

describe("historical version handling", () => {
  test("populateFromJson reads version and historicalVersion", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
    });
    expect(subsample.version).toBe(2);
    expect(subsample.historicalVersion).toBe(true);
  });

  test("records default to non-historical", () => {
    const subsample = makeMockSubSample();
    expect(subsample.historicalVersion).toBe(false);
  });

  test("context menu is disabled with a reason for a historical record", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
    });
    expect(subsample.contextMenuDisabled()).toEqual(expect.any(String));
  });

  test("context menu is not disabled for a live record", () => {
    const subsample = makeMockSubSample();
    expect(subsample.contextMenuDisabled()).toBeNull();
  });

  test("permalinkURL carries the version for a historical record", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
    });
    expect(subsample.permalinkURL).toBe("/inventory/subsample/1?version=2");
  });

  test("permalinkURL is unversioned for a live record", () => {
    const subsample = makeMockSubSample({ version: 3 });
    expect(subsample.permalinkURL).toBe("/inventory/subsample/1");
  });

  test("fetchAdditionalInfo fetches the versioned snapshot for a historical record", async () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
    });
    const querySpy = (
      vi.spyOn(InvApiService, "query") as MockInstance
    ).mockImplementation(() =>
      Promise.resolve({
        data: {
          sample: sampleAttrs(),
          ...subsampleAttrs({ version: 2, historicalVersion: true }),
        },
      } as never),
    );

    await subsample.fetchAdditionalInfo();

    expect(querySpy).toHaveBeenCalledWith(
      "subSamples/1/versions/2",
      expect.anything(),
    );
  });

  test("notes cannot be added to a historical subsample", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
    });
    subsample.updateFieldsState();
    expect(subsample.isFieldEditable("notes")).toBe(false);
  });

  test("notes can be added to a live subsample in preview", () => {
    const subsample = makeMockSubSample();
    subsample.updateFieldsState();
    expect(subsample.isFieldEditable("notes")).toBe(true);
  });

  test("fetchAdditionalInfo fetches the live record when not historical", async () => {
    const subsample = makeMockSubSample();
    const querySpy = (
      vi.spyOn(InvApiService, "query") as MockInstance
    ).mockImplementation(() =>
      Promise.resolve({
        data: {
          sample: sampleAttrs(),
          ...subsampleAttrs(),
        },
      } as never),
    );

    await subsample.fetchAdditionalInfo();

    expect(querySpy).toHaveBeenCalledWith("subSamples/1", expect.anything());
  });
});
