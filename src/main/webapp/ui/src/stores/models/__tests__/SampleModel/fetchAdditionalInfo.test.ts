import { describe, expect, test, vi } from "vitest";
import type { AxiosResponse } from "@/common/axios";
import InvApiService from "../../../../common/InvApiService";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import SampleModel from "../../SampleModel";
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
  test("Discards a stale refresh, with a warning, if editing starts while the fetch is in flight (RSDEV-1309).", async () => {
    const consoleWarnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const sample = makeMockSample({ name: "My in-progress edit" });
    let resolveQuery:
      | ((value: {
          data: unknown;
          status: number;
          statusText: string;
          headers: object;
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          config: any;
        }) => void)
      | undefined;
    vi.spyOn(InvApiService, "query").mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveQuery = resolve;
        }),
    );

    const fetchPromise = sample.fetchAdditionalInfo();
    // The record starts being edited while the fetch is still in flight - the exact race the
    // guard at InventoryBaseRecord.tsx's fetchAdditionalInfo (the "if (this.editing)" check) exists
    // for.
    sample.editing = true;
    resolveQuery?.({
      data: { ...sampleAttrs(), name: "Stale server name" },
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      config: {} as any,
    });
    await fetchPromise;

    // The in-flight edit must not be silently discarded by a snapshot fetched before editing
    // began.
    expect(sample.name).toBe("My in-progress edit");
    expect(consoleWarnSpy).toHaveBeenCalledWith(expect.stringContaining("Discarding a stale additional-info refresh"));
    consoleWarnSpy.mockRestore();
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

test("uses the child count for split availability and for an unloaded sample after deletion", async () => {
  const summary = { ...sampleAttrs({ subSamplesCount: 2 }), globalId: "SA1" };
  Reflect.deleteProperty(summary, "subSamples");
  const sample = new AlwaysNewFactory().newRecord(summary);
  if (!(sample instanceof SampleModel)) throw new Error("Expected a sample");
  const split = () =>
    sample.createOptions.find((option) => option.parameters?.some((parameter) => parameter.state.key === "split"));
  expect(split()?.disabled).toBe(true);
  const query = vi
    .spyOn(InvApiService, "query")
    .mockClear()
    .mockResolvedValue({
      data: { ...summary, subSamplesCount: 1, subSamples: null },
    } as AxiosResponse);
  sample.updateBecauseRecordsChanged(new Set(["SS1"]), new Set(["SA1"]));
  await vi.waitFor(() => expect(sample.subSamplesCount).toBe(1));
  expect(query).toHaveBeenCalledTimes(1);
  expect(split()?.disabled).toBe(false);
});
