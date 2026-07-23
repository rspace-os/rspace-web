import { HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { captureRequests } from "@/__tests__/mswRequestCapture";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { addRaidIdentifierAjax, removeRaidIdentifierAjax } from "../mutations";
import type { AssociateRaidIdentifierRequestBody } from "../schema";
import { AssociateRaidIdentifierRequestBodySchema } from "../schema";

const mockFailureResponse = {
  success: false,
  error: {
    errorMessages: [
      {
        field: "raid",
        errorCode: "VALIDATION_FAILED",
        defaultMessage: "Invalid RAiD identifier",
      },
    ],
  },
  errorMsg: "Failed to associate RAiD identifier",
};

function mockRaidPost(path: string, response: () => Response): Request[] {
  return captureRequests("post", path, response);
}

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

  it("should add RAiD identifier successfully with 201 status", async () => {
    const requests = mockRaidPost("/apps/raid/associate", () => new HttpResponse(null, { status: 201 }));

    const result = await addRaidIdentifierAjax(mockParams);

    expect(result).toBe(true);
    expect(requests).toHaveLength(1);
    await expect(requests[0].json()).resolves.toEqual(expectedRequestBody);
  });

  it("should include correct headers", async () => {
    const requests = mockRaidPost("/apps/raid/associate", () => new HttpResponse(null, { status: 201 }));

    await addRaidIdentifierAjax(mockParams);

    expect(requests).toHaveLength(1);
    expect(requests[0].headers.get("Content-Type")).toBe("application/json");
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("should convert groupId string to number in request body", async () => {
    const requests = mockRaidPost("/apps/raid/associate", () => new HttpResponse(null, { status: 201 }));

    await addRaidIdentifierAjax({
      groupId: "999",
      raidServerAlias: "test-server",
      raidIdentifier: "raid-123",
    });

    expect(requests).toHaveLength(1);
    const requestBody = parseOrThrow(AssociateRaidIdentifierRequestBodySchema, await requests[0].json());
    expect(requestBody.projectGroupId).toBe(999);
    expect(typeof requestBody.projectGroupId).toBe("number");
  });

  it("should handle non-201 success response with failure data", async () => {
    mockRaidPost("/apps/raid/associate", () => HttpResponse.json(mockFailureResponse, { status: 200 }));

    const result = await addRaidIdentifierAjax(mockParams);

    expect(result).not.toBe(true);
    if (result !== true) {
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errorMessages[0].defaultMessage).toBe("Invalid RAiD identifier");
      }
    }
  });

  it("should throw error when response is not ok", async () => {
    const requests = mockRaidPost("/apps/raid/associate", () =>
      HttpResponse.json({ error: "Server error" }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to add RAiD identifier: Internal Server Error",
    );

    expect(requests).toHaveLength(1);
  });

  it("should throw error when response is 404", async () => {
    mockRaidPost("/apps/raid/associate", () =>
      HttpResponse.json({ error: "Not found" }, { status: 404, statusText: "Not Found" }),
    );

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow("Failed to add RAiD identifier: Not Found");
  });

  it("should throw error when response is 401", async () => {
    mockRaidPost("/apps/raid/associate", () =>
      HttpResponse.json({ error: "Unauthorized" }, { status: 401, statusText: "Unauthorized" }),
    );

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow("Failed to add RAiD identifier: Unauthorized");
  });

  it("should throw error when response is 403", async () => {
    mockRaidPost("/apps/raid/associate", () =>
      HttpResponse.json({ error: "Forbidden" }, { status: 403, statusText: "Forbidden" }),
    );

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow("Failed to add RAiD identifier: Forbidden");
  });

  it("should handle network errors", async () => {
    const requests = mockRaidPost("/apps/raid/associate", () => HttpResponse.error());

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow();

    expect(requests).toHaveLength(1);
  });

  it("should handle malformed JSON response for non-201 status", async () => {
    mockRaidPost(
      "/apps/raid/associate",
      () => new HttpResponse("Not valid JSON", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should throw error when response data does not match schema", async () => {
    // Mock a response with invalid data structure
    mockRaidPost("/apps/raid/associate", () =>
      HttpResponse.json({
        success: false,
        // Missing required error fields
      }),
    );

    await expect(addRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should handle special characters in parameters", async () => {
    const requests = mockRaidPost("/apps/raid/associate", () => new HttpResponse(null, { status: 201 }));

    const paramsWithSpecialChars = {
      groupId: "123",
      raidServerAlias: "test-server",
      raidIdentifier: "raid-123/456",
    };

    const result = await addRaidIdentifierAjax(paramsWithSpecialChars);

    expect(result).toBe(true);
    expect(requests).toHaveLength(1);
    const requestBody = parseOrThrow(AssociateRaidIdentifierRequestBodySchema, await requests[0].json());
    expect(requestBody.raid.raidIdentifier).toBe("raid-123/456");
  });
});

describe("removeRaidIdentifierAjax", () => {
  const mockParams = {
    groupId: "123",
  };

  it("should remove RAiD identifier successfully with 201 status", async () => {
    const requests = mockRaidPost("/apps/raid/disassociate/:groupId", () => new HttpResponse(null, { status: 201 }));

    const result = await removeRaidIdentifierAjax(mockParams);

    expect(result).toBe(true);
    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).pathname).toBe("/apps/raid/disassociate/123");
  });

  it("should include X-Requested-With header", async () => {
    const requests = mockRaidPost("/apps/raid/disassociate/:groupId", () => new HttpResponse(null, { status: 201 }));

    await removeRaidIdentifierAjax(mockParams);

    expect(requests).toHaveLength(1);
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("should construct correct URL with groupId", async () => {
    const requests = mockRaidPost("/apps/raid/disassociate/:groupId", () => new HttpResponse(null, { status: 201 }));

    await removeRaidIdentifierAjax({ groupId: "456" });

    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).pathname).toBe("/apps/raid/disassociate/456");
  });

  it("should handle non-201 success response with failure data", async () => {
    mockRaidPost("/apps/raid/disassociate/:groupId", () => HttpResponse.json(mockFailureResponse, { status: 200 }));

    const result = await removeRaidIdentifierAjax(mockParams);

    expect(result).not.toBe(true);
    if (result !== true) {
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.errorMessages[0].defaultMessage).toBe("Invalid RAiD identifier");
      }
    }
  });

  it("should throw error when response is not ok", async () => {
    const requests = mockRaidPost("/apps/raid/disassociate/:groupId", () =>
      HttpResponse.json({ error: "Server error" }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to remove RAiD identifier: Internal Server Error",
    );

    expect(requests).toHaveLength(1);
  });

  it("should throw error when response is 404", async () => {
    mockRaidPost("/apps/raid/disassociate/:groupId", () =>
      HttpResponse.json({ error: "Not found" }, { status: 404, statusText: "Not Found" }),
    );

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow("Failed to remove RAiD identifier: Not Found");
  });

  it("should throw error when response is 401", async () => {
    mockRaidPost("/apps/raid/disassociate/:groupId", () =>
      HttpResponse.json({ error: "Unauthorized" }, { status: 401, statusText: "Unauthorized" }),
    );

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow(
      "Failed to remove RAiD identifier: Unauthorized",
    );
  });

  it("should throw error when response is 403", async () => {
    mockRaidPost("/apps/raid/disassociate/:groupId", () =>
      HttpResponse.json({ error: "Forbidden" }, { status: 403, statusText: "Forbidden" }),
    );

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow("Failed to remove RAiD identifier: Forbidden");
  });

  it("should handle network errors", async () => {
    const requests = mockRaidPost("/apps/raid/disassociate/:groupId", () => HttpResponse.error());

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow();

    expect(requests).toHaveLength(1);
  });

  it("should handle malformed JSON response for non-201 status", async () => {
    mockRaidPost(
      "/apps/raid/disassociate/:groupId",
      () => new HttpResponse("Not valid JSON", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should throw error when response data does not match schema", async () => {
    // Mock a response with invalid data structure
    mockRaidPost("/apps/raid/disassociate/:groupId", () =>
      HttpResponse.json({
        success: false,
        // Missing required error fields
      }),
    );

    await expect(removeRaidIdentifierAjax(mockParams)).rejects.toThrow();
  });

  it("should handle numeric string groupId", async () => {
    const requests = mockRaidPost("/apps/raid/disassociate/:groupId", () => new HttpResponse(null, { status: 201 }));

    await removeRaidIdentifierAjax({ groupId: "999" });

    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).pathname).toBe("/apps/raid/disassociate/999");
  });

  it("should not include request body", async () => {
    const requests = mockRaidPost("/apps/raid/disassociate/:groupId", () => new HttpResponse(null, { status: 201 }));

    await removeRaidIdentifierAjax(mockParams);

    expect(requests).toHaveLength(1);
    expect(requests[0].body).toBeNull();
  });
});
