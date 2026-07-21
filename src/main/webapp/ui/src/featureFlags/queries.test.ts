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

  test("fetches every page", async () => {
    const requestedPages: string[] = [];
    server.use(
      http.get("/api/v2/feature-flags", ({ request }) => {
        const page = new URL(request.url).searchParams.get("page") ?? "1";
        requestedPages.push(page);
        return HttpResponse.json(
          page === "1"
            ? { ...validFlagResponse, docs: [], totalPages: 2, hasNextPage: true, nextPage: 2 }
            : {
                ...validFlagResponse,
                page: 2,
                pagingCounter: 101,
                totalPages: 2,
                hasPrevPage: true,
                prevPage: 1,
              },
        );
      }),
    );

    await expect(getFeatureFlags("token")).resolves.toMatchObject({
      flags: { [FEATURE_FLAGS.bookingEnabled]: { value: true } },
    });
    expect(requestedPages).toEqual(["1", "2"]);
  });

  test("rejects an unsuccessful response", async () => {
    server.use(
      http.get("/api/v2/feature-flags", () => new HttpResponse(null, { status: 500, statusText: "Server Error" })),
    );

    await expect(getFeatureFlags("token")).rejects.toThrow("500 Server Error");
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
