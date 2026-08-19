import { HttpResponse, http } from "msw";
import { describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { FEATURE_FLAGS } from "./generatedFeatureFlags";
import { patchFeatureFlag } from "./mutations";

describe("feature flag mutations", () => {
  test("sets an override", async () => {
    let request: Request | undefined;
    server.use(
      http.patch("/api/v2/feature-flags/:flagName", ({ request: receivedRequest }) => {
        request = receivedRequest.clone();
        return HttpResponse.json({});
      }),
    );

    await patchFeatureFlag({ flagName: FEATURE_FLAGS.bookingEnabled, document: { overrideValue: true } }, "token");

    expect(request?.method).toBe("PATCH");
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(request?.headers.get("Content-Type")).toBe("application/json");
    await expect(request?.json()).resolves.toEqual({ overrideValue: true });
  });

  test("rejects an unsuccessful write", async () => {
    server.use(
      http.patch(
        "/api/v2/feature-flags/:flagName",
        () => new HttpResponse(null, { status: 403, statusText: "Forbidden" }),
      ),
    );

    await expect(
      patchFeatureFlag({ flagName: FEATURE_FLAGS.bookingEnabled, document: { overrideValue: true } }, "token"),
    ).rejects.toThrow("403 Forbidden");
  });
});
