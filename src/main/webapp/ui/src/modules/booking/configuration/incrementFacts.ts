import type { TFunction } from "i18next";
import {
  alignmentAvailable,
  INCREMENT_UNIT_MINUTES,
  type IncrementSettings,
  type IncrementUnit,
} from "@/modules/booking/domain/bookingIncrement";

const INCREMENT_COUNT_KEYS = {
  MINUTES: "bookableItemDetails.minutes",
  HOURS: "bookableItemDetails.hours",
  DAYS: "bookableItemDetails.days",
} as const satisfies Record<IncrementUnit, string>;

/** The increment in its stored unit: "90 minutes", "1.5 hours", "24 hours" or "1 day", never re-derived. */
export function formatIncrement(
  settings: Pick<IncrementSettings, "slotGranularityMinutes" | "incrementUnit">,
  t: TFunction<"booking">,
): string {
  return t(INCREMENT_COUNT_KEYS[settings.incrementUnit], {
    count: settings.slotGranularityMinutes / INCREMENT_UNIT_MINUTES[settings.incrementUnit],
  });
}

/** Label/value facts for a read-out of the increment and whether starts follow its clock intervals. */
export function incrementFacts(settings: IncrementSettings, t: TFunction<"booking">): Array<[string, string]> {
  return [
    [t("bookableItemDetails.fields.granularity"), formatIncrement(settings, t)],
    [
      t("bookableItemDetails.fields.alignToIncrement"),
      !alignmentAvailable(settings)
        ? t("bookableItemDetails.alignmentUnavailable")
        : t(settings.alignToIncrement ? "bookableItemDetails.yes" : "bookableItemDetails.no"),
    ],
  ];
}
