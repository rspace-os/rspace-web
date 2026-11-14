import { Temporal } from "@js-temporal/polyfill";
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { formatDayOffset } from "@/modules/booking/components/DayTimelineEvent";
import {
  ALL_ISO_WEEKDAYS,
  effectiveHours,
  formatIsoWeekday,
  formatOpeningRange,
  isOpenAllDay,
  type OpeningHoursEntry,
  type OpeningSchedule,
  resolveSchedulingBoundary,
} from "@/modules/booking/domain/bookingOpeningHours";
import { formatWallClockTime } from "@/modules/booking/domain/bookingTime";
import i18n from "@/modules/common/i18n";

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
    if (allDay) return { dayOfWeek, hours: `${formatWallClockTime("00:00")} - ${formatWallClockTime("00:00")}` };
    const date = monday.add({ days: dayOfWeek - 1 });
    const boundary = (time: string, end: boolean) => {
      const instant = resolveSchedulingBoundary(date.toString(), time, schedule.timezone);
      const zoned = instant.toZonedDateTimeISO(timezone);
      const day = (end ? instant.subtract({ nanoseconds: 1 }).toZonedDateTimeISO(timezone) : zoned).toPlainDate();
      return `${formatWallClockTime(zoned.toPlainTime().toString({ smallestUnit: "minute" }))}${formatDayOffset(date.until(day).days)}`;
    };
    return { dayOfWeek, hours: `${boundary(hours.start, false)} - ${boundary(hours.end, true)}` };
  });
}

/** Each weekday's independent opening hours in the item's scheduling timezone. */
export function openingHoursFacts(
  schedule: { openingHours: readonly OpeningHoursEntry[] },
  t: TFunction<"booking">,
): Array<[string, ReactNode]> {
  const locale = i18n.language === "cimode" ? "en-US" : i18n.language;
  return [
    [
      t("bookableItemDetails.fields.openingHours"),
      <dl key="opening-hours" className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        {ALL_ISO_WEEKDAYS.map((dayOfWeek) => {
          const hours = schedule.openingHours.find((entry) => entry.dayOfWeek === dayOfWeek);
          return (
            <div key={dayOfWeek} className="contents">
              <dt className="font-normal">{formatIsoWeekday(dayOfWeek, "long", locale)}</dt>
              <dd>
                {hours ? (
                  isOpenAllDay(hours) ? (
                    t("settings.openingHours.openAllDay")
                  ) : (
                    formatOpeningRange(hours)
                  )
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
