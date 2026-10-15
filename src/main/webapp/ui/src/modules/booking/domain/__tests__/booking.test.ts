import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { BookingSummarySchema } from "@/modules/booking/domain/booking";

describe("booking response intervals", () => {
  it("retains a valid response and reports a non-increasing interval at end", () => {
    const summary = {
      id: 17,
      target: {
        relationTo: "booking-instruments",
        value: { id: 34, name: "Spectrometer", deleted: false },
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
