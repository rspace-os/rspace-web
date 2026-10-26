import { describe, expect, it } from "vitest";
import { openingIntervals } from "../bookingOpeningHours";

describe("absolute opening intervals", () => {
  it("clips independently configured weekdays and omits closed dates", () => {
    expect(
      openingIntervals(
        {
          openingHours: [
            { dayOfWeek: 1, start: "08:00", end: "17:00" },
            { dayOfWeek: 2, start: "10:00", end: "24:00" },
          ],
        },
        "UTC",
        { start: "2026-08-17T09:00:00Z", end: "2026-08-20T00:00:00Z" },
      ),
    ).toEqual([
      { start: "2026-08-17T09:00:00Z", end: "2026-08-17T17:00:00Z" },
      { start: "2026-08-18T10:00:00Z", end: "2026-08-19T00:00:00Z" },
    ]);
  });

  it.each([
    ["2026-03-29", "2026-03-29T01:30:00Z", "2026-03-29T02:00:00Z"],
    ["2026-10-25", "2026-10-25T00:30:00Z", "2026-10-25T03:00:00Z"],
  ])("resolves the scheduling-zone DST boundaries on %s", (date, start, end) => {
    expect(
      openingIntervals({ openingHours: [{ dayOfWeek: 7, start: "02:30", end: "04:00" }] }, "Europe/Berlin", {
        start: `${date}T00:00:00Z`,
        end: `${date}T22:00:00Z`,
      }),
    ).toEqual([{ start, end }]);
  });
});
