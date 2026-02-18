import { beforeEach, describe, expect, test, vi } from "vitest";
import { getShareListing } from "../queries";
import type { ShareSearchResponse } from "../schema";
import type { RestApiError } from "@/modules/common/api/schema";

const API_BASE_URL = "/api/v1";

const mockShareResponse: ShareSearchResponse = {
  totalHits: 1,
  pageNumber: 0,
  shares: [
    {
      id: 1234,
      sharedItemId: 23456,
      shareItemName: "Example Document",
      sharedTargetId: 987,
      sharedTargetType: "GROUP",
      permission: "READ",
    },
  ],
  _links: [
    {
      link: "https://example.com/api/v1/share?pageNumber=0",
      rel: "self",
    },
  ],
};

const mockShareResponseWithFolderShares: ShareSearchResponse = {
  ...mockShareResponse,
  folderShares: [
    {
      id: null,
      sharedItemId: 999,
      shareItemName: "Example Folder",
      sharedTargetId: 987,
      sharedTargetType: "GROUP",
      permission: "READ",
    },
  ],
};

beforeEach(() => {
  // TODO: RSDEV-996 Replace with msw once we migrate to Vitest
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

describe("getShareListing", () => {
  const token = "test-token-123";

  test("should fetch shared items with query parameters", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockShareResponse));

    const params = {
      pageNumber: 1,
      pageSize: 25,
      orderBy: "name asc" as const,
      query: "report",
      sharedItemIds: ["1", "2", "3"] as const,
    };

    const result = await getShareListing(params, { token });

    expect(result).toEqual(mockShareResponse);

    const expectedParams = new URLSearchParams();
    expectedParams.set("pageNumber", "1");
    expectedParams.set("pageSize", "25");
    expectedParams.set("orderBy", "name asc");
    expectedParams.set("query", "report");
    expectedParams.set("sharedItemIds", "1,2,3");

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/share?${expectedParams.toString()}`,
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: `Bearer ${token}`,
          "X-Requested-With": "XMLHttpRequest",
        }) as Record<string, string>,
      }),
    );
  });

  test("should fetch shared items without query parameters", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockShareResponse));

    await getShareListing({}, { token });

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/share`,
      expect.any(Object),
    );
  });

  test("should parse folderShares when provided", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockShareResponseWithFolderShares));

    const result = await getShareListing(
      { sharedItemIds: ["999"] },
      { token },
    );

    expect(result.folderShares).toEqual(
      mockShareResponseWithFolderShares.folderShares,
    );
  });

  test("should throw error when server returns error response", async () => {
    const errorResponse: RestApiError = {
      status: "NOT_FOUND",
      httpCode: 404,
      internalCode: 40401,
      message: "Share listing not found",
      messageCode: "share.listing.not.found",
      errors: ["No shares available"],
      iso8601Timestamp: "2026-02-04T12:00:00.000Z",
      data: null,
    };

    fetchMock.mockResponseOnce(JSON.stringify(errorResponse), {
      status: 404,
      statusText: "Not Found",
    });

    await expect(getShareListing({}, { token })).rejects.toThrow(
      "Share listing not found",
    );
  });
});
