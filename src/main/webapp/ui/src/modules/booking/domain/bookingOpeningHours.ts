import * as v from "valibot";

/** ISO 8601 weekdays, matching Java's DayOfWeek numbering. */
export const ALL_ISO_WEEKDAYS: readonly number[] = [1, 2, 3, 4, 5, 6, 7];

const WALL_TIME = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const IsoWeekdaySchema = v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(7));
export const OpeningHoursStartSchema = v.pipe(v.string(), v.regex(WALL_TIME));
export const OpeningHoursEndSchema = v.union([v.pipe(v.string(), v.regex(WALL_TIME)), v.literal("24:00")]);

export const OpeningHoursEntrySchema = v.pipe(
  v.strictObject({
    dayOfWeek: IsoWeekdaySchema,
    start: OpeningHoursStartSchema,
    end: OpeningHoursEndSchema,
  }),
  v.forward(
    v.check((entry) => validOpeningHours(entry.start, entry.end)),
    ["end"],
  ),
);

/** One independently configured weekday; a missing entry means that weekday is closed. */
export type OpeningHoursEntry = v.InferOutput<typeof OpeningHoursEntrySchema>;

/** Complete replacement schedule; parsed output uses canonical weekday order. */
export const OpeningHoursSchema = v.pipe(
  v.array(OpeningHoursEntrySchema),
  v.minLength(1),
  v.maxLength(7),
  v.check((entries) => new Set(entries.map((entry) => entry.dayOfWeek)).size === entries.length),
  v.transform((entries) => entries.toSorted((left, right) => left.dayOfWeek - right.dayOfWeek)),
);

export type OpeningInterval = { start: string; end: string };

/** A localized weekday name; 2024-01-01 is a Monday. */
export function formatIsoWeekday(dayOfWeek: number, width: "long" | "short" = "long", locale = "en-US"): string {
  return new Intl.DateTimeFormat(locale, { weekday: width, timeZone: "UTC" }).format(
    new Date(Date.UTC(2024, 0, dayOfWeek)),
  );
}

/** A 24:00 end closes at the next midnight and follows any valid start. */
export function validOpeningHours(start: string, end: string): boolean {
  if (!WALL_TIME.test(start)) return false;
  if (end === "24:00") return true;
  return WALL_TIME.test(end) && start < end;
}

export function isOpenAllDay(hours: OpeningInterval | null | undefined): boolean {
  return hours?.start === "00:00" && hours.end === "24:00";
}

/** The last valid custom pair that Open all day can restore. */
export function customOpeningHours(hours: OpeningInterval): OpeningInterval | undefined {
  return validOpeningHours(hours.start, hours.end) && !isOpenAllDay(hours)
    ? { start: hours.start, end: hours.end }
    : undefined;
}
