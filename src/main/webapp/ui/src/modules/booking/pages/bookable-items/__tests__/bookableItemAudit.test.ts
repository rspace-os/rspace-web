import { beforeAll as beforeBookingCatalog } from "vitest";
import { ApiV2ProblemError } from "@/modules/common/api/v2";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";

import {
  auditEventKind,
  auditPresetRange,
  auditRangeToQuery,
  fetchBookingConfigurationAudit,
  recordedValues,
  validateAuditDateRange,
} from "../bookableItemAudit";

import { permanentDeleteEvent } from "./bookableItemAuditFixtures";

const eventId = "a".repeat(64);
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

it.each([
  {
    openingHours: [
      { dayOfWeek: 2, start: "10:00", end: "24:00" },
      { dayOfWeek: 1, start: "09:00", end: "17:00" },
    ],
  },
  { openingHours: '[{"dayOfWeek":2,"start":"10:00","end":"24:00"},{"dayOfWeek":1,"start":"09:00","end":"17:00"}]' },
])("formats independent hours in weekday order from an audit snapshot", ({ openingHours }) => {
  expect(recordedValues({ openingHours }, "en-US")).toEqual([
    ["openingHours", "Monday 09:00 AM–05:00 PM, Tuesday 10:00 AM–12:00 AM"],
  ]);
});

it("retains unknown audit schedule values", () => {
  expect(recordedValues({ openingHours: { unknown: true } }, "en-US")).toEqual([["openingHours", '{"unknown":true}']]);
});

describe("recorded audit values", () => {
  it("reads booleans, states, kinds and the bookable-item target as words", () => {
    const words = {
      yes: "Yes",
      no: "No",
      values: { ACTIVE: "Active", CANCELLED: "Cancelled", MAINTENANCE: "Maintenance" },
    };
    expect(
      recordedValues(
        {
          enabled: true,
          allowDoubleBooking: "false",
          state: "ACTIVE",
          kind: "MAINTENANCE",
          target: '{"type":"INSTRUMENT","id":504}',
          timezone: "Australia/Sydney",
        },
        "en-US",
        words,
      ),
    ).toEqual([
      ["enabled", "Yes"],
      ["allowDoubleBooking", "No"],
      ["state", "Active"],
      ["kind", "Maintenance"],
      ["target", "IN504"],
      ["timezone", "Australia/Sydney"],
    ]);
    // An unknown state is left as recorded rather than hidden.
    expect(recordedValues({ state: "PENDING" }, "en-US", words)).toEqual([["state", "PENDING"]]);
  });

  it("formats booking instants in UTC while preserving unknown and invalid values", () => {
    const instant = "2026-10-09T08:00:00Z";
    const values = Object.fromEntries(
      recordedValues(
        {
          start: Date.parse(instant),
          end: instant,
          futureField: { nested: [1, 2] },
          maxBookingDurationMinutes: 90,
        },
        "en-US",
      ),
    );
    expect(values.start).toBe(
      new Intl.DateTimeFormat("en-US", {
        dateStyle: "medium",
        timeStyle: "long",
        timeZone: "UTC",
      }).format(new Date(instant)),
    );
    expect(values.end).toBe(values.start);
    expect(values.futureField).toBe('{"nested":[1,2]}');
    expect(values.maxBookingDurationMinutes).toBe("90");
    expect(recordedValues({ start: "invalid", end: null }, "en-US")).toEqual([
      ["start", "invalid"],
      ["end", "—"],
    ]);
  });

  it("keeps cancellation reasons as free text and omits null reasons", () => {
    expect(recordedValues({ state: "CANCELLED", cancellationReason: "2026-10-09T08:00:00Z" }, "en-US")).toEqual([
      ["state", "CANCELLED"],
      ["cancellationReason", "2026-10-09T08:00:00Z"],
    ]);
    expect(recordedValues({ state: "CONFIRMED", cancellationReason: null }, "en-US")).toEqual([["state", "CONFIRMED"]]);
  });
});

function page(docs = [event]) {
  return { docs, limit: 1000, truncated: false, start: "2026-08-01T00:00:00Z", endExclusive: "2026-08-26T00:00:00Z" };
}

describe("fetchBookingConfigurationAudit", () => {
  it("sends v2 headers and filters and returns the capped window metadata", async () => {
    let captured: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        captured = request;
        if (new URL(request.url).searchParams.has("search")) return new HttpResponse(null, { status: 400 });
        return HttpResponse.json(page());
      }),
    );

    const result = await fetchBookingConfigurationAudit({
      configurationId: 7,
      q: " PurposeMarker ",
      start: "2026-08-01T00:00:00.000Z",
      end: "2026-08-26T00:00:00.000Z",
      actions: ["WRITE"],
    });

    const url = new URL(captured?.url ?? "http://invalid");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      q: "PurposeMarker",
      start: "2026-08-01T00:00:00.000Z",
      end: "2026-08-26T00:00:00.000Z",
      actions: "WRITE",
    });
    expect(captured?.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(result).toMatchObject({
      limit: 1000,
      truncated: false,
      start: "2026-08-01T00:00:00Z",
      endExclusive: "2026-08-26T00:00:00Z",
      rows: [expect.objectContaining({ target: "bookings:41" })],
    });
  });

  it("omits paging and snapshot parameters and keeps duplicate rows", async () => {
    let capturedUrl = "";
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(page([event, event]));
      }),
    );

    const result = await fetchBookingConfigurationAudit({ configurationId: 7 });

    const parameters = new URL(capturedUrl).searchParams;
    expect(parameters.has("page")).toBe(false);
    expect(parameters.has("snapshotDate")).toBe(false);
    expect(parameters.has("snapshotFingerprint")).toBe(false);
    expect(result.rows.map(({ rowId }) => rowId)).toEqual([`${eventId}:1`, `${eventId}:2`]);
  });

  it("parses API problem responses for recovery decisions", async () => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json<ApiV2Problem>(
          {
            status: 503,
            title: "Audit unavailable",
            code: "errors.api.v2.audit.unavailable",
            detail: "Changed",
          },
          { status: 503 },
        ),
      ),
    );

    const request = fetchBookingConfigurationAudit({ configurationId: 7 });
    await expect(request).rejects.toEqual(expect.any(ApiV2ProblemError));
    await expect(request).rejects.toMatchObject({ status: 503, code: "errors.api.v2.audit.unavailable" });
  });

  it("rejects a response whose event identity is not a lowercase SHA-256 value", async () => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json(page([{ ...event, eventId: "NOT-A-HASH" }])),
      ),
    );
    await expect(fetchBookingConfigurationAudit({ configurationId: 7 })).rejects.toThrow();
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
      start: "2024-02-29T00:00:00.000Z",
      end: "2024-03-02T00:00:00.000Z",
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
      dates: {
        from: new Date("2026-02-26T00:00:00.000Z"),
        to: new Date("2026-08-27T00:00:00.000Z"),
      },
    });
    expect(validateAuditDateRange({ from: "2026-02-25", to: "2026-08-27" })).toEqual({
      valid: false,
      fields: { from: "tooWide", to: "tooWide" },
    });
  });
});

describe("DELETE audit event kinds", () => {
  it("keeps the archive kind for ordinary DELETE events", () => {
    expect(auditEventKind({ action: "DELETE", payload: { state: "ARCHIVED" } })).toBe("deleted");
  });

  it("recognizes a permanent deletion even when its prior state was ARCHIVED", () => {
    expect(auditEventKind({ ...permanentDeleteEvent, action: "DELETE" })).toBe("permanentlyDeleted");
  });
});
