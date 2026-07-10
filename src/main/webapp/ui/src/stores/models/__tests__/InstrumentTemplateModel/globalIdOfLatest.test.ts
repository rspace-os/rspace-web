import { describe, expect, test } from "vitest";
import { makeMockInstrumentTemplate } from "./mocking";

describe("InstrumentTemplateModel.globalIdOfLatest", () => {
  test("returns the unversioned global id even when globalId carries a version", () => {
    const template = makeMockInstrumentTemplate({ id: 7, globalId: "NT7v2" });
    expect(template.globalIdOfLatest).toBe("NT7");
  });

  test("is null when the template has not yet been saved", () => {
    const template = makeMockInstrumentTemplate({ id: null, globalId: null });
    expect(template.globalIdOfLatest).toBeNull();
  });

  test("the nested instruments search uses the unversioned parentGlobalId on a historical instance", () => {
    const template = makeMockInstrumentTemplate({
      id: 7,
      globalId: "NT7v2",
      version: 2,
      historicalVersion: true,
    });
    expect(template.search.staticFetcher.parentGlobalId).toBe("NT7");
  });
});
