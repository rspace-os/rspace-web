import * as React from "react";
import { useTranslation } from "react-i18next";
import {
  type AvailabilityInterval,
  type AvailabilityState,
  buildAvailabilitySegments,
} from "@/modules/booking/domain/availability";
import { cn } from "@/modules/common/utils/cn";

export type { AvailabilityInterval } from "@/modules/booking/domain/availability";

const DEFAULT_PERIOD_MILLISECONDS = 24 * 60 * 60 * 1000;

export type AvailabilityBarProps = {
  intervals: ReadonlyArray<AvailabilityInterval>;
  periodStart: Date;
  periodEnd?: Date;
  now?: Date;
  showCurrentAvailability?: boolean;
  showPeriodLabels?: boolean;
  timeZone: string;
  /** @deprecated The bar domain is now always the resolved display timezone. */
  userTimeZone?: string;
  itemName: string;
  className?: string;
};

type TimestampInterval = {
  startsAt: number;
  endsAt: number;
};

const stateClasses: Record<AvailabilityState, string> = {
  available: "bg-transparent",
  booking: "bg-blue-600",
  blockout: "bg-[repeating-linear-gradient(135deg,var(--color-amber-700)_0_3px,var(--color-amber-300)_3px_6px)]",
  overlap: "bg-[repeating-linear-gradient(135deg,var(--color-blue-600)_0_3px,var(--color-amber-400)_3px_6px)]",
};

function timestamp(date: Date, name: string): number {
  const value = date.getTime();
  if (!Number.isFinite(value)) throw new RangeError(`${name} must be a valid Date`);
  return value;
}

function supportedLocale(language: string): string {
  return Intl.DateTimeFormat.supportedLocalesOf([language]).at(0) ?? "en-US";
}

