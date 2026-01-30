/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import {
  addRaidIdentifierAjax,
  removeRaidIdentifierAjax,
} from "../mutations";
import type { AssociateRaidIdentifierRequestBody } from "../schema";
import { AssociateRaidIdentifierRequestBodySchema } from "../schema";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const mockFailureResponse = {
  success: false,
  error: {
    errorMessages: [
      {
        field: "raid",
        errorCode: "VALIDATION_FAILED",
        defaultMessage: "Invalid RaID identifier",
      },
    ],
  },
  errorMsg: "Failed to associate RaID identifier",
};

beforeEach(() => {
  // TODO: RSDEV-996 Replace with msw once we migrate to Vitest
  fetchMock.resetMocks();
  jest.clearAllMocks();
});

describe("addRaidIdentifierAjax", () => {
  const mockParams = {
    groupId: "123",
    raidServerAlias: "test-server",
    raidIdentifier: "raid-456",
  };

  const expectedRequestBody: AssociateRaidIdentifierRequestBody = {
    projectGroupId: 123,
    raid: {
      raidServerAlias: "test-server",
      raidIdentifier: "raid-456",
    },
  };

  it("should add RaID identifier successfully with 201 status", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    const result = await addRaidIdentifierAjax(mockParams);

    expect(result).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      "/apps/raid/associate",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(expectedRequestBody),
      })
    );
  });

  it("should include correct headers", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    await addRaidIdentifierAjax(mockParams);

    expect(fetchMock).toHaveBeenCalledWith(
      "/apps/raid/associate",
      expect.objectContaining({
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          "X-Requested-With": "XMLHttpRequest",
        }) as Record<string, string>,
      })
    );
  });

  it("should convert groupId string to number in request body", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    await addRaidIdentifierAjax({
      groupId: "999",
      raidServerAlias: "test-server",
      raidIdentifier: "raid-123",
    });

    const callArgs = fetchMock.mock.calls[0];
    const requestBody = parseOrThrow(AssociateRaidIdentifierRequestBodySchema, JSON.parse(callArgs[1]?.body as string));
    expect(requestBody.projectGroupId).toBe(999);
    expect(typeof requestBody.projectGroupId).toBe("number");
  });

  it("should handle non-201 success response with failure data", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockFailureResponse), {
      status: 200,
    });

    const result = await addRaidIdentifierAjax(mockParams);

    expect(result).not.toBe(true);
    if (result !== true) {
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errorMessages[0].defaultMessage).toBe(
          "Invalid RaID identifier"
        );
      }
    }
  });

  it("should throw error when response is not ok", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Server error" }), {
      status: 500,
      statusText: "Internal Server Error",
    });

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to add RaID identifier: Internal Server Error"
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("should throw error when response is 404", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Not found" }), {
      status: 404,
      statusText: "Not Found",
    });

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to add RaID identifier: Not Found"
    );
  });

  it("should throw error when response is 401", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      statusText: "Unauthorized",
    });

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to add RaID identifier: Unauthorized"
    );
  });

  it("should throw error when response is 403", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Forbidden" }), {
      status: 403,
      statusText: "Forbidden",
    });

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to add RaID identifier: Forbidden"
    );
  });

  it("should handle network errors", async () => {
    fetchMock.mockRejectOnce(new Error("Network request failed"));

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Network request failed"
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("should handle malformed JSON response for non-201 status", async () => {
    fetchMock.mockResponseOnce("Not valid JSON", {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should throw error when response data does not match schema", async () => {
    // Mock a response with invalid data structure
    fetchMock.mockResponseOnce(
      JSON.stringify({
        success: false,
        // Missing required error fields
      }),
      { status: 200 }
    );

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should handle special characters in parameters", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    const paramsWithSpecialChars = {
      groupId: "123",
      raidServerAlias: "test-server",
      raidIdentifier: "raid-123/456",
    };

    const result = await addRaidIdentifierAjax(paramsWithSpecialChars);

    expect(result).toBe(true);
    const callArgs = fetchMock.mock.calls[0];
    const requestBody = parseOrThrow(
      AssociateRaidIdentifierRequestBodySchema,
      JSON.parse(callArgs[1]?.body as string)
    );
    expect(requestBody.raid.raidIdentifier).toBe("raid-123/456");
  });
});

describe("removeRaidIdentifierAjax", () => {
  const mockParams = {
    groupId: "123",
  };

  it("should remove RaID identifier successfully with 201 status", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    const result = await removeRaidIdentifierAjax(mockParams);

    expect(result).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      "/apps/raid/disassociate/123",
      expect.objectContaining({
        method: "POST",
      })
    );
  });

  it("should include X-Requested-With header", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    await removeRaidIdentifierAjax(mockParams);

    expect(fetchMock).toHaveBeenCalledWith(
      "/apps/raid/disassociate/123",
      expect.objectContaining({
        headers: expect.objectContaining({
          "X-Requested-With": "XMLHttpRequest",
        }) as Record<string, string>,
      })
    );
  });

  it("should construct correct URL with groupId", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    await removeRaidIdentifierAjax({ groupId: "456" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/apps/raid/disassociate/456",
      expect.any(Object)
    );
  });

  it("should handle non-201 success response with failure data", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockFailureResponse), {
      status: 200,
    });

    const result = await removeRaidIdentifierAjax(mockParams);

    expect(result).not.toBe(true);
    if (result !== true) {
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errorMessages[0].defaultMessage).toBe(
          "Invalid RaID identifier"
        );
      }
    }
  });

  it("should throw error when response is not ok", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Server error" }), {
      status: 500,
      statusText: "Internal Server Error",
    });

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to remove RaID identifier: Internal Server Error"
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("should throw error when response is 404", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Not found" }), {
      status: 404,
      statusText: "Not Found",
    });

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to remove RaID identifier: Not Found"
    );
  });

  it("should throw error when response is 401", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      statusText: "Unauthorized",
    });

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to remove RaID identifier: Unauthorized"
    );
  });

  it("should throw error when response is 403", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ error: "Forbidden" }), {
      status: 403,
      statusText: "Forbidden",
    });

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to remove RaID identifier: Forbidden"
    );
  });

  it("should handle network errors", async () => {
    fetchMock.mockRejectOnce(new Error("Network request failed"));

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Network request failed"
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("should handle malformed JSON response for non-201 status", async () => {
    fetchMock.mockResponseOnce("Not valid JSON", {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should throw error when response data does not match schema", async () => {
    // Mock a response with invalid data structure
    fetchMock.mockResponseOnce(
      JSON.stringify({
        success: false,
        // Missing required error fields
      }),
      { status: 200 }
    );

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should handle numeric string groupId", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    await removeRaidIdentifierAjax({ groupId: "999" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/apps/raid/disassociate/999",
      expect.any(Object)
    );
  });

  it("should not include request body", async () => {
    fetchMock.mockResponseOnce("", { status: 201 });

    await removeRaidIdentifierAjax(mockParams);

    const callArgs = fetchMock.mock.calls[0];
    expect(callArgs[1]?.body).toBeUndefined();
  });
});
