import "@/stores/stores/RootStore";
import { describe, expect, test, vi } from "vitest";
import ApiService from "../../../../common/InvApiService";
import { instrumentTemplateAttrs } from "../../../models/__tests__/InstrumentTemplateModel/mocking";
import AlwaysNewFactory from "../../../models/Factory/AlwaysNewFactory";
import getRootStore from "../../getRootStore";

describe("method: getInstrumentTemplate", () => {
  test("fetches the plain endpoint when no version is given", async () => {
    const { searchStore } = getRootStore();
    const spy = vi.spyOn(ApiService, "get").mockResolvedValue({
      data: instrumentTemplateAttrs({ id: 10 }),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });

    await searchStore.getInstrumentTemplate(10, null, new AlwaysNewFactory());

    expect(spy).toHaveBeenCalledWith("instrumentTemplates", "10");
  });

  test("fetches the versioned endpoint when a version is given", async () => {
    const { searchStore } = getRootStore();
    const spy = vi.spyOn(ApiService, "get").mockResolvedValue({
      data: instrumentTemplateAttrs({ id: 10, version: 3, historicalVersion: true }),
      status: 200,
      statusText: "OK",
      headers: {},
      // biome-ignore lint/suspicious/noExplicitAny: test setup
      config: {} as any,
    });

    await searchStore.getInstrumentTemplate(10, 3, new AlwaysNewFactory());

    expect(spy).toHaveBeenCalledWith("instrumentTemplates", "10/versions/3");
  });
});
