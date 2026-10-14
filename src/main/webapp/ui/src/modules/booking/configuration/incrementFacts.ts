import type { TFunction } from "i18next";
import {
  alignmentAvailable,
  INCREMENT_UNIT_MINUTES,
  type IncrementSettings,
} from "@/modules/booking/domain/bookingIncrement";

/** The increment in its stored unit: "90 minutes", "1.5 hours", "24 hours" or "1 day", never re-derived. */
export function formatIncrement(
  settings: Pick<IncrementSettings, "slotGranularityMinutes" | "incrementUnit">,
  t: TFunction<"booking">,
): string {
  const count = settings.slotGranularityMinutes / INCREMENT_UNIT_MINUTES[settings.incrementUnit];
  return {
    MINUTES: t("bookableItemDetails.minutes", { count }),
    HOURS: t("bookableItemDetails.hours", { count }),
    DAYS: t("bookableItemDetails.days", { count }),
  }[settings.incrementUnit];
}

/** Label/value facts for a read-out of the increment and whether starts follow its clock intervals. */
export function incrementFacts(settings: IncrementSettings, t: TFunction<"booking">): Array<[string, string]> {
  return [
    [t("bookableItemDetails.fields.granularity"), formatIncrement(settings, t)],
    [
      t("bookableItemDetails.fields.alignToIncrement"),
      !alignmentAvailable(settings)
        ? t("bookableItemDetails.alignmentUnavailable")
        : settings.alignToIncrement
          ? t("bookableItemDetails.yes")
          : t("bookableItemDetails.no"),
    ],
  ];
}
