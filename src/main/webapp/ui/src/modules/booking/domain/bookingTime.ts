import { Temporal } from "@js-temporal/polyfill";
import i18n from "@/modules/common/i18n";
import { formatList } from "@/modules/common/i18n/listFormat";

export type ZonedDayBounds = {
  start: string;
  end: string;
  elapsedMinutes: number;
};

export type AbsoluteDisplayInterval = ZonedDayBounds & {
  date: string;
  timeZone: string;
};

export type WallClockResolution =
  | { kind: "unique"; instant: string }
  | { kind: "ambiguous"; earlier: string; later: string }
  | { kind: "nonexistent" };

export type BookingWindowDraft = {
  startDate: string;
  startTime: string;
  startOccurrence?: "earlier" | "later";
  endDate: string;
  endTime: string;
  endOccurrence?: "earlier" | "later";
};

export function parsePlainDate(value: string): Temporal.PlainDate {
  return Temporal.PlainDate.from(value);
}

/** Whether two timezone names are the same zone, such as `Etc/UTC` and the `UTC` a browser reports. */
export function sameTimeZone(first: string, second: string): boolean {
  const canonical = (timeZone: string) => new Intl.DateTimeFormat("en-US", { timeZone }).resolvedOptions().timeZone;
  return first === second || canonical(first) === canonical(second);
}

export function isPlainDate(value: string): boolean {
  try {
    return parsePlainDate(value).toString() === value;
  } catch {
    return false;
  }
}

export function addCalendarDays(value: string, days: number): string {
  return parsePlainDate(value).add({ days }).toString();
}

export function zonedDayBounds(date: string, timezone: string): ZonedDayBounds {
  const start = parsePlainDate(date).toZonedDateTime({ timeZone: timezone, plainTime: "00:00" });
  const end = parsePlainDate(date).add({ days: 1 }).toZonedDateTime({ timeZone: timezone, plainTime: "00:00" });
  return {
    start: start.toInstant().toString(),
    end: end.toInstant().toString(),
    elapsedMinutes: Number(end.toInstant().since(start.toInstant()).total("minutes")),
  };
}

/** The preferred wall-clock range. A window wholly inside a DST gap has zero elapsed minutes. */
export function displayInterval(
  date: string,
  timeZone: string,
  startTime: string,
  endTime: string,
): AbsoluteDisplayInterval {
  const startMinute = Temporal.PlainTime.from(startTime).since("00:00").total("minutes");
  const endMinute = endTime === "24:00" ? 1440 : Temporal.PlainTime.from(endTime).since("00:00").total("minutes");
  if (endMinute <= startMinute) {
    throw new RangeError("Availability window end must be after its start");
  }
  const start = dayMinuteToZonedTime(date, timeZone, wallClockToDayMinute(date, timeZone, startMinute));
  const end = dayMinuteToZonedTime(date, timeZone, wallClockToDayMinute(date, timeZone, endMinute));
  return {
    date,
    timeZone,
    start: start.toInstant().toString(),
    end: end.toInstant().toString(),
    elapsedMinutes: Number(end.toInstant().since(start.toInstant()).total("minutes")),
  };
}

export function broadUtcEnvelope(date: string, timezones: readonly string[]): { start: string; end: string } {
  if (timezones.length === 0) {
    const bounds = zonedDayBounds(date, "UTC");
    return { start: bounds.start, end: bounds.end };
  }
  const bounds = timezones.map((timezone) => zonedDayBounds(date, timezone));
  return {
    start: bounds.reduce(
      (first, value) => (Temporal.Instant.compare(value.start, first) < 0 ? value.start : first),
      bounds[0].start,
    ),
    end: bounds.reduce(
      (last, value) => (Temporal.Instant.compare(value.end, last) > 0 ? value.end : last),
      bounds[0].end,
    ),
  };
}

export function currentWallClock(
  instant: string | Temporal.Instant,
  timezone: string,
): { date: string; minute: number } {
  const zoned = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone);
  return {
    date: zoned.toPlainDate().toString(),
    minute: zoned.hour * 60 + zoned.minute + zoned.second / 60,
  };
}

