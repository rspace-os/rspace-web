import { describe, expect, it } from "vitest";
import { mapBookingSummary } from "@/modules/booking/domain/booking";

describe("booking response intervals", () => {
  it("retains a valid response and reports a non-increasing interval at end", () => {
    const summary = {
      id: 17,
      target: {
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
      },
      timezone: "Europe/Berlin",
      start: "2026-08-17T10:00:00Z",
      end: "2026-08-17T11:00:00Z",
      state: "CONFIRMED" as const,
    };
    expect(mapBookingSummary(summary)).toEqual({ ...summary, kind: "BOOKING" });

    expect(() => mapBookingSummary({ ...summary, end: summary.start })).toThrow("Booking end must follow start");
  });
});
