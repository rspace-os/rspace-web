import "@/stores/stores/RootStore";
import { HttpResponse, http } from "msw";
import { expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import MoveStore from "@/stores/stores/MoveStore";
import type { RootStore } from "@/stores/stores/RootStore";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import SampleModel from "../../SampleModel";
import { subsampleAttrs } from "../SubSampleModel/mocking";
import { sampleAttrs } from "./mocking";

const rootStore = {
  authStore: { isSynchronizing: false },
  uiStore: {
    addAlert: () => {},
    setPageNavigationConfirmation: () => {},
    setDirty: () => {},
    unsetDirty: () => {},
  },
  unitStore: {
    assertValidUnitId: () => {},
    getUnit: () => ({ label: "ml" }),
  },
  searchStore: { search: { replaceResult: () => {} } },
};

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => rootStore,
}));

const sampleUrl = "/api/inventory/v1/samples/1";

function savedSample() {
  const summary = { ...sampleAttrs({ subSamplesCount: 1 }), globalId: "SA1" };
  Reflect.deleteProperty(summary, "subSamples");
  const sample = new AlwaysNewFactory().newRecord(summary);
  if (!(sample instanceof SampleModel)) throw new Error("Expected a sample");
  return sample;
}

test("a late lean preview keeps children returned by a concurrent full request", async () => {
  const sample = savedSample();
  const leanStarted = Promise.withResolvers<void>();
  const releaseLean = Promise.withResolvers<void>();
  server.use(
    http.get(sampleUrl, async ({ request }) => {
      if (new URL(request.url).searchParams.get("includeSubSamples") === "false") {
        leanStarted.resolve();
        await releaseLean.promise;
        return HttpResponse.json({ ...sampleAttrs({ subSamplesCount: 1 }), subSamples: null });
      }
      return HttpResponse.json(sampleAttrs({ subSamplesCount: 1, subSamples: [subsampleAttrs()] }));
    }),
  );

  const preview = sample.fetchAdditionalInfo();
  await leanStarted.promise;
  try {
    await sample.fetchWithSubSamples();
    expect(sample.subSamples.map((child) => child.globalId)).toEqual(["SS1"]);
  } finally {
    releaseLean.resolve();
    await preview;
  }
  expect(sample.subSamples.map((child) => child.globalId)).toEqual(["SS1"]);
});

test("the default refresh and the next move use the children returned after a change", async () => {
  const sample = savedSample();
  const first = subsampleAttrs();
  const second = subsampleAttrs({ id: 2, globalId: "SS2" });
  let children = [first];
  server.use(
    http.get(sampleUrl, ({ request }) => {
      const lean = new URL(request.url).searchParams.get("includeSubSamples") === "false";
      return HttpResponse.json({
        ...sampleAttrs({ subSamplesCount: children.length }),
        subSamples: lean ? null : children,
      });
    }),
  );

  await sample.fetchAdditionalInfo(false, new URLSearchParams({ includeSubSamples: "true" }));
  expect(sample.subSamples.map((child) => child.globalId)).toEqual(["SS1"]);

  children = [second];
  await sample.fetchAdditionalInfo();
  const moveStore = new MoveStore(rootStore as unknown as RootStore);
  await moveStore.setSelectedResults([sample]);

  expect(sample.subSamples.map((child) => child.globalId)).toEqual(["SS2"]);
  expect(moveStore.selectedResults.map((child) => child.globalId)).toEqual(["SS2"]);
});

test("editing and saving a lean sample leaves subSamples out of the PUT", async () => {
  const sample = savedSample();
  const submitted = vi.fn();
  server.use(
    http.get(sampleUrl, () => HttpResponse.json({ ...sampleAttrs({ subSamplesCount: 1 }), subSamples: null })),
    http.post("/api/inventory/v1/editLocks/SA1", () =>
      HttpResponse.json({ status: "LOCKED_OK", remainingTimeInSeconds: 60, lockOwner: null }),
    ),
    http.put(sampleUrl, async ({ request }) => {
      submitted(await request.json());
      return HttpResponse.json(sampleAttrs({ name: "Renamed sample", subSamples: [subsampleAttrs()] }));
    }),
    http.delete("/api/inventory/v1/editLocks/SA1", () => new HttpResponse(null, { status: 200 })),
  );

  await sample.fetchAdditionalInfo();
  expect(await sample.setEditing(true)).toBe("LOCKED_OK");
  sample.setAttributesDirty({ name: "Renamed sample" });
  await sample.update(false);

  expect(submitted).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ name: "Renamed sample" }));
  expect(submitted.mock.calls[0][0]).not.toHaveProperty("subSamples");
  expect(sample.subSamples.map((child) => child.globalId)).toEqual(["SS1"]);
});
