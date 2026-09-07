import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getMaintenanceStatus } from "@/modules/maintenance/queries";

describe("getMaintenanceStatus", () => {
  const envelope = (docs: Array<{ canUserLoginNow: boolean }>) => ({
    docs,
    totalDocs: docs.length,
    limit: 1,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length === 0 ? 0 : 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  });

  it("reports 'in-progress' while maintenance is active", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.get("/api/v2/maintenances", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json(envelope([{ canUserLoginNow: false }]));
      }),
    );

    expect(await getMaintenanceStatus()).toBe("in-progress");
    const requestUrl = new URL(capturedRequest?.url ?? "");
    expect(requestUrl.searchParams.get("limit")).toBe("1");
    expect(requestUrl.searchParams.get("fields[maintenances]")).toBe("canUserLoginNow");
    expect(capturedRequest?.headers.get("Authorization")).toBeNull();
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it.each([
    {
      name: "reports 'clear' once maintenance is over",
      response: () => HttpResponse.json(envelope([{ canUserLoginNow: true }])),
      expected: "clear",
    },
    {
      name: "reports 'clear' when no maintenance window remains",
      response: () => HttpResponse.json(envelope([])),
      expected: "clear",
    },
    {
      name: "treats a failed status check as still in maintenance (no false redirect)",
      response: () => new HttpResponse(null, { status: 503 }),
      expected: "in-progress",
    },
    {
      name: "treats a malformed response as still in maintenance",
      response: () => HttpResponse.json({ unexpected: true }),
      expected: "in-progress",
    },
  ] as const)("$name", async ({ response, expected }) => {
    server.use(http.get("/api/v2/maintenances", response));
    expect(await getMaintenanceStatus()).toBe(expected);
  });
});
