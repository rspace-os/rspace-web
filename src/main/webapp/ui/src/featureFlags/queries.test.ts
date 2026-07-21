import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test } from "vitest";
import { FEATURE_FLAGS } from "./generatedFeatureFlags";
import { getFeatureFlagApiToken, getFeatureFlags } from "./queries";

const server = setupServer();

beforeAll(() => {
  fetchMock.disableMocks();
  server.listen({ onUnhandledRequest: "error" });
});

beforeEach(() => sessionStorage.clear());
afterEach(() => server.resetHandlers());

afterAll(() => {
  server.close();
  fetchMock.enableMocks();
});

const validFlagResponse = {
  flags: {
    [FEATURE_FLAGS.bookingEnabled]: {
      value: true,
      baselineValue: false,
      source: "USER_OVERRIDE",
      canOverride: true,
    },
  },
};

describe("feature flag queries", () => {
  test("fetches and stores an API token", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/userform/ajax/inventoryOauthToken", ({ request: receivedRequest }) => {
        request = receivedRequest;
        return HttpResponse.json({ data: "token" });
      }),
    );

    await expect(getFeatureFlagApiToken()).resolves.toBe("token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(sessionStorage.getItem("id_token")).toBe("token");
  });

  test("fetches flags with bearer authentication", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/feature-flags", ({ request: receivedRequest }) => {
        request = receivedRequest;
        return HttpResponse.json(validFlagResponse);
      }),
    );

    await expect(getFeatureFlags("token")).resolves.toEqual(validFlagResponse);
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  test("rejects an unsuccessful response", async () => {
    server.use(
      http.get("/api/v2/feature-flags", () => new HttpResponse(null, { status: 500, statusText: "Server Error" })),
    );

    await expect(getFeatureFlags("token")).rejects.toThrow("500 Server Error");
  });
});
