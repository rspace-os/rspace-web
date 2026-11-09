import { Temporal } from "@js-temporal/polyfill";
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import {
  ALL_ISO_WEEKDAYS,
  effectiveHours,
  formatIsoWeekday,
  formatOpeningRange,
  isOpenAllDay,
  type OpeningInterval,
  type OpeningSchedule,
  resolveSchedulingBoundary,
} from "@/modules/booking/domain/bookingOpeningHours";
import {
  bookingDateTimeLocale,
  bookingLocale,
  formatTimeWithDayOffset,
  formatWallClockTime,
  sameTimeZone,
} from "@/modules/booking/domain/bookingTime";
import i18n from "@/modules/common/i18n";

export type WeekdayOpening = { dayOfWeek: number; hours: string | null };

/**
 * Each ISO weekday's effective hours shown in `timezone`, for the week containing `referenceDate` (daylight saving
 * makes the conversion depend on the date), as `start - end`. A boundary on another date than its weekday gets a
 * `(+1)`/`(-1)` offset; an end is read as the moment before it, so a closing midnight stays on its own day. With
 * `allDayLabel`, a day open from midnight to midnight reads as that label wherever it is still a whole local day.
 */
export function weeklyOpeningHours(
  schedule: OpeningSchedule & { timezone: string },
  timezone: string,
  referenceDate: string,
  locale = bookingDateTimeLocale(),
  allDayLabel?: string,
): WeekdayOpening[] {
  const reference = Temporal.PlainDate.from(referenceDate);
  const monday = reference.subtract({ days: reference.dayOfWeek - 1 });
  const allWeek = ALL_ISO_WEEKDAYS.every((day) => isOpenAllDay(effectiveHours(schedule, day)));
  const sameZone = sameTimeZone(schedule.timezone, timezone);
  return ALL_ISO_WEEKDAYS.map((dayOfWeek) => {
    const hours = effectiveHours(schedule, dayOfWeek);
    if (!hours) return { dayOfWeek, hours: null };
    if (allDayLabel !== undefined && isOpenAllDay(hours) && (allWeek || sameZone)) {
      return { dayOfWeek, hours: allDayLabel };
    }
    // Open around the clock reads the same in every timezone.
    if (allWeek)
      return {
        dayOfWeek,
        hours: i18n.t("booking:time.openingRange", {
          lng: bookingLocale(),
          start: formatWallClockTime("00:00", locale),
          end: formatWallClockTime("00:00", locale),
        }),
      };
    const date = monday.add({ days: dayOfWeek - 1 });
    const boundary = (wallTime: string, end: boolean) => {
      const instant = resolveSchedulingBoundary(date.toString(), wallTime, schedule.timezone);
      const zoned = instant.toZonedDateTimeISO(timezone);
      const day = (end ? instant.subtract({ nanoseconds: 1 }).toZonedDateTimeISO(timezone) : zoned).toPlainDate();
      const time = formatWallClockTime(zoned.toPlainTime().toString({ smallestUnit: "minute" }), locale);
      const days = date.until(day).days;
      return formatTimeWithDayOffset(time, days, locale);
    };
    return {
      dayOfWeek,
      hours: i18n.t("booking:time.openingRange", {
        lng: bookingLocale(),
        start: boundary(hours.start, false),
        end: boundary(hours.end, true),
      }),
    };
  });
}

/** Each weekday's independently configured hours in the scheduling timezone. */
export function openingHoursFacts(
  schedule: OpeningSchedule,
  t: TFunction<"booking">,
  timeLocale = bookingDateTimeLocale(),
): Array<[string, ReactNode]> {
  const format = (hours: OpeningInterval) =>
    isOpenAllDay(hours) ? t("settings.openingHours.openAllDay") : formatOpeningRange(hours, timeLocale);
  return [
    [
      t("bookableItemDetails.fields.openingHours"),
      <dl key="opening-hours" className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        {ALL_ISO_WEEKDAYS.map((day) => {
          const hours = effectiveHours(schedule, day);
          return (
            <div key={day} className="contents">
              <dt className="font-normal">{formatIsoWeekday(day)}</dt>
              <dd>
                {hours ? (
                  format(hours)
                ) : (
                  <span className="text-muted-foreground">{t("bookableItemDetails.openingHours.closed")}</span>
                )}
              </dd>
            </div>
          );
        })}
      </dl>,
    ],
  ];
}