/** Booking endpoints align to local scheduling-zone minutes, not display-zone coordinates. */
export function isBookingInstantAlignedToGranularity(
  instant: string,
  timezone: string,
  granularityMinutes: number,
): boolean {
  if (!Number.isInteger(granularityMinutes) || granularityMinutes <= 0) return false;
  try {
    const time = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone);
    return (
      time.second === 0 &&
      time.millisecond === 0 &&
      time.microsecond === 0 &&
      time.nanosecond === 0 &&
      (time.hour * 60 + time.minute) % granularityMinutes === 0
    );
  } catch {
    return false;
  }
}

export function instantToWallClockMinute(instant: string, date: string, timezone: string): number {
  const wallClock = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone).toPlainDateTime();
  const midnight = parsePlainDate(date).toPlainDateTime("00:00");
  return Number(wallClock.since(midnight, { largestUnit: "day" }).total("minutes"));
}

/** Timeline coordinates are elapsed minutes, so both occurrences of a repeated hour have space. */
export function instantToDayMinute(instant: string, date: string, timezone: string): number {
  return Temporal.Instant.from(instant).since(zonedDayBounds(date, timezone).start).total("minutes");
}

export function dayMinuteToZonedTime(date: string, timezone: string, minute: number): Temporal.ZonedDateTime {
  return parsePlainDate(date)
    .toZonedDateTime({ timeZone: timezone, plainTime: "00:00" })
    .add({ milliseconds: Math.round(minute * 60_000) });
}

/** Convert a preferred wall-clock boundary to the timeline's elapsed coordinate. */
export function wallClockToDayMinute(date: string, timezone: string, minute: number): number {
  const plain = parsePlainDate(date).toPlainDateTime("00:00").add({ minutes: minute });
  let zoned = plain.toZonedDateTime(timezone);
  if (!zoned.toPlainDateTime().equals(plain)) {
    // A boundary inside a skipped hour starts at the first real instant after the gap.
    zoned = zoned.add({ nanoseconds: 1 }).getTimeZoneTransition("previous") ?? zoned;
  }
  return instantToDayMinute(zoned.toInstant().toString(), date, timezone);
}

export function sliceAcrossZonedDay(
  start: string,
  end: string,
  date: string,
  timezone: string,
): { startMinute: number; endMinute: number } {
  return {
    startMinute: instantToDayMinute(start, date, timezone),
    endMinute: instantToDayMinute(end, date, timezone),
  };
}

export function resolveWallClock(date: string, time: string, timezone: string): WallClockResolution {
  const plain = parsePlainDate(date).toPlainDateTime(Temporal.PlainTime.from(time));
  const fields = {
    timeZone: timezone,
    year: plain.year,
    month: plain.month,
    day: plain.day,
    hour: plain.hour,
    minute: plain.minute,
    second: plain.second,
  };
  const earlier = Temporal.ZonedDateTime.from(fields, { disambiguation: "earlier" });
  const later = Temporal.ZonedDateTime.from(fields, { disambiguation: "later" });
  const earlierMatches = earlier.toPlainDateTime().equals(plain);
  const laterMatches = later.toPlainDateTime().equals(plain);
  if (!earlierMatches || !laterMatches) return { kind: "nonexistent" };
  if (Temporal.Instant.compare(earlier.toInstant(), later.toInstant()) === 0) {
    return { kind: "unique", instant: earlier.toInstant().toString() };
  }
  return {
    kind: "ambiguous",
    earlier: earlier.toInstant().toString(),
    later: later.toInstant().toString(),
  };
}

