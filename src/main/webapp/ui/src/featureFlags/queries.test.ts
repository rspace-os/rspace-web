import { HttpResponse, http } from "msw";
import { describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { FEATURE_FLAGS } from "./generatedFeatureFlags";
import { getFeatureFlags } from "./queries";

const validFlagResponse = {
  docs: [
    {
      name: FEATURE_FLAGS.bookingEnabled,
      value: true,
      baselineValue: false,
      source: "USER_OVERRIDE",
      canOverride: true,
    },
  ],
  totalDocs: 1,
  limit: 100,
  page: 1,
  pagingCounter: 1,
  totalPages: 1,
  hasPrevPage: false,
  hasNextPage: false,
  prevPage: null,
  nextPage: null,
};

describe("feature flag queries", () => {
  test("fetches flags with bearer authentication", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/feature-flags", ({ request: receivedRequest }) => {
        request = receivedRequest;
        return HttpResponse.json(validFlagResponse);
      }),
    );

    await expect(getFeatureFlags("token")).resolves.toEqual({
      flags: {
        [FEATURE_FLAGS.bookingEnabled]: {
          value: true,
          baselineValue: false,
          source: "USER_OVERRIDE",
          canOverride: true,
        },
      },
    });
    expect(request?.headers.get("Authorization")).toBe("Bearer token");
    expect(request?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(new URL(request?.url ?? "http://localhost").searchParams.get("limit")).toBe("100");
  });

  test("rejects an unsuccessful response", async () => {
    server.use(
      http.get("/api/v2/feature-flags", () => new HttpResponse(null, { status: 500, statusText: "Server Error" })),
    );

    await expect(getFeatureFlags("token")).rejects.toMatchObject({
      message: "500 Server Error",
      status: 500,
    });
  });

  test("disables a flag when its document is invalid", async () => {
    server.use(
      http.get("/api/v2/feature-flags", () =>
        HttpResponse.json({
          ...validFlagResponse,
          docs: [{ name: FEATURE_FLAGS.bookingEnabled, value: true }],
        }),
      ),
    );

    await expect(getFeatureFlags("token")).resolves.toEqual({
      flags: {
        [FEATURE_FLAGS.bookingEnabled]: {
          value: false,
          baselineValue: false,
          source: "DEFAULT",
          canOverride: false,
        },
      },
    });
  });

  test("rejects pagination that cannot reach the next page", async () => {
    server.use(
      http.get("/api/v2/feature-flags", () =>
        HttpResponse.json({
          ...validFlagResponse,
          totalPages: 2,
          hasNextPage: true,
          nextPage: 1,
        }),
      ),
    );

    await expect(getFeatureFlags("token")).rejects.toThrow("Feature flag pagination did not advance");
  });
});
