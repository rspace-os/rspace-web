import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { getNextMaintenance } from "@/modules/common/app/queries/nextMaintenance";

const envelope = (docs: Array<Record<string, unknown>>) => ({
  docs,
  totalDocs: docs.length,
  limit: 1,
  page: 1,
  totalPages: docs.length === 0 ? 0 : 1,
  hasPrevPage: false,
  hasNextPage: false,
  prevPage: null,
  nextPage: null,
});

// vitest-fetch-mock (enabled globally in src/__tests__/setup.ts) replaces
// globalThis.fetch, which shadows the real fetch MSW's node interceptor needs
// to hook. Disable it for this file only and restore it afterwards so other
// jsdom suites are unaffected (each test file gets its own setupFiles pass).
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

describe("getNextMaintenance", () => {
  it("requests /api/v2/maintenances with the bearer token and returns docs[0].startDate", async () => {
    let capturedRequest: Request | undefined;
    server.use(
      http.get("/api/v2/maintenances", ({ request }) => {
        capturedRequest = request;
        return HttpResponse.json(
          envelope([
            {
              id: 1,
              startDate: "2026-07-01T09:00:00.000Z",
              endDate: "2026-07-01T10:00:00.000Z",
              stopUserLoginDate: "2026-07-01T08:50:00.000Z",
              message: "Scheduled upgrade",
            },
          ]),
        );
      }),
    );

    const result = await getNextMaintenance("tok-123");

    expect(result).toEqual({ startDate: new Date("2026-07-01T09:00:00.000Z") });
    expect(capturedRequest?.url).toContain("/api/v2/maintenances?limit=1");
    expect(capturedRequest?.headers.get("Authorization")).toBe("Bearer tok-123");
    expect(capturedRequest?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("returns null when no maintenance is scheduled (empty docs)", async () => {
    server.use(http.get("/api/v2/maintenances", () => HttpResponse.json(envelope([]))));
    expect(await getNextMaintenance("tok-123")).toBeNull();
  });

  it("fails soft to null on a non-OK response", async () => {
    server.use(http.get("/api/v2/maintenances", () => new HttpResponse(null, { status: 500 })));
    expect(await getNextMaintenance("tok-123")).toBeNull();
  });

  it("fails soft to null when the envelope is malformed", async () => {
    const restoreConsole = silenceConsole(["warn"], [/Could not read the next scheduled maintenance/]);
    server.use(http.get("/api/v2/maintenances", () => HttpResponse.json({ unexpected: true })));
    expect(await getNextMaintenance("tok-123")).toBeNull();
    restoreConsole();
  });
});