export function wallClockDraftFromInstants(start: string, end: string, timezone: string): BookingWindowDraft {
  const endpoint = (instant: string) => {
    const zoned = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone);
    const date = zoned.toPlainDate().toString();
    const time = `${String(zoned.hour).padStart(2, "0")}:${String(zoned.minute).padStart(2, "0")}`;
    const resolution = resolveWallClock(date, time, timezone);
    const occurrence =
      resolution.kind === "ambiguous"
        ? Temporal.Instant.compare(instant, resolution.earlier) === 0
          ? ("earlier" as const)
          : ("later" as const)
        : undefined;
    return { date, time, occurrence };
  };
  const startValue = endpoint(start);
  const endValue = endpoint(end);
  return {
    startDate: startValue.date,
    startTime: startValue.time,
    startOccurrence: startValue.occurrence,
    endDate: endValue.date,
    endTime: endValue.time,
    endOccurrence: endValue.occurrence,
  };
}

export function wallClockInstant(
  resolution: WallClockResolution | undefined,
  occurrence: "earlier" | "later" | undefined,
): string | undefined {
  if (!resolution || resolution.kind === "nonexistent") return undefined;
  if (resolution.kind === "unique") return resolution.instant;
  return occurrence ? resolution[occurrence] : undefined;
}

/** The app language when Intl can format it, otherwise en-US; for words such as durations. */
export function bookingLocale(): string {
  const language = i18n.resolvedLanguage ?? i18n.language;
  try {
    return language && Intl.DateTimeFormat.supportedLocalesOf([language]).length > 0 ? language : "en-US";
  } catch {
    return "en-US";
  }
}

/** The Booking "Time format" preference: the browser region's clock, or an explicit 12- or 24-hour one. */
export type BookingTimeFormat = "AUTOMATIC" | "H12" | "H24";

/**
 * The browser's regional format with the clock of `format`: the region's own for Automatic, otherwise a `-u-hc-`
 * extension for an explicit 12- or 24-hour clock. Numeric dates keep the region's order either way.
 */
export function bookingDateTimeLocaleFor(format: BookingTimeFormat): string {
  const locale = new Intl.DateTimeFormat().resolvedOptions().locale;
  if (format === "AUTOMATIC") return locale;
  return new Intl.Locale(locale, { hourCycle: format === "H24" ? "h23" : "h12" }).toString();
}

/**
 * The browser's regional format, for numeric dates and times, with the viewer's explicit Time format when they chose
 * one. Native date and time inputs always use the browser region, so Automatic keeps "14:00" or "2:00 PM" consistent
 * between inputs and cards.
 */
export function bookingDateTimeLocale(format: BookingTimeFormat = "AUTOMATIC"): string {
  return bookingDateTimeLocaleFor(format);
}

/** The 12- or 24-hour clock of `bookingDateTimeLocale(format)`, so times written in the app language use it too. */
export function bookingHourCycle(format: BookingTimeFormat = "AUTOMATIC"): Intl.DateTimeFormatOptions["hourCycle"] {
  return new Intl.DateTimeFormat(bookingDateTimeLocale(format), { hour: "numeric" }).resolvedOptions().hourCycle;
}

/** A wall-clock `HH:mm` time as the locale writes it, which is also how native time inputs display it. */
export function formatWallClockTime(time: string, locale = bookingDateTimeLocale()): string {
  const [hour, minute] = time.split(":").map(Number);
  return new Intl.DateTimeFormat(locale, { timeZone: "UTC", hour: "2-digit", minute: "2-digit" }).format(
    Date.UTC(1970, 0, 1, hour, minute),
  );
}

/** A plain `YYYY-MM-DD` date as the locale writes it, which is also how native date inputs display it. */
export function formatPlainDate(date: string, locale = bookingDateTimeLocale()): string {
  return new Intl.DateTimeFormat(locale, { timeZone: "UTC", year: "numeric", month: "2-digit", day: "2-digit" }).format(
    new Date(`${date}T12:00:00.000Z`),
  );
}

/** A whole-minute duration in words, for example "2 hours" or "1 day, 30 minutes". */
export function formatDurationMinutes(totalMinutes: number, locale = bookingLocale()): string {
  const parts = (
    [
      ["day", Math.floor(totalMinutes / 1440)],
      ["hour", Math.floor((totalMinutes % 1440) / 60)],
      ["minute", totalMinutes % 60],
    ] as const
  ).filter(([, value]) => value > 0);
  return formatList(
    (parts.length > 0 ? parts : [["minute", 0] as const]).map(([unit, value]) =>
      new Intl.NumberFormat(locale, { style: "unit", unit, unitDisplay: "long" }).format(value),
    ),
    locale,
    { type: "unit", style: "long" },
  );
}

