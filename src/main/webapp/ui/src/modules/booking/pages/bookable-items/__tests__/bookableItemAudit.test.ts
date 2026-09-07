import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import {
  auditPresetRange,
  auditRangeToQuery,
  fetchBookingConfigurationAudit,
  validateAuditDateRange,
} from "../bookableItemAudit";

const eventId = "a".repeat(64);
const fingerprint = "b".repeat(64);
const event = {
  eventId,
  timestamp: "2026-08-25T10:42:18Z",
  username: "ada",
  fullName: "Ada Lovelace",
  domain: "RECORD",
  action: "WRITE",
  description: "Updated booking configuration IN123",
  payload: { enabled: true, bookingConfigurationId: "booking-configurations:7" },
  target: "bookings:41",
};

function page(docs = [event]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 20,
    page: 2,
    pagingCounter: 21,
    totalPages: 2,
    hasPrevPage: true,
    hasNextPage: false,
    prevPage: 1,
    nextPage: null,
    snapshotDate: "2026-08-25",
    snapshotFingerprint: fingerprint,
  };
}

describe("fetchBookingConfigurationAudit", () => {
  it("sends v2 headers, one-based paging, UTC bounds, and a complete snapshot pair", async () => {
    let captured: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        captured = request;
        return HttpResponse.json(page());
      }),
    );

    const result = await fetchBookingConfigurationAudit({
      configurationId: 7,
      page: 1,
      dateFrom: "2026-08-01T00:00:00.000Z",
      dateTo: "2026-08-25T23:59:59.999Z",
      snapshot: { snapshotDate: "2026-08-25", snapshotFingerprint: fingerprint },
      token: "secret",
    });

    const url = new URL(captured?.url ?? "http://invalid");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: "2",
      limit: "20",
      dateFrom: "2026-08-01T00:00:00.000Z",
      dateTo: "2026-08-25T23:59:59.999Z",
      snapshotDate: "2026-08-25",
      snapshotFingerprint: fingerprint,
    });
    expect(captured?.headers.get("Authorization")).toBe("Bearer secret");
    expect(captured?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(result).toMatchObject({
      totalDocs: 1,
      totalPages: 2,
      hasPrevPage: true,
      hasNextPage: false,
      snapshotDate: "2026-08-25",
      snapshotFingerprint: fingerprint,
      rows: [expect.objectContaining({ target: "bookings:41" })],
    });
  });

  it("sends neither snapshot field for a new result set and keeps duplicate rows", async () => {
    let capturedUrl = "";
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(page([event, event]));
      }),
    );

    const result = await fetchBookingConfigurationAudit({ configurationId: 7, page: 0, token: "token" });

    const parameters = new URL(capturedUrl).searchParams;
    expect(parameters.get("page")).toBe("1");
    expect(parameters.has("snapshotDate")).toBe(false);
    expect(parameters.has("snapshotFingerprint")).toBe(false);
    expect(result.rows.map(({ rowId }) => rowId)).toEqual([`${eventId}:1`, `${eventId}:2`]);
  });

  it("parses API problem responses for recovery decisions", async () => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json(
          { status: 409, code: "errors.api.v2.audit.snapshot.changed", detail: "Changed" },
          { status: 409 },
        ),
      ),
    );

    const request = fetchBookingConfigurationAudit({ configurationId: 7, page: 0, token: "token" });
    await expect(request).rejects.toEqual(expect.any(ApiV2ProblemError));
    await expect(request).rejects.toMatchObject({ status: 409, code: "errors.api.v2.audit.snapshot.changed" });
  });

  it("rejects a response whose event identity is not a lowercase SHA-256 value", async () => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json(page([{ ...event, eventId: "NOT-A-HASH" }])),
      ),
    );
    await expect(fetchBookingConfigurationAudit({ configurationId: 7, page: 0, token: "token" })).rejects.toThrow();
  });
});

describe("audit UTC date ranges", () => {
  it("makes preset labels inclusive", () => {
    const today = new Date("2026-08-27T22:30:00-05:00");
    expect(auditPresetRange(7, today)).toEqual({ from: "2026-08-22", to: "2026-08-28" });
    expect(auditPresetRange(30, new Date("2024-03-01T12:00:00Z"))).toEqual({
      from: "2024-02-01",
      to: "2024-03-01",
    });
    expect(auditPresetRange(90, new Date("2026-08-27T00:00:00Z"))).toEqual({
      from: "2026-05-30",
      to: "2026-08-27",
    });
  });

  it("converts an inclusive range to exact source-compatible UTC instants", () => {
    expect(auditRangeToQuery({ from: "2024-02-29", to: "2024-03-01" })).toEqual({
      dateFrom: "2024-02-29T00:00:00.000Z",
      dateTo: "2024-03-01T23:59:59.999Z",
    });
  });

  it("identifies blank, impossible, inverted, and over-wide fields", () => {
    expect(validateAuditDateRange({ from: "", to: "" })).toEqual({
      valid: false,
      fields: { from: "required", to: "required" },
    });
    expect(validateAuditDateRange({ from: "2025-02-29", to: "2026-08-27" })).toEqual({
      valid: false,
      fields: { from: "invalid" },
    });
    expect(validateAuditDateRange({ from: "2026-08-28", to: "2026-08-27" })).toEqual({
      valid: false,
      fields: { from: "inverted", to: "inverted" },
    });
    expect(validateAuditDateRange({ from: "2026-02-26", to: "2026-08-27" })).toEqual({
      valid: true,
      range: { from: "2026-02-26", to: "2026-08-27" },
    });
    expect(validateAuditDateRange({ from: "2026-02-25", to: "2026-08-27" })).toEqual({
      valid: false,
      fields: { from: "tooWide", to: "tooWide" },
    });
  });
});
