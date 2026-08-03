import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import { getNextMaintenance } from "@/modules/common/app/queries/nextMaintenance";

const envelope = (docs: Array<Record<string, unknown>>) => ({
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

describe("getNextMaintenance", () => {
  it("requests the public maintenance list and returns docs[0].startDate", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.get("/api/v2/maintenances", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json(
          envelope([
            {
              id: 1,
              startDate: "2026-07-01T09:00:00.000Z",
            },
          ]),
        );
      }),
    );

    const result = await getNextMaintenance();

    expect(result).toEqual({ startDate: new Date("2026-07-01T09:00:00.000Z") });
    // Keep this filter if the caller adds authentication later.
    const requestUrl = new URL(capturedRequest?.url ?? "");
    expect(requestUrl.pathname).toBe("/api/v2/maintenances");
    expect(requestUrl.searchParams.get("limit")).toBe("1");
    expect(requestUrl.searchParams.get("where")).toMatch(/^endDate=gt=\d{4}-\d{2}-\d{2}T/);
    expect(requestUrl.searchParams.get("fields[maintenances]")).toBe("startDate");
    expect(requestUrl.searchParams.has("select[startDate]")).toBe(false);
    expect(capturedRequest?.headers.get("Authorization")).toBeNull();
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("returns null when no maintenance is scheduled (empty docs)", async () => {
    server.use(http.get("/api/v2/maintenances", () => HttpResponse.json(envelope([]))));
    expect(await getNextMaintenance()).toBeNull();
  });

  it("fails soft to null on a non-OK response", async () => {
    server.use(http.get("/api/v2/maintenances", () => new HttpResponse(null, { status: 500 })));
    expect(await getNextMaintenance()).toBeNull();
  });

  it("fails soft to null when the envelope is malformed", async () => {
    const restoreConsole = silenceConsole(["warn"], [/Could not read the next scheduled maintenance/]);
    server.use(http.get("/api/v2/maintenances", () => HttpResponse.json({ unexpected: true })));
    expect(await getNextMaintenance()).toBeNull();
    restoreConsole();
  });
});
