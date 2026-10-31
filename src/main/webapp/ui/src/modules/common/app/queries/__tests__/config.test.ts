import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_APP_CONFIG, getAppConfig } from "@/modules/common/app/queries/config";

describe("getAppConfig", () => {
  it("returns validated deployment configuration", async () => {
    let request: Request | undefined;
    const response = {
      version: "2.99.1",
      branding: { bannerImageUrl: "/public/banner" },
      helpLinks: [{ label: "Support", url: "https://example.org/support" }],
      deploymentDescription: "Configured for advanced research teams",
      deploymentHelpEmail: "groups@example.com",
    };
    server.use(
      http.get("/api/v2/config", ({ request: receivedRequest }) => {
        request = receivedRequest;
        return HttpResponse.json(response);
      }),
    );

    await expect(getAppConfig()).resolves.toEqual(response);
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it.each(["", "legacy address", "o'brien@uni.ac.uk", "admin@localhost"])(
    "accepts server-provided help email %s",
    async (deploymentHelpEmail) => {
      server.use(http.get("/api/v2/config", () => HttpResponse.json({ ...DEFAULT_APP_CONFIG, deploymentHelpEmail })));
      await expect(getAppConfig()).resolves.toMatchObject({ deploymentHelpEmail });
    },
  );

  it("accepts nullable deployment metadata", async () => {
    const response = {
      version: "2.99.1",
      branding: { bannerImageUrl: "/public/banner" },
      helpLinks: [],
      deploymentDescription: null,
      deploymentHelpEmail: null,
    };
    server.use(http.get("/api/v2/config", () => HttpResponse.json(response)));

    await expect(getAppConfig()).resolves.toEqual(response);
  });

  it.each([
    ["an unsuccessful response", () => new HttpResponse(null, { status: 500 })],
    ["a malformed response", () => HttpResponse.json({ unexpected: true })],
    ["a network failure", () => HttpResponse.error()],
  ])("rejects %s", async (_description, response) => {
    server.use(http.get("/api/v2/config", response));

    await expect(getAppConfig()).rejects.toThrow();
  });
});
