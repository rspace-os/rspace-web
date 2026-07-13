import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { DEFAULT_APP_CONFIG, getAppConfig } from "@/modules/common/app/queries/config";

const server = setupServer();

beforeAll(() => {
  fetchMock.disableMocks();
  server.listen({ onUnhandledRequest: "error" });
});

afterEach(() => server.resetHandlers());

afterAll(() => {
  server.close();
  fetchMock.enableMocks();
});

describe("getAppConfig", () => {
  it("returns validated deployment configuration", async () => {
    let request: Request | undefined;
    const response = {
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

  it("trims optional About-page values", async () => {
    server.use(
      http.get("/api/v2/config", () =>
        HttpResponse.json({
          branding: { bannerImageUrl: "/public/banner" },
          helpLinks: [],
          deploymentDescription: "  Configured for advanced research teams  ",
          deploymentHelpEmail: "   ",
        }),
      ),
    );

    await expect(getAppConfig()).resolves.toMatchObject({
      deploymentDescription: "Configured for advanced research teams",
      deploymentHelpEmail: "",
    });
  });

  it("parses an embedded next-maintenance window", async () => {
    const response = {
      branding: { bannerImageUrl: "/public/banner" },
      helpLinks: [],
      deploymentDescription: "",
      deploymentHelpEmail: "",
      nextMaintenance: {
        id: 1,
        startDate: "2026-07-16T09:00:00.000Z",
        endDate: "2026-07-16T10:00:00.000Z",
        stopUserLoginDate: null,
        message: "Planned upgrade",
      },
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
