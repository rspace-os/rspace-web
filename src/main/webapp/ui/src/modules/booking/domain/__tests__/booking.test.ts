import * as v from "valibot";
import { describe, expect, it } from "vitest";
import {
  BookingDetailsSchema,
  BookingListDocumentSchema,
  BookingSummarySchema,
} from "@/modules/booking/domain/booking";

describe("booking response intervals", () => {
  it("accepts a retained booking whose target details are redacted", () => {
    const redacted = {
      id: 18,
      version: 2,
      target: null,
      timezone: null,
      start: "2026-08-17T10:00:00Z",
      end: "2026-08-17T11:00:00Z",
      state: "CONFIRMED",
      kind: "BOOKING",
      canViewConfiguration: false,
      privacy: "full",
      purpose: "Retained requester details",
      bookedBy: "Ada Lovelace",
      createdBy: "Grace Hopper (grace)",
      canEdit: false,
      canCancel: false,
      createdAt: "2026-08-01T09:00:00Z",
      updatedAt: "2026-08-02T10:00:00Z",
    } as const;

    const details = { ...redacted, cancellationReason: null };
    expect(v.parse(BookingDetailsSchema, details)).toEqual(details);
    const numericRequester = { ...redacted, requester: { relationTo: "users", value: 84 } };
    expect(v.parse(BookingListDocumentSchema, numericRequester)).toEqual(numericRequester);

    const expandedRequester = {
      ...redacted,
      requester: { relationTo: "users", value: { id: 84, name: "Ada Lovelace" }, globalId: "US84" },
    };
    expect(v.parse(BookingListDocumentSchema, expandedRequester)).toEqual(expandedRequester);

    const concealedRequester = { ...redacted, requester: null };
    expect(v.parse(BookingListDocumentSchema, concealedRequester)).toEqual(concealedRequester);
  });

  it("retains a valid response and reports a non-increasing interval at end", () => {
    const summary = {
      id: 17,
      target: {
        relationTo: "instruments",
        value: {
          id: 34,
          name: "Spectrometer",
          deleted: false,
          parentContainer: {
            relationTo: "containers",
            value: { id: 12, globalId: "IC12", name: "Lab" },
            globalId: "IC12",
          },
        },
        globalId: "IN34",
      },
      timezone: "Europe/Berlin",
      start: "2026-08-17T10:00:00Z",
      end: "2026-08-17T11:00:00Z",
      state: "CONFIRMED",
    };
    expect(v.parse(BookingSummarySchema, summary)).toEqual({ ...summary, kind: "BOOKING" });

    const result = v.safeParse(BookingSummarySchema, { ...summary, end: summary.start });
    expect(result.success).toBe(false);
    expect(result.issues?.map((issue) => issue.path?.map((item) => item.key).join("."))).toEqual(["end"]);
  });
});
