import { describe, expect, it } from "vitest";
import { clockChangeCrossing } from "../bookingClockChange";

describe("clockChangeCrossing", () => {
  it("finds the spring change inside a calendar day", () => {
    expect(clockChangeCrossing("2026-03-28T11:00:00Z", "2026-03-29T10:00:00Z", "Europe/Berlin")).toEqual({
      timezone: "Europe/Berlin",
      changes: [{ instant: "2026-03-29T01:00:00Z", offsetBefore: "+01:00", offsetAfter: "+02:00", deltaMinutes: 60 }],
      daylightSaving: true,
      elapsedMinutes: 23 * 60,
      calendarDays: 1,
    });
  });

  it("finds the autumn change", () => {
    expect(clockChangeCrossing("2026-10-24T10:00:00Z", "2026-10-26T11:00:00Z", "Europe/Berlin")).toMatchObject({
      changes: [{ deltaMinutes: -60 }],
      daylightSaving: true,
      elapsedMinutes: 49 * 60,
      calendarDays: 2,
    });
  });

  it("reports a half-hour shift without assuming an hour", () => {
    // Lord Howe Island moves its clocks by 30 minutes.
    expect(clockChangeCrossing("2026-10-03T12:00:00Z", "2026-10-04T12:00:00Z", "Australia/Lord_Howe")).toMatchObject({
      changes: [{ deltaMinutes: 30 }],
      daylightSaving: true,
    });
  });

  it("finds two changes even when both endpoints have the same offset", () => {
    const crossing = clockChangeCrossing("2026-03-01T12:00:00Z", "2026-11-01T12:00:00Z", "Europe/Berlin");
    expect(crossing?.changes.map(({ deltaMinutes }) => deltaMinutes)).toEqual([60, -60]);
    expect(crossing?.daylightSaving).toBe(false);
  });

  it("counts a change exactly at the end but not one exactly at the start", () => {
    expect(clockChangeCrossing("2026-03-28T01:00:00Z", "2026-03-29T01:00:00Z", "Europe/Berlin")).toMatchObject({
      changes: [{ instant: "2026-03-29T01:00:00Z" }],
    });
    expect(clockChangeCrossing("2026-03-29T01:00:00Z", "2026-03-30T01:00:00Z", "Europe/Berlin")).toBeUndefined();
  });

  it("finds changes that are reversed within days, which the platform transition search skips", () => {
    const crossing = clockChangeCrossing("2040-10-19T00:00:00Z", "2040-10-28T00:00:00Z", "Asia/Gaza");
    const deltas = crossing?.changes.map(({ deltaMinutes }) => deltaMinutes) ?? [];
    expect(deltas).toHaveLength(2);
    expect(deltas[0]).toBe(-deltas[1]);
  });

  it("calls a permanent standard-offset change a clock change, not daylight saving", () => {
    // Khandyga moved permanently from +11 to +10 in September 2011.
    expect(clockChangeCrossing("2011-09-12T12:00:00Z", "2011-09-13T12:00:00Z", "Asia/Khandyga")).toMatchObject({
      changes: [{ deltaMinutes: -60 }],
      daylightSaving: false,
    });
  });

  it("does not call a standard-time change between seasonal ones daylight saving", () => {
    // Ciudad Juarez moved from -06:00 to -07:00 standard time on 30 November 2022.
    expect(clockChangeCrossing("2022-11-30T00:00:00Z", "2022-12-01T00:00:00Z", "America/Ciudad_Juarez")).toMatchObject({
      changes: [{ deltaMinutes: -60 }],
      daylightSaving: false,
    });
  });

  it("gives no result beyond the 527,040-minute event limit, which maintenance also follows", () => {
    expect(clockChangeCrossing("2026-01-01T00:00:00Z", "2027-01-02T00:00:00Z", "Europe/Berlin")).toBeDefined();
    expect(clockChangeCrossing("2026-01-01T00:00:00Z", "2027-01-02T00:01:00Z", "Europe/Berlin")).toBeUndefined();
  });

  it("gives no result for a zone Temporal does not know", () => {
    expect(clockChangeCrossing("2026-03-28T11:00:00Z", "2026-03-30T10:00:00Z", "GMT+01:00")).toBeUndefined();
  });

  it("ignores intervals without a change and zones without daylight saving", () => {
    expect(clockChangeCrossing("2026-08-17T09:00:00Z", "2026-08-20T09:00:00Z", "Europe/Berlin")).toBeUndefined();
    expect(clockChangeCrossing("2026-03-01T00:00:00Z", "2026-11-01T00:00:00Z", "Asia/Tokyo")).toBeUndefined();
  });
});
