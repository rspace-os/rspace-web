import type { MockInstance } from "@vitest/spy";
import { describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { GeneratedBarcode } from "../../Barcode";
import { instrumentAttrs, makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(() => ({})),
    get: vi.fn(() => ({})),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    uiStore: {
      addAlert: () => {},
      setPageNavigationConfirmation: () => {},
      setDirty: () => {},
    },
    trackingStore: {
      trackEvent: () => {},
    },
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

describe("InstrumentModel historical version handling", () => {
  test("populateFromJson reads version and historicalVersion", () => {
    const instrument = makeMockInstrument({
      version: 2,
      historicalVersion: true,
    });
    expect(instrument.version).toBe(2);
    expect(instrument.historicalVersion).toBe(true);
  });

  test("records default to non-historical", () => {
    const instrument = makeMockInstrument();
    expect(instrument.historicalVersion).toBe(false);
  });

  test("fetchAdditionalInfo fetches the versioned snapshot for a historical record", async () => {
    const instrument = makeMockInstrument({
      version: 2,
      historicalVersion: true,
      templateId: null,
    });
    const querySpy = (vi.spyOn(InvApiService, "query") as MockInstance).mockImplementation(() =>
      Promise.resolve({
        data: instrumentAttrs({ version: 2, historicalVersion: true, templateId: null }),
      } as never),
    );

    await instrument.fetchAdditionalInfo();

    expect(querySpy).toHaveBeenCalledWith("instruments/1/versions/2", expect.anything());
  });

  test("fetchAdditionalInfo fetches the live record when not historical", async () => {
    const instrument = makeMockInstrument({ templateId: null });
    const querySpy = (vi.spyOn(InvApiService, "query") as MockInstance).mockImplementation(() =>
      Promise.resolve({
        data: instrumentAttrs({ templateId: null }),
      } as never),
    );

    await instrument.fetchAdditionalInfo();

    expect(querySpy).toHaveBeenCalledWith("instruments/1", expect.anything());
  });

  test("setEditing(true) is refused for a historical record", async () => {
    const instrument = makeMockInstrument({
      version: 2,
      historicalVersion: true,
    });
    const checkLockSpy = vi.spyOn(instrument, "checkLock");

    expect(await instrument.setEditing(true)).toBe("CANNOT_LOCK");

    expect(checkLockSpy).not.toHaveBeenCalled();
    expect(instrument.editing).toBe(false);
  });

  test("generated barcode always encodes the live record, never a version", () => {
    const instrument = makeMockInstrument({
      version: 2,
      historicalVersion: true,
    });

    const generated = instrument.barcodes[0];
    if (!(generated instanceof GeneratedBarcode)) throw new Error("expected a generated barcode");

    expect(generated.data).toMatch(/\/inventory\/instrument\/1$/);
    expect(generated.data).not.toContain("version=");
    expect(instrument.permalinkURL).toBe("/inventory/instrument/1?version=2");
  });
});
