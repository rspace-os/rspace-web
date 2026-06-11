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
      mockGet.mockResolvedValue({ data: { permittedActions: ["READ"] } });
      await expect(checkLinkTargetExists(globalId)).resolves.toBe(true);
      expect(mockGet).toHaveBeenCalledWith(endpoint);
    },
  );

  it("accepts an editable inventory target (READ alongside UPDATE)", async () => {
    mockGet.mockResolvedValue({
      data: { permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"] },
    });
    await expect(checkLinkTargetExists("SA12")).resolves.toBe(true);
  });

  it("reports a missing inventory target as not existing", async () => {
    mockGet.mockRejectedValue(new Error("404"));
    await expect(checkLinkTargetExists("SA99999")).resolves.toBe(false);
  });

  it("treats a limited-read inventory target as not visible", async () => {
    // the inventory GET succeeds for items any user may see a redacted view
    // of, but linking requires full READ (the backend rejects the link at
    // save); reporting it as not-visible gives the same generic message as a
    // missing id, so existence is not leaked
    mockGet.mockResolvedValue({
      data: { permittedActions: ["LIMITED_READ"] },
    });
    await expect(checkLinkTargetExists("SA12")).resolves.toBe(false);
  });

  it("treats an inventory response without READ permission as not visible", async () => {
    mockGet.mockResolvedValue({ data: { permittedActions: [] } });
    await expect(checkLinkTargetExists("SA12")).resolves.toBe(false);
    mockGet.mockResolvedValue({ data: {} });
    await expect(checkLinkTargetExists("SA12")).resolves.toBe(false);
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
