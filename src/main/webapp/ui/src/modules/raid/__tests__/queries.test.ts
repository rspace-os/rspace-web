import { HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { captureRequests } from "@/__tests__/mswRequestCapture";
import { getAvailableRaidIdentifiersAjax, getRaidIntegrationInfoAjax } from "../queries";
import type { GetAvailableRaidListResponse, IntegrationRaidInfo, RaidReferenceDTO } from "../schema";

const mockRaidReference1: RaidReferenceDTO = {
  raidServerAlias: "test-server-1",
  raidIdentifier: "raid-123",
  raidTitle: "Test RAiD Project 1",
};

const mockRaidReference2: RaidReferenceDTO = {
  raidServerAlias: "test-server-2",
  raidIdentifier: "raid-456",
  raidTitle: "Test RAiD Project 2",
};

const mockAvailableRaidListSuccess: GetAvailableRaidListResponse = {
  success: true,
  data: [mockRaidReference1, mockRaidReference2],
};

const mockIntegrationInfoSuccess: IntegrationRaidInfo = {
  success: true,
  data: {
    name: "RAID",
    displayName: "RAiD",
    available: true,
    enabled: true,
    oauthConnected: true,
    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
    options: {
      RAID_CONFIGURED_SERVERS: [
        {
          url: "https://raid-server-1.example.com",
          alias: "test-server-1",
        },
        {
          url: "https://raid-server-2.example.com",
          alias: "test-server-2",
        },
      ],
      "test-server-1": {
        RAID_OAUTH_CONNECTED: "true",
        RAID_URL: "https://raid-server-1.example.com",
        RAID_ALIAS: "test-server-1",
      },
      "test-server-2": {
        RAID_OAUTH_CONNECTED: "true",
        RAID_URL: "https://raid-server-2.example.com",
        RAID_ALIAS: "test-server-2",
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    } as any,
  },
};

const mockIntegrationInfoSuccessResponse: IntegrationRaidInfo = {
  success: true,
  data: {
    name: "RAID",
    displayName: "RAiD",
    available: true,
    enabled: true,
    oauthConnected: true,
    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
    options: {
      RAID_CONFIGURED_SERVERS: [
        {
          url: "https://raid-server-1.example.com",
          alias: "test-server-1",
        },
        {
          url: "https://raid-server-2.example.com",
          alias: "test-server-2",
        },
      ],
      "test-server-1": {
        RAID_OAUTH_CONNECTED: true,
        RAID_URL: "https://raid-server-1.example.com",
        RAID_ALIAS: "test-server-1",
      },
      "test-server-2": {
        RAID_OAUTH_CONNECTED: true,
        RAID_URL: "https://raid-server-2.example.com",
        RAID_ALIAS: "test-server-2",
      },
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    } as any,
  },
};

const mockIntegrationInfoDisabled: IntegrationRaidInfo = {
  success: true,
  data: {
    name: "RAID",
    displayName: "RAiD",
    available: true,
    enabled: false,
    oauthConnected: false,
    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
    options: {
      RAID_CONFIGURED_SERVERS: [],
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    } as any,
  },
};

const mockIntegrationInfoFailure: IntegrationRaidInfo = {
  success: false,
  error: {
    errorMessages: [
      {
        field: "integration",
        errorCode: "SERVICE_UNAVAILABLE",
        defaultMessage: "Failed to retrieve integration info",
      },
    ],
  },
  errorMsg: "Integration service unavailable",
};

function mockGet(path: string, response: () => Response): Request[] {
  return captureRequests("get", path, response);
}

describe("getAvailableRaidIdentifiersAjax", () => {
  it("should fetch available RAiD identifiers successfully", async () => {
    const requests = mockGet("/apps/raid", () => HttpResponse.json(mockAvailableRaidListSuccess));

    const result = await getAvailableRaidIdentifiersAjax();

    expect(result).toEqual(mockAvailableRaidListSuccess);
    expect(result.success).toBe(true);
    expect(result).toHaveProperty("data");
    if (result.success) {
      expect(result.data).toHaveLength(2);
      expect(result.data[0].raidIdentifier).toBe("raid-123");
      expect(result.data[1].raidIdentifier).toBe("raid-456");
    }

    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).pathname).toBe("/apps/raid");
  });

  it("should include X-Requested-With header", async () => {
    const requests = mockGet("/apps/raid", () => HttpResponse.json(mockAvailableRaidListSuccess));

    await getAvailableRaidIdentifiersAjax();

    expect(requests).toHaveLength(1);
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("should handle empty RAiD list successfully", async () => {
    const mockAvailableRaidListEmpty: GetAvailableRaidListResponse = {
      success: true,
      data: [],
    };

    mockGet("/apps/raid", () => HttpResponse.json(mockAvailableRaidListEmpty));

    const result = await getAvailableRaidIdentifiersAjax();

    expect(result).toEqual(mockAvailableRaidListEmpty);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data).toHaveLength(0);
    }
  });

  it("should handle failure response from server", async () => {
    const mockAvailableRaidListFailure: GetAvailableRaidListResponse = {
      success: false,
      error: {
        errorMessages: [
          {
            field: "raidIdentifiers",
            errorCode: "CONNECTION_FAILED",
            defaultMessage: "Unable to fetch RAiD identifiers",
          },
        ],
      },
      errorMsg: "Connection to RAiD server failed",
    };

    mockGet("/apps/raid", () => HttpResponse.json(mockAvailableRaidListFailure));

    const result = await getAvailableRaidIdentifiersAjax();

    expect(result).toEqual(mockAvailableRaidListFailure);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errorMessages[0].defaultMessage).toBe("Unable to fetch RAiD identifiers");
    }
  });

  it("should throw error when response is not ok", async () => {
    const requests = mockGet("/apps/raid", () =>
      HttpResponse.json({ error: "Server error" }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(getAvailableRaidIdentifiersAjax()).rejects.toThrow("Failed to fetch RAiD apps: Internal Server Error");

    expect(requests).toHaveLength(1);
  });

  it("should throw error when response is 404", async () => {
    mockGet("/apps/raid", () => HttpResponse.json({ error: "Not found" }, { status: 404, statusText: "Not Found" }));

    await expect(getAvailableRaidIdentifiersAjax()).rejects.toThrow("Failed to fetch RAiD apps: Not Found");
  });

  it("should throw error when response is 401", async () => {
    mockGet("/apps/raid", () =>
      HttpResponse.json({ error: "Unauthorized" }, { status: 401, statusText: "Unauthorized" }),
    );

    await expect(getAvailableRaidIdentifiersAjax()).rejects.toThrow("Failed to fetch RAiD apps: Unauthorized");
  });

  it("should handle network errors", async () => {
    const requests = mockGet("/apps/raid", () => HttpResponse.error());

    await expect(getAvailableRaidIdentifiersAjax()).rejects.toThrow();

    expect(requests).toHaveLength(1);
  });

  it("should handle malformed JSON response", async () => {
    mockGet(
      "/apps/raid",
      () => new HttpResponse("Not valid JSON", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await expect(getAvailableRaidIdentifiersAjax()).rejects.toThrow();
  });

  it("should throw error when response data does not match schema", async () => {
    // Mock a response with invalid data structure
    mockGet("/apps/raid", () =>
      HttpResponse.json({
        success: true,
        data: [
          {
            raidServerAlias: "test-server",
            // Missing required fields: raidIdentifier and raidTitle
          },
        ],
      }),
    );

    await expect(getAvailableRaidIdentifiersAjax()).rejects.toThrow();
  });

  it("should validate all required fields in RaidReferenceDTO", async () => {
    // Mock a response with incomplete RaidReferenceDTO
    mockGet("/apps/raid", () =>
      HttpResponse.json({
        success: true,
        data: [
          {
            raidServerAlias: "test-server",
            raidIdentifier: "raid-123",
            // Missing raidTitle
          },
        ],
      }),
    );

    await expect(getAvailableRaidIdentifiersAjax()).rejects.toThrow();
  });

  it("should handle response with extra fields gracefully", async () => {
    const responseWithExtraFields = {
      success: true,
      data: [
        {
          ...mockRaidReference1,
          extraField: "should be ignored",
        },
      ],
    };

    mockGet("/apps/raid", () => HttpResponse.json(responseWithExtraFields));

    const result = await getAvailableRaidIdentifiersAjax();

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data).toHaveLength(1);
      expect(result.data[0].raidIdentifier).toBe("raid-123");
    }
  });
});

