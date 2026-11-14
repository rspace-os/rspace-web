import { Temporal } from "@js-temporal/polyfill";
import type { TFunction } from "i18next";
import { INCREMENT_UNIT_MINUTES, type IncrementSettings } from "@/modules/booking/domain/bookingIncrement";
import { formatDurationMinutes } from "@/modules/booking/domain/bookingTime";

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

/** Elapsed length for minute/hour rules; local calendar length for day rules. */
export function formatBookingLength(
  unit: IncrementSettings["incrementUnit"],
  window: { start: string; end: string },
  timezone: string,
  t: TFunction<"booking">,
  locale: string,
): string {
  const start = Temporal.Instant.from(window.start);
  const end = Temporal.Instant.from(window.end);
  const elapsed = (end.epochMilliseconds - start.epochMilliseconds) / 60_000;
  if (unit === "MINUTES") return t("bookableItemDetails.minutes", { count: elapsed });
  if (unit === "HOURS") return formatDurationMinutes(elapsed, locale, { largestUnit: "hour" });
  const localStart = start.toZonedDateTimeISO(timezone).toPlainDateTime();
  const localEnd = end.toZonedDateTimeISO(timezone).toPlainDateTime();
  return formatDurationMinutes(localStart.until(localEnd, { largestUnit: "minute" }).minutes, locale);
}

/** Label and value for the increment. */
export function incrementFacts(settings: IncrementSettings, t: TFunction<"booking">): Array<[string, string]> {
  return [[t("bookableItemDetails.fields.granularity"), formatIncrement(settings, t)]];
}
