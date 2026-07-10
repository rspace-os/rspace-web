import { describe, expect, test, vi } from "vitest";
import { makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentModel.permalinkURL", () => {
  test("is null when the instrument has not yet been saved", () => {
    const instrument = makeMockInstrument({ id: null, globalId: null });
    expect(instrument.permalinkURL).toBe(null);
  });

  test("is non-null when the instrument has been saved", () => {
    const instrument = makeMockInstrument({ id: 1, globalId: "IN1" });
    expect(instrument.permalinkURL).not.toBeNull();
  });

  test("carries the version for a historical record", () => {
    const instrument = makeMockInstrument({
      version: 2,
      historicalVersion: true,
    });
    expect(instrument.permalinkURL).toBe("/inventory/instrument/1?version=2");
  });

  test("is unversioned for a live record", () => {
    const instrument = makeMockInstrument({ version: 3 });
    expect(instrument.permalinkURL).toBe("/inventory/instrument/1");
  });
});