describe("getRaidIntegrationInfoAjax", () => {
  it("should fetch RAiD integration info successfully", async () => {
    const requests = mockGet("/integration/integrationInfo", () => HttpResponse.json(mockIntegrationInfoSuccess));

    const result = await getRaidIntegrationInfoAjax();

    // This is slightly different due to the transformation of RAID_OAUTH_CONNECTED to boolean
    expect(result).toEqual(mockIntegrationInfoSuccessResponse);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe("RAID");
      expect(result.data.displayName).toBe("RAiD");
      expect(result.data.available).toBe(true);
      expect(result.data.enabled).toBe(true);
      expect(result.data.oauthConnected).toBe(true);
      expect(result.data.options.RAID_CONFIGURED_SERVERS).toHaveLength(2);
    }

    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).searchParams.get("name")).toBe("RAID");
  });

  it("should include X-Requested-With header", async () => {
    const requests = mockGet("/integration/integrationInfo", () => HttpResponse.json(mockIntegrationInfoSuccess));

    await getRaidIntegrationInfoAjax();

    expect(requests).toHaveLength(1);
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("should handle disabled integration successfully", async () => {
    mockGet("/integration/integrationInfo", () => HttpResponse.json(mockIntegrationInfoDisabled));

    const result = await getRaidIntegrationInfoAjax();

    expect(result).toEqual(mockIntegrationInfoDisabled);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.enabled).toBe(false);
      expect(result.data.oauthConnected).toBe(false);
      expect(result.data.options.RAID_CONFIGURED_SERVERS).toHaveLength(0);
    }
  });

  it("should handle failure response from server", async () => {
    mockGet("/integration/integrationInfo", () => HttpResponse.json(mockIntegrationInfoFailure));

    const result = await getRaidIntegrationInfoAjax();

    expect(result).toEqual(mockIntegrationInfoFailure);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errorMessages[0].defaultMessage).toBe("Failed to retrieve integration info");
    }
  });

  it("should throw error when response is not ok", async () => {
    const requests = mockGet("/integration/integrationInfo", () =>
      HttpResponse.json({ error: "Server error" }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow(
      "Failed to fetch RAiD integration info: Internal Server Error",
    );

    expect(requests).toHaveLength(1);
  });

  it("should throw error when response is 404", async () => {
    mockGet("/integration/integrationInfo", () =>
      HttpResponse.json({ error: "Integration not found" }, { status: 404, statusText: "Not Found" }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow("Failed to fetch RAiD integration info: Not Found");
  });

  it("should throw error when response is 401", async () => {
    mockGet("/integration/integrationInfo", () =>
      HttpResponse.json({ error: "Unauthorized" }, { status: 401, statusText: "Unauthorized" }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow("Failed to fetch RAiD integration info: Unauthorized");
  });

  it("should handle network errors", async () => {
    const requests = mockGet("/integration/integrationInfo", () => HttpResponse.error());

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow();

    expect(requests).toHaveLength(1);
  });

  it("should handle malformed JSON response", async () => {
    mockGet(
      "/integration/integrationInfo",
      () => new HttpResponse("Not valid JSON", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow();
  });

  it("should throw error when response data does not match schema", async () => {
    // Mock a response with invalid data structure
    mockGet("/integration/integrationInfo", () =>
      HttpResponse.json({
        success: true,
        data: {
          name: "RAID",
          displayName: "RAiD",
          // Missing required fields: available, enabled, oauthConnected, options
        },
      }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow();
  });

  it("should validate name field must be 'RAID'", async () => {
    mockGet("/integration/integrationInfo", () =>
      HttpResponse.json({
        success: true,
        data: {
          name: "WRONG_NAME",
          displayName: "RAiD",
          available: true,
          enabled: true,
          oauthConnected: true,
          options: {
            RAID_CONFIGURED_SERVERS: [],
          },
        },
      }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow();
  });

  it("should validate displayName field must be 'RAiD'", async () => {
    mockGet("/integration/integrationInfo", () =>
      HttpResponse.json({
        success: true,
        data: {
          name: "RAID",
          displayName: "WrongDisplayName",
          available: true,
          enabled: true,
          oauthConnected: true,
          options: {
            RAID_CONFIGURED_SERVERS: [],
          },
        },
      }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow();
  });

  it("should validate RAID_URL field is a valid URL", async () => {
    mockGet("/integration/integrationInfo", () =>
      HttpResponse.json({
        success: true,
        data: {
          name: "RAID",
          displayName: "RAiD",
          available: true,
          enabled: true,
          oauthConnected: true,
          options: {
            RAID_CONFIGURED_SERVERS: [
              {
                url: "not-a-valid-url",
                alias: "test-server",
              },
            ],
          },
        },
      }),
    );

    await expect(getRaidIntegrationInfoAjax()).rejects.toThrow();
  });

  it("should validate RAID_OAUTH_CONNECTED transforms string to boolean", async () => {
    const responseWithStringBoolean = {
      success: true,
      data: {
        name: "RAID",
        displayName: "RAiD",
        available: true,
        enabled: true,
        oauthConnected: true,
        options: {
          RAID_CONFIGURED_SERVERS: [],
          "test-server": {
            RAID_OAUTH_CONNECTED: "true",
            RAID_URL: "https://raid-server.example.com",
            RAID_ALIAS: "test-server",
          },
        },
      },
    };

    mockGet("/integration/integrationInfo", () => HttpResponse.json(responseWithStringBoolean));

    const result = await getRaidIntegrationInfoAjax();

    expect(result.success).toBe(true);
    if (result.success) {
      const testServerOptions = result.data.options["test-server"];
      if (testServerOptions && "RAID_OAUTH_CONNECTED" in testServerOptions) {
        expect(testServerOptions.RAID_OAUTH_CONNECTED).toBe(true);
        expect(typeof testServerOptions.RAID_OAUTH_CONNECTED).toBe("boolean");
      }
    }
  });

  it("should handle integration info with multiple configured servers", async () => {
    mockGet("/integration/integrationInfo", () => HttpResponse.json(mockIntegrationInfoSuccess));

    const result = await getRaidIntegrationInfoAjax();

    expect(result.success).toBe(true);
    if (result.success) {
      const configuredServers = result.data.options.RAID_CONFIGURED_SERVERS;
      expect(configuredServers).toHaveLength(2);
      if (configuredServers) {
        expect(configuredServers[0].alias).toBe("test-server-1");
        expect(configuredServers[1].alias).toBe("test-server-2");
      }
      // Verify server-specific options exist
      expect(result.data.options["test-server-1"]).toBeDefined();
      expect(result.data.options["test-server-2"]).toBeDefined();
    }
  });

  it("should handle response with extra fields gracefully", async () => {
    const successData = mockIntegrationInfoSuccess.success ? mockIntegrationInfoSuccess.data : null;
    if (!successData) {
      throw new Error("Test setup error: mockIntegrationInfoSuccess should have success=true");
    }

    const responseWithExtraFields = {
      success: true,
      data: {
        ...successData,
        extraField: "should be ignored",
      },
    };

    mockGet("/integration/integrationInfo", () => HttpResponse.json(responseWithExtraFields));

    const result = await getRaidIntegrationInfoAjax();

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe("RAID");
      expect(result.data.enabled).toBe(true);
    }
  });
});
