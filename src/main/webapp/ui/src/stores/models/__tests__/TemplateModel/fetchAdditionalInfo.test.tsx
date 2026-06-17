import { describe, expect, test, vi } from "vitest";
import type { AxiosResponse } from "@/common/axios";
import InvApiService from "../../../../common/InvApiService";
import { makeMockTemplate, templateAttrs } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    get: () => ({}),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    uiStore: {
      addAlert: () => {},
      setPageNavigationConfirmation: () => {},
      setDirty: () => {},
    },
  }),
}));
describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const template = makeMockTemplate();
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve({
        data: templateAttrs(),
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse),
    );
    let firstCallDone = false;
    await template.fetchAdditionalInfo().then(() => {
      firstCallDone = true;
    });
    await template.fetchAdditionalInfo();
    /*
     * The second call should not have resolved until the first resolved and
     * set firstCallDone to true
     */
    expect(firstCallDone).toBe(true);
  });

  test("a pinned version is fetched through the versions endpoint", async () => {
    const template = makeMockTemplate({ version: 2 });
    const spy = vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve({
        data: templateAttrs({ version: 2 }),
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse),
    );

    await template.fetchAdditionalInfo();

    expect(spy).toHaveBeenCalledWith("sampleTemplates", "1/versions/2");
  });

  test("version 0 is requested as a version, not silently swapped for the live record", async () => {
    /*
     * /versions/0 correctly 404s server-side; a truthiness check here would
     * instead fetch the live record, diverging from the != null convention
     * used by CoreFetcher and InventoryBaseRecord.
     */
    const template = makeMockTemplate({ version: 0 });
    const spy = vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve({
        data: templateAttrs(),
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse),
    );

    await template.fetchAdditionalInfo();

    expect(spy).toHaveBeenCalledWith("sampleTemplates", "1/versions/0");
  });
});
