import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";
import { FEATURE_FLAGS } from "./generatedFeatureFlags";
import { clearFeatureFlagOverride, setFeatureFlagBaseline, setFeatureFlagOverride } from "./mutations";

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

describe("feature flag mutations", () => {
  test("sets an override", async () => {
    let request: Request | undefined;
    server.use(
      http.put("/api/v2/feature-flags/:flagName/override", ({ request: receivedRequest }) => {
        request = receivedRequest.clone();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await setFeatureFlagOverride({ flagName: FEATURE_FLAGS.bookingEnabled, value: true }, "token");

    expect(request?.method).toBe("PUT");
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(request?.headers.get("Content-Type")).toBe("application/json");
    await expect(request?.json()).resolves.toEqual({ value: true });
  });

  test("sets a baseline", async () => {
    let request: Request | undefined;
    server.use(
      http.put("/api/v2/feature-flags/:flagName/baseline", ({ request: receivedRequest }) => {
        request = receivedRequest.clone();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await setFeatureFlagBaseline({ flagName: FEATURE_FLAGS.bookingEnabled, value: false }, "token");

    await expect(request?.json()).resolves.toEqual({ value: false });
  });

  test("clears an override", async () => {
    let request: Request | undefined;
    server.use(
      http.delete("/api/v2/feature-flags/:flagName/override", ({ request: receivedRequest }) => {
        request = receivedRequest.clone();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await clearFeatureFlagOverride({ flagName: FEATURE_FLAGS.bookingEnabled }, "token");

    expect(request?.method).toBe("DELETE");
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    await expect(request?.text()).resolves.toBe("");
  });

  test("rejects an unsuccessful write", async () => {
    server.use(
      http.put(
        "/api/v2/feature-flags/:flagName/override",
        () => new HttpResponse(null, { status: 403, statusText: "Forbidden" }),
      ),
    );

    await expect(
      setFeatureFlagOverride({ flagName: FEATURE_FLAGS.bookingEnabled, value: true }, "token"),
    ).rejects.toThrow("403 Forbidden");
  });
});
