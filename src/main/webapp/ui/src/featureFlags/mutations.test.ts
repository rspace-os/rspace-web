import { HttpResponse, http } from "msw";
import { describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { FEATURE_FLAGS } from "./generatedFeatureFlags";
import { clearFeatureFlagOverride, setFeatureFlagBaseline, setFeatureFlagOverride } from "./mutations";

describe("feature flag mutations", () => {
  test("sets an override", async () => {
    let request: Request | undefined;
    server.use(
      http.patch("/api/v2/feature-flags/:flagName", ({ request: receivedRequest }) => {
        request = receivedRequest.clone();
        return HttpResponse.json({});
      }),
    );

    await setFeatureFlagOverride({ flagName: FEATURE_FLAGS.bookingEnabled, value: true }, "token");

    expect(request?.method).toBe("PATCH");
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(request?.headers.get("Content-Type")).toBe("application/json");
    await expect(request?.json()).resolves.toEqual({ overrideValue: true });
  });

  test("sets a baseline", async () => {
    let request: Request | undefined;
    server.use(
      http.patch("/api/v2/feature-flags/:flagName", ({ request: receivedRequest }) => {
        request = receivedRequest.clone();
        return HttpResponse.json({});
      }),
    );

    await setFeatureFlagBaseline({ flagName: FEATURE_FLAGS.bookingEnabled, value: false }, "token");

    await expect(request?.json()).resolves.toEqual({ baselineValue: false });
  });

  test("clears an override", async () => {
    let request: Request | undefined;
    server.use(
      http.patch("/api/v2/feature-flags/:flagName", ({ request: receivedRequest }) => {
        request = receivedRequest.clone();
        return HttpResponse.json({});
      }),
    );

    await clearFeatureFlagOverride({ flagName: FEATURE_FLAGS.bookingEnabled }, "token");

    expect(request?.method).toBe("PATCH");
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    await expect(request?.json()).resolves.toEqual({ overrideValue: null });
  });

  test("rejects an unsuccessful write", async () => {
    server.use(
      http.patch(
        "/api/v2/feature-flags/:flagName",
        () => new HttpResponse(null, { status: 403, statusText: "Forbidden" }),
      ),
    );

    await expect(
      setFeatureFlagOverride({ flagName: FEATURE_FLAGS.bookingEnabled, value: true }, "token"),
    ).rejects.toThrow("403 Forbidden");
  });
});