export function AvailabilityBar({
  intervals,
  periodStart,
  periodEnd,
  now,
  showCurrentAvailability = false,
  showPeriodLabels = false,
  timeZone,
  itemName,
  className,
}: AvailabilityBarProps) {
  const { t, i18n } = useTranslation("booking");
  const descriptionId = `${React.useId()}-description`;
  const start = timestamp(periodStart, "periodStart");
  const end = periodEnd === undefined ? start + DEFAULT_PERIOD_MILLISECONDS : timestamp(periodEnd, "periodEnd");
  if (end <= start) throw new RangeError("periodEnd must be after periodStart");
  const nowTimestamp = now === undefined ? null : timestamp(now, "now");
  const visibleNow = nowTimestamp;
  const nowPosition = visibleNow === null ? null : Math.max(0, Math.min(1, (visibleNow - start) / (end - start)));

  const segments = buildAvailabilitySegments(intervals, new Date(start), new Date(end));
  const locale = supportedLocale(i18n.language);
  const itemDateTimeFormatter = new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone,
    timeZoneName: "short",
  });
  const timeFormatter = new Intl.DateTimeFormat(locale, {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone,
  });
  const dateFormatter = new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "numeric",
    day: "numeric",
    timeZone,
  });
  const listFormatter = new Intl.ListFormat(locale, { style: "long", type: "conjunction" });
  const formatRange = ({ startsAt, endsAt }: TimestampInterval) =>
    `${itemDateTimeFormatter.format(startsAt)}–${itemDateTimeFormatter.format(endsAt)}`;
  const period = formatRange({ startsAt: start, endsAt: end });
  const nowLabel =
    visibleNow === null
      ? null
      : visibleNow < start
        ? t("availabilityBar.nowBeforeWindow", { time: itemDateTimeFormatter.format(visibleNow) })
        : visibleNow >= end
          ? t("availabilityBar.nowAfterWindow", { time: itemDateTimeFormatter.format(visibleNow) })
          : t("availabilityBar.now", { time: itemDateTimeFormatter.format(visibleNow) });
  const stateOrder: ReadonlyArray<AvailabilityState> = ["available", "booking", "blockout", "overlap"];
  const states =
    segments.length === 1 && segments[0].state === "available"
      ? t("availabilityBar.fullAvailable")
      : stateOrder
          .flatMap((state) => {
            const ranges = segments.filter((segment) => segment.state === state).map(formatRange);
            return ranges.length === 0
              ? []
              : [
                  t(`availabilityBar.ranges.${state}`, {
                    ranges: listFormatter.format(ranges),
                  }),
                ];
          })
          .join(" ");
  const description = [t("availabilityBar.summary", { period, states }), nowLabel].filter(Boolean).join(" ");
  const currentSegmentIndex =
    visibleNow === null
      ? -1
      : segments.findIndex(({ startsAt, endsAt }) => startsAt <= visibleNow && visibleNow < endsAt);
  const currentSegment = segments[currentSegmentIndex];
  const currentlyAvailable = currentSegment?.state === "available";
  const nextAvailabilityChange = segments
    .slice(currentSegmentIndex + 1)
    .find(({ state }) => (state === "available") !== currentlyAvailable)?.startsAt;
  const showNextAvailabilityChange =
    visibleNow !== null &&
    nextAvailabilityChange !== undefined &&
    dateFormatter.format(visibleNow) === dateFormatter.format(nextAvailabilityChange);
  const currentAvailabilityLabel =
    !showCurrentAvailability || currentSegment === undefined
      ? null
      : currentlyAvailable
        ? showNextAvailabilityChange
          ? t("availabilityBar.current.availableUntil", {
              time: timeFormatter.format(nextAvailabilityChange),
            })
          : t("availabilityBar.current.available")
        : showNextAvailabilityChange
          ? t("availabilityBar.current.notAvailableFrom", {
              time: timeFormatter.format(nextAvailabilityChange),
            })
          : t("availabilityBar.current.notAvailable");

  return (
    <div className={cn("w-full", className)}>
      {currentAvailabilityLabel !== null && (
        <p data-slot="availability-bar-current" className="mb-1 flex items-center gap-1.5 text-xs font-medium">
          <span
            aria-hidden="true"
            className={cn("size-2 rounded-full", currentlyAvailable ? "bg-emerald-600" : "bg-red-600")}
          />
          {currentAvailabilityLabel}
        </p>
      )}
      {showPeriodLabels && (
        <div aria-hidden="true" className="mb-1 flex justify-between text-[10px] tabular-nums text-muted-foreground">
          <time dateTime={new Date(start).toISOString()}>{timeFormatter.format(start)}</time>
          <time dateTime={new Date(end).toISOString()}>{timeFormatter.format(end)}</time>
        </div>
      )}
      <div
        role="img"
        aria-label={t("availabilityBar.label", { itemName })}
        aria-describedby={descriptionId}
        data-slot="availability-bar"
        className="relative min-h-4 w-full"
      >
        <div
          aria-hidden="true"
          className="absolute inset-0 flex overflow-hidden rounded-full bg-background ring-1 ring-border"
        >
          {segments.map((segment) => (
            <span
              key={`${segment.startsAt}-${segment.endsAt}-${segment.state}`}
              data-availability-state={segment.state}
              className={stateClasses[segment.state]}
              style={{
                flexBasis: `${((segment.endsAt - segment.startsAt) / (end - start)) * 100}%`,
                flexGrow: 0,
                flexShrink: 0,
              }}
            />
          ))}
        </div>
        {visibleNow !== null && nowLabel !== null && nowPosition !== null && (
          <time
            dateTime={new Date(visibleNow).toISOString()}
            title={nowLabel}
            data-slot="availability-bar-now"
            className="pointer-events-none absolute -top-1 -bottom-1 z-10 w-0.5 bg-red-600"
            style={{ left: `${nowPosition * 100}%` }}
          />
        )}
      </div>
      <span id={descriptionId} className="sr-only">
        {description}
      </span>
    </div>
  );
}
