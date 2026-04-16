import { beforeEach, describe, expect, it, vi } from "vitest";
import { getWorkspaceRecordInformationAjax } from "@/modules/workspace/queries";
import type { WorkspaceRecordInformation } from "@/modules/workspace/schema";

const WORKSPACE_API_BASE_URL = "/workspace";

const mockRecordInformation: WorkspaceRecordInformation = {
  id: 21,
  oid: {
    idString: "GL21",
  },
  name: "Gallery image",
  type: "Image",
  ownerFullName: "Alice Example",
  ownerUsername: "alice",
  description: "Microscope image",
  extension: "png",
  size: 1024,
  version: 2,
  thumbnailId: 301,
  widthResized: 640,
  heightResized: 480,
  modificationDate: 1712755200000,
  creationDateWithClientTimezoneOffset: "2026-04-14 10:00 +0000",
  modificationDateWithClientTimezoneOffset: "2026-04-14 10:05 +0000",
};

beforeEach(() => {
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

describe("getWorkspaceRecordInformationAjax", () => {
  it("fetches and unwraps detailed record information", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: mockRecordInformation,
        error: null,
        errorMsg: null,
        success: true,
      }),
    );

    const result = await getWorkspaceRecordInformationAjax({
      recordId: 21,
      revision: 3,
      version: 2,
    });

    expect(result).toEqual(mockRecordInformation);
    expect(fetchMock).toHaveBeenCalledWith(
      `${WORKSPACE_API_BASE_URL}/getRecordInformation?recordId=21&revision=3&version=2`,
      expect.objectContaining({
        method: "GET",
        headers: {
          "X-Requested-With": "XMLHttpRequest",
        },
      }),
    );
  });

  it("throws the endpoint error message when a successful response has no data", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: null,
        error: {
          errorMessages: [
            {
              defaultMessage: "Could not load record information",
              errorCode: "workspace.getRecordInformation.failed",
              field: "recordId",
            },
          ],
        },
        success: false,
      }),
    );

    await expect(
      getWorkspaceRecordInformationAjax({ recordId: 21 }),
    ).rejects.toThrow("Could not load record information");
  });

  it("throws endpoint error details for non-OK responses", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: null,
        error: {
          errorMessages: ["Record not found"],
        },
        success: false,
      }),
      {
        status: 404,
        statusText: "Not Found",
      },
    );

    await expect(
      getWorkspaceRecordInformationAjax({ recordId: 99 }),
    ).rejects.toThrow("Record not found");
  });
});

