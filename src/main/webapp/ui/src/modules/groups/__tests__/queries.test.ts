import { HttpResponse, http } from "msw";
import { describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { RestApiError } from "@/modules/common/api/schema";
import { getGroupById } from "../queries";
import type { GroupInfo } from "../schema";

const API_BASE_URL = "/api/v1";

// Mock data
const mockGroupInfo: GroupInfo = {
  id: 32768,
  globalId: "GR32768",
  name: "Test Lab Group",
  type: "LAB_GROUP",
  sharedFolderId: 12345,
  sharedSnippetFolderId: 12346,
  members: [
    {
      id: 1,
      username: "testuser",
      role: "PI",
    },
    {
      id: 2,
      username: "labmember",
      role: "USER",
    },
  ],
  raid: {
    raidServerAlias: "test-server",
    raidIdentifier: "raid-123",
    raidTitle: "Test RAID",
  },
};

const mockGroupInfoWithoutRaid: GroupInfo = {
  ...mockGroupInfo,
  raid: null,
};

const mockRestApiError: RestApiError = {
  status: "NOT_FOUND",
  httpCode: 404,
  internalCode: 40401,
  message: "Group not found",
  messageCode: "group.not.found",
  errors: ["The requested group does not exist"],
  iso8601Timestamp: "2026-01-26T12:00:00.000Z",
  data: null,
};

function mockGroupResponse(response: () => Response): Request[] {
  const requests: Request[] = [];
  server.use(
    http.get(`${API_BASE_URL}/groups/:id`, ({ request }) => {
      requests.push(request);
      return response();
    }),
  );
  return requests;
}

describe("getGroupById", () => {
  const token = "test-token-123";

  test("should fetch group info successfully with raid", async () => {
    const requests = mockGroupResponse(() => HttpResponse.json(mockGroupInfo));

    const result = await getGroupById("32768", { token });

    expect(result).not.toBeNull();
    expect(result).toEqual(mockGroupInfo);
    expect(result?.members).toHaveLength(2);
    expect(result?.raid).not.toBeNull();
    expect(result?.raid?.raidIdentifier).toBe("raid-123");

    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).pathname).toBe(`${API_BASE_URL}/groups/32768`);
  });

  test("should fetch group info successfully without raid", async () => {
    mockGroupResponse(() => HttpResponse.json(mockGroupInfoWithoutRaid));

    const result = await getGroupById("32768", { token });

    expect(result).toEqual(mockGroupInfoWithoutRaid);
    expect(result).toHaveProperty("raid");
    expect(result?.raid).toBeNull();
  });

  test("should include authorization header in request", async () => {
    const requests = mockGroupResponse(() => HttpResponse.json(mockGroupInfo));

    await getGroupById("32768", { token });

    expect(requests).toHaveLength(1);
    expect(requests[0].headers.get("Authorization")).toBe(`Bearer ${token}`);
  });

  test("should include X-Requested-With header", async () => {
    const requests = mockGroupResponse(() => HttpResponse.json(mockGroupInfo));

    await getGroupById("32768", { token });

    expect(requests).toHaveLength(1);
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  test("should return null when group not found (404)", async () => {
    const requests = mockGroupResponse(() =>
      HttpResponse.json(mockRestApiError, { status: 404, statusText: "Not Found" }),
    );

    const result = await getGroupById("99999", { token });

    expect(result).toBeNull();
    expect(requests).toHaveLength(1);
  });

  test("should throw error when server returns 400", async () => {
    const badRequestError: RestApiError = {
      status: "BAD_REQUEST",
      httpCode: 400,
      internalCode: 40001,
      message: "Bad request",
      messageCode: "group.bad.request",
      errors: ["The request was invalid"],
      iso8601Timestamp: "2026-01-26T12:00:00.000Z",
      data: null,
    };

    mockGroupResponse(() => HttpResponse.json(badRequestError, { status: 400, statusText: "Bad Request" }));

    await expect(getGroupById("32768", { token })).rejects.toThrow("Bad request");
  });

  test("should throw error when server returns 503", async () => {
    const serviceUnavailableError: RestApiError = {
      status: "SERVICE_UNAVAILABLE",
      httpCode: 503,
      internalCode: 50301,
      message: "Service unavailable",
      messageCode: "server.unavailable",
      errors: ["Upstream service unavailable"],
      iso8601Timestamp: "2026-01-26T12:00:00.000Z",
      data: null,
    };

    mockGroupResponse(() =>
      HttpResponse.json(serviceUnavailableError, { status: 503, statusText: "Service Unavailable" }),
    );

    await expect(getGroupById("32768", { token })).rejects.toThrow("Service unavailable");
  });

  test("should throw error when server returns 500", async () => {
    const serverError: RestApiError = {
      status: "INTERNAL_SERVER_ERROR",
      httpCode: 500,
      internalCode: 50001,
      message: "Internal server error occurred",
      messageCode: "server.error",
      errors: ["Database connection failed"],
      iso8601Timestamp: "2026-01-26T12:00:00.000Z",
      data: null,
    };

    mockGroupResponse(() => HttpResponse.json(serverError, { status: 500, statusText: "Internal Server Error" }));

    await expect(getGroupById("32768", { token })).rejects.toThrow("Internal server error occurred");
  });

  test("should throw generic error when response is not ok and error parsing fails", async () => {
    mockGroupResponse(() =>
      HttpResponse.json({ invalidFormat: "not a valid error" }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(getGroupById("32768", { token })).rejects.toThrow("Failed to fetch group: Internal Server Error");
  });

  test("should throw error when response data does not match schema", async () => {
    // Mock a response with incomplete/invalid data
    mockGroupResponse(() =>
      HttpResponse.json({
        id: 32768,
        name: "Incomplete Group",
        // Missing required fields like globalId, type, etc.
      }),
    );

    await expect(getGroupById("32768", { token })).rejects.toThrow();
  });

  test("should handle network errors", async () => {
    const requests = mockGroupResponse(() => HttpResponse.error());

    await expect(getGroupById("32768", { token })).rejects.toThrow();

    expect(requests).toHaveLength(1);
  });

  test("should handle malformed JSON response", async () => {
    mockGroupResponse(
      () =>
        new HttpResponse("Not valid JSON", {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
    );

    await expect(getGroupById("32768", { token })).rejects.toThrow();
  });

  test("should fetch different group IDs correctly", async () => {
    const groupId = "12345";
    const requests = mockGroupResponse(() => HttpResponse.json({ ...mockGroupInfo, id: 12345, globalId: "GR12345" }));

    await getGroupById(groupId, { token });

    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).pathname).toBe(`${API_BASE_URL}/groups/${groupId}`);
  });

  test("should handle empty string in error message", async () => {
    const errorWithEmptyMessage: RestApiError = {
      status: "BAD_REQUEST",
      httpCode: 400,
      internalCode: 40001,
      message: "",
      messageCode: null,
      errors: [],
      iso8601Timestamp: "2026-01-26T12:00:00.000Z",
      data: null,
    };

    mockGroupResponse(() => HttpResponse.json(errorWithEmptyMessage, { status: 400, statusText: "Bad Request" }));

    await expect(getGroupById("32768", { token })).rejects.toThrow();
  });
});
