import { describe, expect, test, vi } from "vitest";
import { makeMockInstrumentTemplate } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentTemplateModel.permalinkURL", () => {
  test("is null when the template has not yet been saved", () => {
    const template = makeMockInstrumentTemplate({ id: null, globalId: null });
    expect(template.permalinkURL).toBe(null);
  });

  test("is non-null when the template has been saved", () => {
    const template = makeMockInstrumentTemplate({ id: 1, globalId: "NT1" });
    expect(template.permalinkURL).not.toBeNull();
  });

  test("carries the version for a historical record", () => {
    const template = makeMockInstrumentTemplate({
      version: 2,
      historicalVersion: true,
    });
    expect(template.permalinkURL).toBe("/inventory/instrumenttemplate/1?version=2");
  });

  test("is unversioned for a live record", () => {
    const template = makeMockInstrumentTemplate({ version: 3 });
    expect(template.permalinkURL).toBe("/inventory/instrumenttemplate/1");
  });
});
