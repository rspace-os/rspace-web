import { beforeAll as beforeBookingCatalog } from "vitest";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

import { describe, expect, test, vi } from "vitest";
import i18n from "@/modules/common/i18n";
import { resolveBookingWindow } from "../../creation/ZonedBookingWindowFields";
import {
  addCalendarDays,
  bookingAddedNoticeValues,
  bookingDateTimeFormatter,
  bookingDateTimeLocale,
  bookingHourCycle,
  bookingLocale,
  currentWallClock,
  dayMinuteToZonedTime,
  displayInterval,
  formatAgendaPeriod,
  formatBookingDateTime,
  formatBookingPeriod,
  formatDurationMinutes,
  formatPlainDate,
  formatWallClockTime,
  instantToWallClockMinute,
  instrumentZoneDiffers,
  isPlainDate,
  resolveWallClock,
  sameTimeZone,
  sliceAcrossZonedDay,
  wallClockDraftFromInstants,
  wallClockInstant,
  wallClockToDayMinute,
  zonedDayBounds,
} from "../bookingTime";

describe("bookingTime", () => {
  test("keeps recently used formatters and evicts the least recently used at capacity", () => {
    const options = { timeZone: "UTC", year: "numeric" } as const;
    const oldest = bookingDateTimeFormatter("en-US-x-oldest", options);
    const recent = bookingDateTimeFormatter("en-US-x-recent", options);
    for (let i = 0; i < 126; i++) bookingDateTimeFormatter(`en-US-x-${i}`, options);
    expect(bookingDateTimeFormatter("en-US-x-recent", options)).toBe(recent);
    bookingDateTimeFormatter("en-US-x-overflow", options);
    expect(bookingDateTimeFormatter("en-US-x-oldest", options)).not.toBe(oldest);
    expect(bookingDateTimeFormatter("en-US-x-recent", options)).toBe(recent);
    expect(bookingDateTimeFormatter("en-US", { timeZone: "UTC" })).not.toBe(
      bookingDateTimeFormatter("en-US", { timeZone: "Europe/Berlin" }),
    );
  });

  test("isolates cached day bounds by zone and protects them from caller mutation", () => {
    const berlin = zonedDayBounds("2026-03-29", "Europe/Berlin");
    berlin.start = "changed";
    expect(zonedDayBounds("2026-03-29", "Europe/Berlin").elapsedMinutes).toBe(1380);
    expect(zonedDayBounds("2026-03-29", "Europe/Berlin").start).toBe("2026-03-28T23:00:00Z");
    expect(zonedDayBounds("2026-03-29", "UTC").elapsedMinutes).toBe(1440);
  });

  test.each(["en-US", "en-GB", "de-DE"])("caches equivalent options and distinguishes %s hour cycles", (locale) => {
    for (const timeZone of ["UTC", "Europe/Berlin", "America/New_York"]) {
      const options: Intl.DateTimeFormatOptions = {
        timeZone,
        dateStyle: "medium",
        timeStyle: "long",
        hourCycle: "h23",
      };
      const formatter = bookingDateTimeFormatter(locale, options);
      expect(
        bookingDateTimeFormatter(locale, { hourCycle: "h23", timeStyle: "long", dateStyle: "medium", timeZone }),
      ).toBe(formatter);
      const twelveHour = bookingDateTimeFormatter(locale, { ...options, hourCycle: "h12" });
      expect(twelveHour).not.toBe(formatter);
      const instant = new Date("2026-11-01T18:30:00Z");
      expect(twelveHour.format(instant)).not.toBe(formatter.format(instant));
    }
    expect(bookingDateTimeFormatter("en-US", { timeZone: "UTC" })).not.toBe(
      bookingDateTimeFormatter("de-DE", { timeZone: "UTC" }),
    );
  });

  test("validates and adds calendar dates", () => {
    expect(isPlainDate("2026-02-28")).toBe(true);
    expect(isPlainDate("2026-02-30")).toBe(false);
    expect(addCalendarDays("2026-02-28", 1)).toBe("2026-03-01");
  });

  test.each([
    ["2026-02-10", 1440, "2026-02-09T23:00:00Z", "2026-02-10T23:00:00Z"],
    ["2026-03-29", 1380, "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z"],
    ["2026-10-25", 1500, "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z"],
  ])("uses DST-correct Berlin bounds for %s", (date, minutes, start, end) => {
    expect(zonedDayBounds(date, "Europe/Berlin")).toEqual({ start, end, elapsedMinutes: minutes });
  });

  test("resolves consecutive midnights independently when the first midnight is skipped", () => {
    expect(zonedDayBounds("2026-09-06", "America/Santiago")).toEqual({
      start: "2026-09-06T04:00:00Z",
      end: "2026-09-07T03:00:00Z",
      elapsedMinutes: 1380,
    });
  });

  test.each([
    ["2026-03-29", 1380, "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z"],
    ["2026-10-25", 1500, "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z"],
  ])("creates a DST-correct absolute full-day display interval for %s", (date, elapsedMinutes, start, end) => {
    expect(displayInterval(date, "Europe/Berlin", "00:00", "24:00")).toEqual({
      date,
      timeZone: "Europe/Berlin",
      start,
      end,
      elapsedMinutes,
    });
  });

  test.each(["02:45", "03:00"])("collapses a preferred window inside the spring gap ending at %s", (end) => {
    expect(displayInterval("2026-03-29", "Europe/Berlin", "02:30", end)).toMatchObject({
      start: "2026-03-29T01:00:00Z",
      end: "2026-03-29T01:00:00Z",
      elapsedMinutes: 0,
    });
  });

  test("keeps wall-clock formatting separate from elapsed timeline positions", () => {
    expect(instantToWallClockMinute("2026-03-29T01:30:00Z", "2026-03-29", "Europe/Berlin")).toBe(210);
    expect(instantToWallClockMinute("2026-03-29T01:00:00Z", "2026-03-29", "Europe/Berlin")).toBe(180);
    expect(instantToWallClockMinute("2026-03-28T23:00:00Z", "2026-03-29", "Europe/Berlin")).toBe(0);
    expect(instantToWallClockMinute("2026-03-28T22:00:00Z", "2026-03-29", "Europe/Berlin")).toBe(-60);
    expect(sliceAcrossZonedDay("2026-03-28T22:30:00Z", "2026-03-29T23:30:00Z", "2026-03-29", "Europe/Berlin")).toEqual({
      startMinute: -30,
      endMinute: 1470,
    });
  });

  test("preserves both repeated-hour occurrences and the final hour of a long day", () => {
    const date = "2026-10-25";
    const timezone = "Europe/Berlin";
    expect(sliceAcrossZonedDay("2026-10-25T00:45:00Z", "2026-10-25T01:15:00Z", date, timezone)).toEqual({
      startMinute: 165,
      endMinute: 195,
    });
    expect(wallClockToDayMinute(date, timezone, 24 * 60)).toBe(1500);
    const start = dayMinuteToZonedTime(date, timezone, 165).toInstant().toString();
    const end = dayMinuteToZonedTime(date, timezone, 195).toInstant().toString();
    expect(wallClockDraftFromInstants(start, end, timezone)).toEqual({
      startDate: date,
      startTime: "02:45",
      startOccurrence: "earlier",
      endDate: date,
      endTime: "02:15",
      endOccurrence: "later",
    });
    expect(dayMinuteToZonedTime(date, timezone, 1470).toPlainTime().toString()).toBe("23:30:00");
  });

  test("gets the local date and current minute from an injected instant", () => {
    expect(currentWallClock("2026-08-17T22:30:00Z", "Europe/Berlin")).toEqual({
      date: "2026-08-18",
      minute: 30,
    });
  });

  test("clamps a skipped-hour display boundary to the next real instant", () => {
    expect(wallClockToDayMinute("2026-03-29", "Europe/Berlin", 150)).toBe(120);
    expect(wallClockToDayMinute("2026-03-29", "Europe/Berlin", 195)).toBe(135);
  });

  test("distinguishes unique, ambiguous, and nonexistent wall-clock times", () => {
    expect(resolveWallClock("2026-02-10", "09:30", "Europe/Berlin")).toEqual({
      kind: "unique",
      instant: "2026-02-10T08:30:00Z",
    });
    expect(resolveWallClock("2026-10-25", "02:30", "Europe/Berlin")).toEqual({
      kind: "ambiguous",
      earlier: "2026-10-25T00:30:00Z",
      later: "2026-10-25T01:30:00Z",
    });
    expect(resolveWallClock("2026-03-29", "02:30", "Europe/Berlin")).toEqual({
      kind: "nonexistent",
    });
  });

  test("round-trips the selected fall-back occurrence without changing its instant", () => {
    const draft = wallClockDraftFromInstants("2026-10-25T01:30:00Z", "2026-10-25T02:30:00Z", "Europe/Berlin");

    expect(draft).toMatchObject({
      startDate: "2026-10-25",
      startTime: "02:30",
      startOccurrence: "later",
      endDate: "2026-10-25",
      endTime: "03:30",
    });
    expect(
      wallClockInstant(resolveWallClock(draft.startDate, draft.startTime, "Europe/Berlin"), draft.startOccurrence),
    ).toBe("2026-10-25T01:30:00Z");
  });

  test("shows a shared timezone only once in an agenda period", () => {
    const period = formatAgendaPeriod("2026-08-18T08:00:00Z", "2026-08-18T09:00:00Z", "Europe/Berlin", "en-GB");

    expect(period).not.toContain("GMT");
    expect(period).toBe("10:00–11:00");
  });

  test("writes regional dates in booking notices", () => {
    const values = bookingAddedNoticeValues("2026-11-02T13:00:00Z", "2026-11-02T15:00:00Z", "Europe/London", "H24");
    expect(values.date).toBe(
      new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeZone: "Europe/London" }).format(
        new Date("2026-11-02T13:00:00Z"),
      ),
    );
    expect(values.start).toBe("13:00");
    expect(values.end).toBe("15:00");
  });

  test("names the date in a booking period, even when two bookings share the times", () => {
    const period = formatBookingPeriod("2026-10-08T04:00:00Z", "2026-10-08T05:00:00Z", "Europe/Berlin", "en-US");

    expect(period).toContain("Oct 8, 2026");
    expect(period).not.toBe(
      formatBookingPeriod("2026-10-09T04:00:00Z", "2026-10-09T05:00:00Z", "Europe/Berlin", "en-US"),
    );
  });

  test("prints both dates when a booking crosses midnight in the display zone", () => {
    expect(formatBookingPeriod("2026-11-06T23:00:00Z", "2026-11-07T01:00:00Z", "UTC", "en-GB", "H24")).toBe(
      "6 Nov 2026, 23:00–7 Nov 2026, 01:00",
    );
  });

  test.each([
    ["2026-11-06T23:00:00Z", "2026-11-07T01:00:00Z", "7 Nov 2026, 00:00–02:00"],
    ["2026-11-06T22:00:00Z", "2026-11-06T23:00:00Z", "6 Nov 2026, 23:00–7 Nov 2026, 00:00"],
  ])("uses display-zone dates to elide the end date: %s to %s", (start, end, expected) => {
    expect(formatBookingPeriod(start, end, "Europe/Berlin", "en-GB", "H24")).toBe(expected);
  });

  describe("dated times on a clock-change day", () => {
    // Europe/Berlin repeats 02:00-03:00 on 2026-10-25 and skips it on 2026-03-29.
    const dateTime = (instant: string) =>
      new Intl.DateTimeFormat("en-US", {
        dateStyle: "medium",
        timeStyle: "short",
        hourCycle: bookingHourCycle("AUTOMATIC"),
        timeZone: "Europe/Berlin",
      }).format(new Date(instant));

    test("tells the two occurrences of a repeated hour apart by their offsets", () => {
      const earlier = formatBookingDateTime("2026-10-25T00:30:00Z", "Europe/Berlin", "en-US");
      const later = formatBookingDateTime("2026-10-25T01:30:00Z", "Europe/Berlin", "en-US");

      expect(earlier).toBe(`${dateTime("2026-10-25T00:30:00Z")} +02:00`);
      expect(later).toBe(`${dateTime("2026-10-25T01:30:00Z")} +01:00`);
      expect(dateTime("2026-10-25T00:30:00Z")).toBe(dateTime("2026-10-25T01:30:00Z"));
      expect(formatBookingPeriod("2026-10-25T00:30:00Z", "2026-10-25T02:30:00Z", "Europe/Berlin", "en-US")).toBe(
        `${earlier}–3:30 AM +01:00`,
      );
      expect(formatBookingPeriod("2026-10-25T01:30:00Z", "2026-10-25T02:30:00Z", "Europe/Berlin", "en-US")).toBe(
        `${later}–3:30 AM +01:00`,
      );
    });

    test("names the offsets either side of a skipped hour", () => {
      expect(formatBookingPeriod("2026-03-29T00:30:00Z", "2026-03-29T01:30:00Z", "Europe/Berlin", "en-US")).toBe(
        `${dateTime("2026-03-29T00:30:00Z")} +01:00–3:30 AM +02:00`,
      );
    });

    test("leaves an ordinary day's times without offsets", () => {
      expect(formatBookingDateTime("2026-10-08T04:00:00Z", "Europe/Berlin", "en-US")).toBe(
        dateTime("2026-10-08T04:00:00Z"),
      );
      expect(formatBookingPeriod("2026-10-08T04:00:00Z", "2026-10-08T05:00:00Z", "Europe/Berlin", "en-US")).toBe(
        `${dateTime("2026-10-08T04:00:00Z")}–7:00 AM`,
      );
    });
  });

  test("formats wall-clock times and dates the way the locale's native inputs show them", () => {
    expect(formatWallClockTime("14:05", "en-US")).toBe("02:05 PM");
    expect(formatWallClockTime("00:00", "en-US")).toBe("12:00 AM");
    expect(formatWallClockTime("24:00", "en-US")).toBe("12:00 AM");
    expect(formatWallClockTime("14:05", "en-GB")).toBe("14:05");
    expect(formatPlainDate("2026-08-17", "en-US")).toBe("08/17/2026");
    expect(formatPlainDate("2026-08-17", "de-DE")).toBe("17.08.2026");
  });

  test("spells out durations in the largest whole units", () => {
    expect(formatDurationMinutes(120, "en-US")).toBe("2 hours");
    expect(formatDurationMinutes(1470, "en-US")).toBe("1 day, 30 minutes");
    expect(formatDurationMinutes(1470, "en-US", { largestUnit: "hour" })).toBe("24 hours, 30 minutes");
    expect(formatDurationMinutes(1, "en-US")).toBe("1 minute");
    expect(formatDurationMinutes(0, "en-US")).toBe("0 minutes");
  });

  test("words follow the app language, numeric dates and times follow the browser region", () => {
    // Tests run i18next in cimode, which is not a locale Intl can format.
    expect(bookingLocale()).toBe("en-US");
    const resolvedLanguage = i18n.resolvedLanguage;
    i18n.resolvedLanguage = "en-GB";
    try {
      expect(bookingLocale()).toBe("en-GB");
      // The app language does not change the numeric format, which native inputs take from the browser.
      expect(formatWallClockTime("14:05")).toBe(formatWallClockTime("14:05", bookingDateTimeLocale()));
    } finally {
      i18n.resolvedLanguage = resolvedLanguage;
    }
    expect(formatWallClockTime("14:05", "en-GB")).toBe("14:05");
    expect(formatAgendaPeriod("2026-08-18T13:00:00Z", "2026-08-18T14:00:00Z", "UTC", "en-GB")).toMatch(
      /^13:00\s?–\s?14:00/,
    );
  });

  test("refreshes the regional locale and automatic hour cycle when the browser region changes", () => {
    const OriginalDateTimeFormat = Intl.DateTimeFormat;
    const formatter = bookingDateTimeFormatter();
    // Cached-instance spies assume the 128-entry LRU does not evict them mid-test.
    const staleRegion = vi.spyOn(formatter, "resolvedOptions").mockReturnValue({
      ...formatter.resolvedOptions(),
      locale: "en-US",
    });
    let browserLocale = "en-US";
    // biome-ignore lint/complexity/useArrowFunction: Intl.DateTimeFormat is called with new.
    const formatterConstructor = vi.spyOn(Intl, "DateTimeFormat").mockImplementation(function (locale, options) {
      return new OriginalDateTimeFormat(locale ?? browserLocale, options);
    });
    try {
      for (const [locale, hourCycle] of [
        ["en-GB", "h23"],
        ["en-US", "h12"],
        ["en-GB", "h23"],
      ] as const) {
        browserLocale = locale;
        expect(bookingDateTimeFormatter()).toBe(formatter);
        expect(formatter.resolvedOptions().locale).toBe("en-US");
        expect(bookingDateTimeLocale()).toBe(locale);
        expect(bookingHourCycle()).toBe(hourCycle);
      }
    } finally {
      formatterConstructor.mockRestore();
      staleRegion.mockRestore();
    }
  });

  test("an explicit Time format replaces the browser region's clock but not its date order", () => {
    const regional = bookingDateTimeLocale("AUTOMATIC");
    expect(bookingHourCycle("H24")).toBe("h23");
    expect(formatWallClockTime("14:05", bookingDateTimeLocale("H24"))).toBe("14:05");
    expect(formatWallClockTime("00:00", bookingDateTimeLocale("H24"))).toBe("00:00");
    expect(formatBookingDateTime("2026-10-08T12:05:00Z", "UTC", "en-US", "H24")).toBe("Oct 8, 2026, 12:05");
    expect(formatPlainDate("2026-08-17")).toBe(formatPlainDate("2026-08-17", regional));

    expect(bookingHourCycle("H12")).toBe("h12");
    expect(new Intl.Locale(bookingDateTimeLocale("H12")).hourCycle).toBe("h12");
    expect(formatBookingDateTime("2026-10-08T12:05:00Z", "UTC", "en-US", "H12").replace(/\s/g, " ")).toBe(
      "Oct 8, 2026, 12:05 PM",
    );
    expect(bookingDateTimeLocale()).toBe(regional);
  });

  test("drafts selected timeline ranges as a forward booking window", () => {
    for (const [date, timezone] of [
      ["2026-03-29", "Europe/Berlin"],
      ["2026-10-25", "Europe/Berlin"],
      ["2026-02-10", "UTC"],
    ] as const) {
      const minutes = zonedDayBounds(date, timezone).elapsedMinutes;
      for (let start = 0; start < minutes; start += 5) {
        const end = Math.min(minutes, start + 60);
        if (end <= start) continue;
        const draft = wallClockDraftFromInstants(
          dayMinuteToZonedTime(date, timezone, start).toInstant().toString(),
          dayMinuteToZonedTime(date, timezone, end).toInstant().toString(),
          timezone,
        );
        expect(resolveBookingWindow(draft, timezone).orderInvalid, `${date} ${timezone} ${start}-${end}`).toBe(false);
      }
    }
  });
});

describe("sameTimeZone", () => {
  test("treats aliases of one zone as the same zone", () => {
    expect(sameTimeZone("Etc/UTC", "UTC")).toBe(true);
    expect(sameTimeZone("Asia/Kolkata", "Asia/Calcutta")).toBe(true);
    expect(sameTimeZone("Europe/Berlin", "Europe/Paris")).toBe(false);
  });
});

describe("instrumentZoneDiffers", () => {
  test("ignores an unavailable browser zone while retaining the display comparison", () => {
    expect(instrumentZoneDiffers("UTC", "UTC", null)).toBe(false);
    expect(instrumentZoneDiffers("UTC", "Europe/Berlin", null)).toBe(true);
  });

  test("treats timezone aliases as equivalent for both comparisons", () => {
    expect(instrumentZoneDiffers("Asia/Kolkata", "Asia/Calcutta", "Asia/Kolkata")).toBe(false);
  });

  test("detects a difference from either the display zone or browser zone", () => {
    expect(instrumentZoneDiffers("Europe/Berlin", "Europe/Paris", "Europe/Berlin")).toBe(true);
    expect(instrumentZoneDiffers("UTC", "UTC", "Europe/Berlin")).toBe(true);
  });
});
