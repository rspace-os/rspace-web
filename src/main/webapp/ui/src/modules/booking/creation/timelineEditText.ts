import type { TFunction } from "i18next";
import { addCalendarDays, formatPlainDate } from "@/modules/booking/domain/bookingTime";
import type { HiddenEdgeAction, TimelineAdjuster, TimelinePolicy } from "./timelineRange";

/** The label of a hidden endpoint's midnight action, naming the displayed day. */
export function hiddenEdgeLabel(edge: "start" | "end", date: string, t: TFunction<"booking">, locale?: string): string {
  const formattedDate = formatPlainDate(date, locale);
  return edge === "start"
    ? t("timelineEdit.startAtMidnight", { date: formattedDate })
    : t("timelineEdit.endAtMidnight", { date: formattedDate });
}

/** Why a hidden endpoint cannot snap to midnight, or the instruction when it can. */
export function hiddenEdgeDescription(
  edge: "start" | "end",
  action: HiddenEdgeAction,
  date: string,
  displayTimezone: string,
  t: TFunction<"booking">,
  locale?: string,
): string {
  if (action.kind === "enabled") return t("timelineEdit.continuationHint");
  const midnightDate = formatPlainDate(edge === "start" ? date : addCalendarDays(date, 1), locale);
  return {
    alignment: t("timelineEdit.unavailable.alignment", { date: midnightDate, timezone: displayTimezone }),
    dayEndMissing: t("timelineEdit.unavailable.dayEndMissing", { date: midnightDate, timezone: displayTimezone }),
    duration: t("timelineEdit.unavailable.duration", { date: midnightDate, timezone: displayTimezone }),
    precision: t("timelineEdit.unavailable.precision", { date: midnightDate, timezone: displayTimezone }),
    order: t("timelineEdit.unavailable.order", { date: midnightDate, timezone: displayTimezone }),
    maximum: t("timelineEdit.unavailable.maximum", { date: midnightDate, timezone: displayTimezone }),
    openingHours: t("timelineEdit.unavailable.openingHours", { date: midnightDate, timezone: displayTimezone }),
    midnightSkipped: t("timelineEdit.unavailable.midnightSkipped", { date: midnightDate, timezone: displayTimezone }),
    midnightRepeated: t("timelineEdit.unavailable.midnightRepeated", { date: midnightDate, timezone: displayTimezone }),
  }[action.reason];
}

/** Why a visible endpoint cannot be dragged, or undefined when it can. */
export function resizeDisabledReason(
  edge: "start" | "end",
  adjuster: TimelineAdjuster,
  policy: TimelinePolicy,
  t: TFunction<"booking">,
): string | undefined {
  if (adjuster.canResize(edge)) return undefined;
  if (policy.incrementUnit === "DAYS") return t("timelineEdit.resizeDays");
  return edge === "start" ? t("timelineEdit.resizeStart") : t("timelineEdit.resizeEnd");
}
