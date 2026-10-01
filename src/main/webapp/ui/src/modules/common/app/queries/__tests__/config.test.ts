import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
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
  ])("fails soft for %s", async (_description, response) => {
    const restoreConsole = silenceConsole(["warn"], [/Could not read app configuration/]);
    server.use(http.get("/api/v2/config", response));

    await expect(getAppConfig()).resolves.toEqual(DEFAULT_APP_CONFIG);
    restoreConsole();
  });
});
