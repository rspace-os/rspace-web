import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getMaintenanceStatus } from "@/modules/maintenance/queries";

describe("getMaintenanceStatus", () => {
  const envelope = (canUserLoginNow: boolean) => ({
    docs: [{ canUserLoginNow }],
    totalDocs: 1,
    limit: 1,
    page: 1,
    pagingCounter: 1,
    totalPages: 1,
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
        return HttpResponse.json(envelope(false));
      }),
    );

    expect(await getMaintenanceStatus()).toBe("in-progress");
    const requestUrl = new URL(capturedRequest?.url ?? "");
    expect(requestUrl.searchParams.get("limit")).toBe("1");
    expect(requestUrl.searchParams.get("fields[maintenances]")).toBe("canUserLoginNow");
    expect(capturedRequest?.headers.get("Authorization")).toBeNull();
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("reports 'clear' once maintenance is over", async () => {
    server.use(http.get("/api/v2/maintenances", () => HttpResponse.json(envelope(true))));
    expect(await getMaintenanceStatus()).toBe("clear");
  });

  it("reports 'clear' when no maintenance window remains", async () => {
    server.use(
      http.get("/api/v2/maintenances", () =>
        HttpResponse.json({
          ...envelope(true),
          docs: [],
          totalDocs: 0,
          totalPages: 0,
        }),
      ),
    );

    expect(await getMaintenanceStatus()).toBe("clear");
  });

  it("treats a failed status check as still in maintenance (no false redirect)", async () => {
    server.use(http.get("/api/v2/maintenances", () => new HttpResponse(null, { status: 503 })));
    expect(await getMaintenanceStatus()).toBe("in-progress");
  });

  it("treats a malformed response as still in maintenance", async () => {
    server.use(http.get("/api/v2/maintenances", () => HttpResponse.json({ unexpected: true })));
    expect(await getMaintenanceStatus()).toBe("in-progress");
  });
});
