import { Temporal } from "@js-temporal/polyfill";
import { describe, expect, it } from "vitest";
import {
  BOOKING_READ_FIELDS,
  BookingUnavailableError,
  mapBookingDetails,
  mapBookingListDocument,
  mapBookingSummary,
  retryBookingRead,
  type SelectedBooking,
} from "@/modules/booking/domain/booking";
import {
  ApiV2NetworkError,
  ApiV2ProblemError,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
} from "@/modules/common/api/v2/ApiV2ProblemError";
import { apiV2Fields } from "@/modules/common/api/v2/apiV2Selected";

describe("booking response intervals", () => {
  it("accepts a retained booking whose target details are redacted", () => {
    const redacted = {
      id: 18,
      version: 2,
      target: null,
      timezone: null,
      start: Temporal.Instant.from("2026-08-17T10:00:00Z"),
      end: Temporal.Instant.from("2026-08-17T11:00:00Z"),
      state: "CONFIRMED",
      kind: "BOOKING",
      canViewConfiguration: false,
      privacy: "full",
      purpose: "Retained requester details",
      bookedBy: "Ada Lovelace",
      createdBy: "Grace Hopper (grace)",
      canEdit: false,
      canCancel: false,
      createdAt: Temporal.Instant.from("2026-08-01T09:00:00Z"),
      updatedAt: Temporal.Instant.from("2026-08-02T10:00:00Z"),
    } as const;

    expect(mapBookingDetails(redacted)).toEqual(redacted);
    expect(mapBookingListDocument({ ...redacted, requester: { relationTo: "users", value: 84 } })).toEqual({
      ...redacted,
      requester: { relationTo: "users", value: 84 },
    });
  });

  it("retains a valid response and reports a non-increasing interval at end", () => {
    const target = {
      relationTo: "instruments" as const,
      value: {
        id: 34,
        name: "Spectrometer",
        deleted: false,
        parentContainer: {
          relationTo: "containers" as const,
          value: { id: 12, globalId: "IC12", name: "Lab" },
          globalId: "IC12",
        },
      },
      globalId: "IN34",
    };
    const summary = {
      id: 17,
      target,
      timezone: "Europe/Berlin",
      start: Temporal.Instant.from("2026-08-17T10:00:00Z"),
      end: Temporal.Instant.from("2026-08-17T11:00:00Z"),
      state: "CONFIRMED" as const,
      kind: "BOOKING" as const,
    };
    const document: SelectedBooking = {
      ...summary,
      version: 1,
      canViewConfiguration: true,
      purpose: null,
      bookedBy: null,
      createdBy: null,
      privacy: "full",
      canEdit: true,
      canCancel: true,
      createdAt: Temporal.Instant.from("2026-08-01T10:00:00Z"),
      updatedAt: Temporal.Instant.from("2026-08-01T10:00:00Z"),
    };
    expect(mapBookingSummary(document)).toEqual(summary);

    expect(() => mapBookingSummary({ ...document, end: document.start })).toThrow("Booking end must follow start");
  });
});

it("requests the booking read fields in a fixed order", () => {
  expect(apiV2Fields(BOOKING_READ_FIELDS)).toBe(
    "id,version,target,canViewConfiguration,timezone,start,end,state,kind,purpose,bookedBy,createdBy,privacy,canEdit,canCancel,createdAt,updatedAt",
  );
});

describe("booking read retries", () => {
  it.each([
    ["a 403 problem", new ApiV2ProblemError(403, undefined, "Forbidden")],
    ["an undeclared 404", new ApiV2UnexpectedResponseError("undeclared", { status: 404, media: "text/html" })],
    ["an ended session on the login page", new ApiV2SessionEndedError(200, undefined)],
    ["an unavailable booking", new BookingUnavailableError("busy")],
  ])("does not retry %s", (_, error) => {
    expect(retryBookingRead(0, error)).toBe(false);
  });

  it.each([
    ["a network failure", new ApiV2NetworkError(new TypeError("offline"))],
    ["a 503 problem", new ApiV2ProblemError(503, undefined, "Unavailable")],
  ])("retries %s twice", (_, error) => {
    expect([0, 1, 2].map((count) => retryBookingRead(count, error))).toEqual([true, true, false]);
  });
});
