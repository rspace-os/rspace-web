/*
 */
import { describe, test, expect, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockSample, sampleAttrs } from "./mocking";
import { makeMockTemplate } from "../TemplateModel/mocking";
import InvApiService from "../../../../common/InvApiService";

const mockRootStore = {
  unitStore: {
    assertValidUnitId: () => {},
    getUnit: () => ({ label: "ml" }),
  },
  uiStore: {
    addAlert: () => {},
    setPageNavigationConfirmation: () => {},
    setDirty: () => {},
  },
  searchStore: {
    getTemplate: vi.fn().mockRejectedValue(new Error("Test error")),
  },
};

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: () => ({}),
  },
}));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => mockRootStore,
}));

describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const template = makeMockTemplate();
    mockRootStore.searchStore.getTemplate.mockImplementation(() =>
      Promise.resolve(template)
    );

    const sample = makeMockSample({
      templateId: 1,
    });
    // @ts-expect-error Mock implementation return type incompatibility
    vi.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: {
          ...sampleAttrs(),
          templateId: 1,
        },
      })
    );

    let firstCallDone = false;
    void sample.fetchAdditionalInfo().then(() => {
      firstCallDone = true;
    });

    await sample.fetchAdditionalInfo();
    /*
     * The second call should not have resolved until the first resolved and
     * set firstCallDone to true
     */
    expect(firstCallDone).toBe(true);
    /*
     * The fetching of the sample's template should also complete before
     * fetchAdditionalInfo resolves
     */
    expect(sample.template).toEqual(template);
  });
  test("Calls made on a sample without a template should resolve.", async () => {
    const sample = makeMockSample();
    // @ts-expect-error Mock implementation return type incompatibility
    vi.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: sampleAttrs(),
      })
    );
    await expect(sample.fetchAdditionalInfo()).resolves.toBeUndefined();
  });
});

