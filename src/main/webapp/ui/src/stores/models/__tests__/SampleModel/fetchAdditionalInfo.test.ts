import { describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { makeMockTemplate } from "../TemplateModel/mocking";
import { makeMockSample, sampleAttrs } from "./mocking";

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
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => mockRootStore,
}));
describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const template = makeMockTemplate();
    mockRootStore.searchStore.getTemplate.mockImplementation(() => Promise.resolve(template));
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
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        config: {} as any,
      }),
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
  test("A solitary call fully awaits the template fetch before resolving (RSDEV-1309 regression).", async () => {
    const template = makeMockTemplate();
    mockRootStore.searchStore.getTemplate.mockImplementation(() => Promise.resolve(template));
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
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        config: {} as any,
      }),
    );

    await sample.fetchAdditionalInfo();

    // The bug this guards against (see fetchAdditionalInfo's own doc comment): awaiting a
    // *solitary* call used to resolve before the template fetch - and even the base class's own
    // fetchAdditionalInfo - had actually finished, so `sample.template` would still be null here.
    // The "Subsequent invocations" test above only exercises the concurrent-second-call path,
    // which was already correct before the fix; this is the path that was actually broken.
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
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        config: {} as any,
      }),
    );
    await expect(sample.fetchAdditionalInfo()).resolves.toBeUndefined();
  });
});
