import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockGet, mockGetWorkspaceRecordInformationAjax } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockGetWorkspaceRecordInformationAjax: vi.fn(),
}));

vi.mock("@/common/InvApiService", () => ({
  default: { get: mockGet },
}));
vi.mock("@/modules/workspace/queries", () => ({
  getWorkspaceRecordInformationAjax: mockGetWorkspaceRecordInformationAjax,
}));

import { checkLinkTargetExists } from "../linkTargetExists";

describe("checkLinkTargetExists", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockGetWorkspaceRecordInformationAjax.mockReset();
  });

  it.each([
    ["SA12", "samples/12"],
    ["SS34", "subSamples/34"],
    ["IC56", "containers/56"],
    ["IN78", "instruments/78"],
    ["IT90", "sampleTemplates/90"],
  ])(
    "resolves an inventory target %s through the inventory API",
    async (globalId, endpoint) => {
      mockGet.mockResolvedValue({ data: {} });
      await expect(checkLinkTargetExists(globalId)).resolves.toBe(true);
      expect(mockGet).toHaveBeenCalledWith(endpoint);
    },
  );

  it("reports a missing inventory target as not existing", async () => {
    mockGet.mockRejectedValue(new Error("404"));
    await expect(checkLinkTargetExists("SA99999")).resolves.toBe(false);
  });

  it("resolves an ELN target through the workspace record-information endpoint", async () => {
    mockGetWorkspaceRecordInformationAjax.mockResolvedValue({ id: 42 });
    await expect(checkLinkTargetExists("SD42")).resolves.toBe(true);
    expect(mockGetWorkspaceRecordInformationAjax).toHaveBeenCalledWith({
      recordId: 42,
    });
  });

  it("reports a missing ELN target as not existing", async () => {
    mockGetWorkspaceRecordInformationAjax.mockRejectedValue(
      new Error("not found"),
    );
    await expect(checkLinkTargetExists("NB123")).resolves.toBe(false);
  });

  it("checks the base record for a version-pinned target", async () => {
    mockGetWorkspaceRecordInformationAjax.mockResolvedValue({ id: 7 });
    await expect(checkLinkTargetExists("SD7v3")).resolves.toBe(true);
    expect(mockGetWorkspaceRecordInformationAjax).toHaveBeenCalledWith({
      recordId: 7,
    });
  });

  it("reports unknown prefixes and malformed ids as not existing", async () => {
    await expect(checkLinkTargetExists("XX5")).resolves.toBe(false);
    await expect(checkLinkTargetExists("not-an-id")).resolves.toBe(false);
    expect(mockGet).not.toHaveBeenCalled();
    expect(mockGetWorkspaceRecordInformationAjax).not.toHaveBeenCalled();
  });
});
