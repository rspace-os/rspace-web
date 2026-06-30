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
});
