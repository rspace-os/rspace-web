import type { MockInstance } from "@vitest/spy";
import { describe, expect, test, vi } from "vitest";
import InvApiService from "../../../../common/InvApiService";
import { GeneratedBarcode } from "../../Barcode";
import { instrumentTemplateAttrs, makeMockInstrumentTemplate } from "./mocking";

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

describe("InstrumentTemplateModel historical version handling", () => {
  test("populateFromJson reads version and historicalVersion", () => {
    const template = makeMockInstrumentTemplate({
      version: 2,
      historicalVersion: true,
    });
    expect(template.version).toBe(2);
    expect(template.historicalVersion).toBe(true);
  });

  test("records default to non-historical", () => {
    const template = makeMockInstrumentTemplate();
    expect(template.historicalVersion).toBe(false);
  });

  test("fetchAdditionalInfo fetches the versioned snapshot for a historical record", async () => {
    const template = makeMockInstrumentTemplate({
      version: 2,
      historicalVersion: true,
    });
    const querySpy = (vi.spyOn(InvApiService, "query") as MockInstance).mockImplementation(() =>
      Promise.resolve({
        data: instrumentTemplateAttrs({ version: 2, historicalVersion: true }),
      } as never),
    );

    await template.fetchAdditionalInfo();

    expect(querySpy).toHaveBeenCalledWith("instrumentTemplates/1/versions/2", expect.anything());
  });

  test("fetchAdditionalInfo fetches the live record when not historical", async () => {
    const template = makeMockInstrumentTemplate();
    const querySpy = (vi.spyOn(InvApiService, "query") as MockInstance).mockImplementation(() =>
      Promise.resolve({
        data: instrumentTemplateAttrs(),
      } as never),
    );

    await template.fetchAdditionalInfo();

    expect(querySpy).toHaveBeenCalledWith("instrumentTemplates/1", expect.anything());
  });

  test("setEditing(true) is refused for a historical record", async () => {
    const template = makeMockInstrumentTemplate({
      version: 2,
      historicalVersion: true,
    });
    const checkLockSpy = vi.spyOn(template, "checkLock");

    expect(await template.setEditing(true)).toBe("CANNOT_LOCK");

    expect(checkLockSpy).not.toHaveBeenCalled();
    expect(template.editing).toBe(false);
  });

  test("generated barcode always encodes the live record, never a version", () => {
    const template = makeMockInstrumentTemplate({
      version: 2,
      historicalVersion: true,
    });

    const generated = template.barcodes[0];
    if (!(generated instanceof GeneratedBarcode)) throw new Error("expected a generated barcode");

    expect(generated.data).toMatch(/\/inventory\/instrumenttemplate\/1$/);
    expect(generated.data).not.toContain("version=");
    expect(template.permalinkURL).toBe("/inventory/instrumenttemplate/1?version=2");
  });
});
