import { Temporal } from "@js-temporal/polyfill";

export type ZonedDayBounds = {
  start: string;
  end: string;
  elapsedMinutes: number;
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
  const end = start.add({ days: 1 });
  return {
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

export function instantToWallClockMinute(instant: string, date: string, timezone: string): number {
  const wallClock = Temporal.Instant.from(instant).toZonedDateTimeISO(timezone).toPlainDateTime();
  const midnight = parsePlainDate(date).toPlainDateTime("00:00");
  return Number(wallClock.since(midnight, { largestUnit: "day" }).total("minutes"));
}

export function sliceAcrossWallClockDay(
  start: string,
  end: string,
  date: string,
  timezone: string,
): { startMinute: number; endMinute: number } {
  return {
    startMinute: instantToWallClockMinute(start, date, timezone),
    endMinute: instantToWallClockMinute(end, date, timezone),
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

export function formatAgendaPeriod(start: string, end: string, timezone: string, locale = "en-US"): string {
  const formatter = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "shortOffset",
  });
  return `${formatter.format(new Date(start))}–${formatter.format(new Date(end))}`;
}
