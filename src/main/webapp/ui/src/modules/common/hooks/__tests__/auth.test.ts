import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { fetchToken } from "@/modules/common/hooks/auth";
import { getStoredToken } from "@/modules/common/utils/auth";

describe("fetchToken", () => {
  it("issues and stores an OAuth token through the legacy endpoint by default", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.get("/userform/ajax/inventoryOauthToken", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json({ data: "legacy-token" });
      }),
    );

    await expect(fetchToken()).resolves.toBe("legacy-token");
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(getStoredToken()).toBe("legacy-token");
  });

  it("uses REST API v2 when requested by newApp", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.post("/api/v2/oauth/tokens", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json({ accessToken: "new-token" });
      }),
    );

    await expect(fetchToken(true)).resolves.toBe("new-token");
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(getStoredToken()).toBe("new-token");
  });

  it("rejects malformed token responses", async () => {
    server.use(http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ data: "old-shape" })));

    await expect(fetchToken(true)).rejects.toThrow("Validation failed");
  });

  it("rejects unsuccessful responses", async () => {
    server.use(http.post("/api/v2/oauth/tokens", () => new HttpResponse(null, { status: 500 })));

    await expect(fetchToken(true)).rejects.toThrow("Failed to fetch token");
  });
});
