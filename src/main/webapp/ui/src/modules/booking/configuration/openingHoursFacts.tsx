import { Temporal } from "@js-temporal/polyfill";
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { formatDayOffset } from "@/modules/booking/components/DayTimelineEvent";
import {
  ALL_ISO_WEEKDAYS,
  effectiveHours,
  formatIsoWeekday,
  formatOpeningRange,
  type OpeningSchedule,
  resolveSchedulingBoundary,
} from "@/modules/booking/domain/bookingOpeningHours";
import { bookingDateTimeLocale, bookingLocale, formatWallClockTime } from "@/modules/booking/domain/bookingTime";
import { formatList } from "@/modules/common/i18n/listFormat";

export type WeekdayOpening = { dayOfWeek: number; hours: string | null };

/**
 * Each ISO weekday's effective hours shown in `timezone`, for the week containing `referenceDate` (daylight saving
 * makes the conversion depend on the date), as `start - end`. A boundary on another date than its weekday gets a
 * `(+1)`/`(-1)` offset; an end is read as the moment before it, so a closing midnight stays on its own day.
 */
export function weeklyOpeningHours(
  schedule: OpeningSchedule & { timezone: string },
  timezone: string,
  referenceDate: string,
  locale = bookingDateTimeLocale(),
): WeekdayOpening[] {
  const reference = Temporal.PlainDate.from(referenceDate);
  const monday = reference.subtract({ days: reference.dayOfWeek - 1 });
  const allDay = ALL_ISO_WEEKDAYS.every((day) => {
    const hours = effectiveHours(schedule, day);
    return hours?.start === "00:00" && hours.end === "24:00";
  });
  return ALL_ISO_WEEKDAYS.map((dayOfWeek) => {
    const hours = effectiveHours(schedule, dayOfWeek);
    if (!hours) return { dayOfWeek, hours: null };
    // Open around the clock reads the same in every timezone.
    if (allDay)
      return { dayOfWeek, hours: `${formatWallClockTime("00:00", locale)} - ${formatWallClockTime("00:00", locale)}` };
    const date = monday.add({ days: dayOfWeek - 1 });
    const boundary = (time: string, end: boolean) => {
      const instant = resolveSchedulingBoundary(date.toString(), time, schedule.timezone);
      const zoned = instant.toZonedDateTimeISO(timezone);
      const day = (end ? instant.subtract({ nanoseconds: 1 }).toZonedDateTimeISO(timezone) : zoned).toPlainDate();
      return `${formatWallClockTime(zoned.toPlainTime().toString({ smallestUnit: "minute" }), locale)}${formatDayOffset(date.until(day).days)}`;
    };
    return { dayOfWeek, hours: `${boundary(hours.start, false)} - ${boundary(hours.end, true)}` };
  });
}

/**
 * Label/value facts for a read-out of scheduling-zone opening hours. Without exceptions: the open days and one
 * range. With exceptions: every weekday's effective range, exceptions in bold and closed days marked closed.
 */
export function openingHoursFacts(schedule: OpeningSchedule, t: TFunction<"booking">): Array<[string, ReactNode]> {
  if (schedule.openingExceptions.length === 0) {
    const openDays = ALL_ISO_WEEKDAYS.filter((day) => schedule.openDays.includes(day));
    return [
      [
        t("bookableItemDetails.fields.openOn"),
        openDays.length === ALL_ISO_WEEKDAYS.length
          ? t("bookableItemDetails.openingHours.everyDay")
          : formatList(
              openDays.map((day) => formatIsoWeekday(day)),
              bookingLocale(),
            ),
      ],
      [
        t("bookableItemDetails.fields.openingHours"),
        formatOpeningRange({ start: schedule.openingStart, end: schedule.openingEnd }),
      ],
    ];
  }
  return [
    [
      t("bookableItemDetails.fields.openingHours"),
      <dl key="opening-hours" className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        {ALL_ISO_WEEKDAYS.map((day) => {
          const hours = effectiveHours(schedule, day);
          const exception = schedule.openingExceptions.some((candidate) => candidate.dayOfWeek === day);
          return (
            <div key={day} className="contents">
              <dt className="font-normal">{formatIsoWeekday(day)}</dt>
              <dd>
                {!hours ? (
                  <span className="text-muted-foreground">{t("bookableItemDetails.openingHours.closed")}</span>
                ) : exception ? (
                  <strong>{formatOpeningRange(hours)}</strong>
                ) : (
                  formatOpeningRange(hours)
                )}
              </dd>
            </div>
          );
        })}
      </dl>,
    ],
  ];
}