/**
 * The UTC offset, such as " +02:00", after a time on a day whose clock changes, so the two occurrences of a repeated
 * hour (and the times either side of a skipped one) read differently; empty on other days. `day` is the date the time
 * is read against, by default its own.
 */
export function clockChangeOffsetLabel(time: Temporal.ZonedDateTime, day = time.toPlainDate().toString()): string {
  return zonedDayBounds(day, time.timeZoneId).elapsedMinutes === 24 * 60 ? "" : ` ${time.offset}`;
}

function zonedDateTime(value: string, timeZone: string): Temporal.ZonedDateTime {
  // Parsed as Date parses it, so any value the formatters accept also gets its offset.
  return Temporal.Instant.fromEpochMilliseconds(new Date(value).getTime()).toZonedDateTimeISO(timeZone);
}

function dateTimeFormatter(timeZone: string, locale: string, timeFormat: BookingTimeFormat): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    hourCycle: bookingHourCycle(timeFormat),
    timeZone,
  });
}

/** A date and time such as "Oct 8, 2026, 06:00"; on a clock-change day with its offset, "Oct 25, 2026, 02:30 +02:00". */
export function formatBookingDateTime(
  value: string,
  timeZone: string,
  locale = bookingLocale(),
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): string {
  return `${dateTimeFormatter(timeZone, locale, timeFormat).format(new Date(value))}${clockChangeOffsetLabel(zonedDateTime(value, timeZone))}`;
}

/**
 * A booking's start and end with the date, e.g. "Oct 8, 2026, 06:00 – 07:00", for sentences outside a dated list such
 * as the cancel confirmation. Words follow the app language; the clock follows the browser region. When either end
 * falls on a clock-change day, both ends are written as `formatBookingDateTime` writes them.
 */
export function formatBookingPeriod(
  start: string,
  end: string,
  timezone: string,
  locale = bookingLocale(),
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): string {
  const formatter = dateTimeFormatter(timezone, locale, timeFormat);
  const startOffset = clockChangeOffsetLabel(zonedDateTime(start, timezone));
  const endOffset = clockChangeOffsetLabel(zonedDateTime(end, timezone));
  if (!startOffset && !endOffset) return formatter.formatRange(new Date(start), new Date(end));
  return `${formatter.format(new Date(start))}${startOffset} – ${formatter.format(new Date(end))}${endOffset}`;
}

export function formatAgendaPeriod(
  start: string,
  end: string,
  timezone: string,
  locale: string | undefined = undefined,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): string {
  const resolvedLocale = locale ?? bookingDateTimeLocale(timeFormat);
  const timeFormatter = new Intl.DateTimeFormat(resolvedLocale, {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: timeFormat === "AUTOMATIC" ? undefined : bookingHourCycle(timeFormat),
  });
  const offsetFormatter = new Intl.DateTimeFormat(resolvedLocale, {
    timeZone: timezone,
    hour: "2-digit",
    timeZoneName: "shortOffset",
  });
  const startDate = new Date(start);
  const endDate = new Date(end);
  const offset = (date: Date) =>
    offsetFormatter.formatToParts(date).find((part) => part.type === "timeZoneName")?.value;
  const startOffset = offset(startDate);
  const endOffset = offset(endDate);

  if (startOffset && startOffset === endOffset) {
    return `${timeFormatter.formatRange(startDate, endDate)} ${startOffset}`;
  }

  const withOffset = new Intl.DateTimeFormat(resolvedLocale, {
    hourCycle: timeFormat === "AUTOMATIC" ? undefined : bookingHourCycle(timeFormat),
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "shortOffset",
  });
  return `${withOffset.format(startDate)}–${withOffset.format(endDate)}`;
}
