import { describe, expect, test, vi } from 'vitest';
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
    vi.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: {
          ...sampleAttrs(),
          templateId: 1,
        },
        status: 200,
        statusText: "OK",
        headers: {},
        config: {} as any,
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
    vi.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: sampleAttrs(),
        status: 200,
        statusText: "OK",
        headers: {},
        config: {} as any,
      })
    );
    await expect(sample.fetchAdditionalInfo()).resolves.toBeUndefined();
  });
});

