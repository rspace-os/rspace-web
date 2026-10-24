import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import {
  ALL_ISO_WEEKDAYS,
  formatIsoWeekday,
  isOpenAllDay,
  type OpeningHoursEntry,
} from "@/modules/booking/domain/bookingOpeningHours";
import i18n from "@/modules/common/i18n";

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
                    `${hours.start}–${hours.end}`
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
