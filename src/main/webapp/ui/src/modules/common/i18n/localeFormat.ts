export function formatNumber(value: number, language: string | undefined): string {
  return new Intl.NumberFormat(language).format(value);
}

type WeekInfoLocale = Intl.Locale & { getWeekInfo?: () => { firstDay: number }; weekInfo?: { firstDay: number } };

/** The first day of the week where the language is used, as an ISO weekday: 1 is Monday and 7 is Sunday. */
export function firstDayOfWeek(language: string | undefined): number {
  try {
    const locale = new Intl.Locale(language || "en-US") as WeekInfoLocale;
    return (locale.getWeekInfo?.() ?? locale.weekInfo)?.firstDay ?? 1;
  } catch {
    return 1;
  }
}

const RELATIVE_TIME_UNITS: ReadonlyArray<{ unit: Intl.RelativeTimeFormatUnit; seconds: number }> = [
  { unit: "year", seconds: 60 * 60 * 24 * 365 },
  { unit: "month", seconds: 60 * 60 * 24 * 30 },
  { unit: "day", seconds: 60 * 60 * 24 },
  { unit: "hour", seconds: 60 * 60 },
  { unit: "minute", seconds: 60 },
  { unit: "second", seconds: 1 },
];

/** The time from `now` to `target` in the largest whole unit that fits, such as "in 2 hours" or "yesterday". */
export function formatRelativeTime(target: Date, language: string | undefined, now: Date = new Date()): string {
  const diffInSeconds = Math.floor((target.getTime() - now.getTime()) / 1000);
  const format = new Intl.RelativeTimeFormat(language, { numeric: "auto" });
  const match = RELATIVE_TIME_UNITS.find(({ seconds }) => Math.abs(diffInSeconds) >= seconds);
  return match ? format.format(Math.floor(diffInSeconds / match.seconds), match.unit) : format.format(0, "second");
}

export function firstGrapheme(value: string, language: string | undefined): string {
  return (
    new Intl.Segmenter(language, { granularity: "grapheme" }).segment(value)[Symbol.iterator]().next().value?.segment ??
    ""
  );
}

function formatInstant(date: Date, language: string | undefined, options: Intl.DateTimeFormatOptions, raw: unknown) {
  return Number.isNaN(date.getTime()) ? String(raw) : new Intl.DateTimeFormat(language, options).format(date);
}

/** An instant, given as a `Date` or an ISO 8601 date-time string, in the viewer's time zone. */
export function formatDateTime(value: Date | string, language: string | undefined): string {
  return formatInstant(new Date(value), language, { dateStyle: "medium", timeStyle: "short" }, value);
}

/** A calendar date (`YYYY-MM-DD`), kept on the same day in every time zone. */
export function formatDate(value: string, language: string | undefined): string {
  return formatInstant(new Date(`${value}T00:00:00Z`), language, { dateStyle: "medium", timeZone: "UTC" }, value);
}

/** A wall-clock time (`HH:mm` or `HH:mm:ss`). */
export function formatTime(value: string, language: string | undefined): string {
  return formatInstant(new Date(`1970-01-01T${value}Z`), language, { timeStyle: "short", timeZone: "UTC" }, value);
}
