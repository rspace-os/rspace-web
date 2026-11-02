import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingSummaryAccordion } from "@/modules/booking/components/BookingSummaryAccordion";
import {
  type AvailabilitySource,
  type AvailabilityState,
  buildAvailabilitySlices,
  type SourcedAvailabilityInterval,
  timestamp,
} from "@/modules/booking/domain/availability";
import {
  bookingDateTimeFormatter,
  bookingDateTimeLocale,
  formatBookingPeriod,
  formatInstantRange,
} from "@/modules/booking/domain/bookingTime";
import { formatList } from "@/modules/common/i18n/listFormat";
import { PopoverDescription, PopoverTitle } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";
import { AvailabilitySlicePopover } from "./AvailabilitySlicePopover";
import { BookingSourceRow } from "./BookingSourceRow";

const DEFAULT_PERIOD_MILLISECONDS = 24 * 60 * 60 * 1000;

export type AvailabilityBarProps = {
  intervals: ReadonlyArray<SourcedAvailabilityInterval>;
  periodStart: Date;
  periodEnd?: Date;
  now?: Date;
  /** Show the item and actor rows when a standard booking is expanded. */
  showBookingContextDetails?: boolean;
  showCurrentAvailability?: boolean;
  showPeriodLabels?: boolean;
  timeZone: string;
  instrumentTimeZone?: string | null;
  item: {
    name: string;
    globalId: string;
    location?: { name: string; globalId: string };
  };
  className?: string;
};

type TimestampInterval = {
  startsAt: number | Date;
  endsAt: number | Date;
};

type DetailedBookingSource = AvailabilitySource & {
  booking: NonNullable<AvailabilitySource["booking"]> & { privacy: "full" };
};

const stateClasses: Record<AvailabilityState, string> = {
  available: "bg-transparent",
  booking: "bg-blue-200",
  blockout: "bg-muted bg-[repeating-linear-gradient(135deg,var(--color-muted-foreground)_0_1px,transparent_1px_6px)]",
  overlap: "bg-[repeating-linear-gradient(135deg,var(--color-blue-600)_0_3px,var(--color-amber-400)_3px_6px)]",
};

function hasValidSource(interval: SourcedAvailabilityInterval): boolean {
  const startsAt = interval.source.startsAt.getTime();
  const endsAt = interval.source.endsAt.getTime();
  return Number.isFinite(startsAt) && Number.isFinite(endsAt) && endsAt > startsAt;
}

function hasBookingDetails(source: AvailabilitySource): source is DetailedBookingSource {
  return source.booking?.privacy === "full";
}

function AvailabilityBarContent({
  intervals,
  periodStart,
  periodEnd,
  now,
  showBookingContextDetails = true,
  showCurrentAvailability = false,
  showPeriodLabels = false,
  timeZone,
  instrumentTimeZone,
  item,
  className,
}: AvailabilityBarProps) {
  const { t } = useTranslation("booking");
  const descriptionId = `${React.useId()}-description`;
  const accordionName = `${React.useId()}-bookings`;
  const start = timestamp(periodStart, "periodStart");
  const end = periodEnd === undefined ? start + DEFAULT_PERIOD_MILLISECONDS : timestamp(periodEnd, "periodEnd");
  if (end < start) throw new RangeError("periodEnd must be after periodStart");
  if (end === start)
    return <p className={cn("text-xs text-muted-foreground", className)}>{t("availabilityBar.emptyWindow")}</p>;
  const nowTimestamp = now === undefined ? null : timestamp(now, "now");
  const nowPosition = nowTimestamp === null ? null : Math.max(0, Math.min(1, (nowTimestamp - start) / (end - start)));

  const slices = buildAvailabilitySlices(intervals, new Date(start), new Date(end));
  const locale = bookingDateTimeLocale();
  const itemDateTimeFormatter = bookingDateTimeFormatter(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone,
  });
  const timeFormatter = bookingDateTimeFormatter(locale, {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone,
  });
  const dateFormatter = bookingDateTimeFormatter(locale, {
    year: "numeric",
    month: "numeric",
    day: "numeric",
    timeZone,
  });
  const formatRange = ({ startsAt, endsAt }: TimestampInterval) =>
    formatBookingPeriod(new Date(startsAt).toISOString(), new Date(endsAt).toISOString(), timeZone, locale, "H24");
  const formatTimeRange = ({ startsAt, endsAt }: TimestampInterval) =>
    formatInstantRange(timeFormatter, startsAt, endsAt);
  const period = formatRange({ startsAt: start, endsAt: end });
  const nowLabel =
    nowTimestamp === null
      ? null
      : nowTimestamp < start
        ? t("availabilityBar.nowBeforeWindow", { time: itemDateTimeFormatter.format(nowTimestamp) })
        : nowTimestamp >= end
          ? t("availabilityBar.nowAfterWindow", { time: itemDateTimeFormatter.format(nowTimestamp) })
          : t("availabilityBar.now", { time: itemDateTimeFormatter.format(nowTimestamp) });
  const stateOrder: ReadonlyArray<AvailabilityState> = ["available", "booking", "blockout", "overlap"];
  const states =
    slices.length === 1 && slices[0].state === "available"
      ? t("availabilityBar.fullAvailable")
      : stateOrder
          .flatMap((state) => {
            const ranges = [
              ...new Set(
                slices
                  .filter((slice) => slice.state === state)
                  .flatMap((slice) =>
                    state === "available" || state === "overlap" || !slice.intervals.every(hasValidSource)
                      ? [formatRange(slice)]
                      : slice.intervals
                          .toSorted((left, right) => left.source.startsAt.getTime() - right.source.startsAt.getTime())
                          .map(({ source }) => formatRange(source)),
                  ),
              ),
            ];
            if (ranges.length === 0) return [];
            const values = { ranges: formatList(ranges, locale) };
            return [
              {
                available: () => t("availabilityBar.ranges.available", values),
                booking: () => t("availabilityBar.ranges.booking", values),
                blockout: () => t("availabilityBar.ranges.blockout", values),
                overlap: () => t("availabilityBar.ranges.overlap", values),
              }[state](),
            ];
          })
          .join(" ");
  const gapRanges = slices.flatMap((slice) => {
    if (slice.state !== "booking" || !slice.intervals.every(hasValidSource)) return [];
    return buildAvailabilitySlices(
      slice.intervals.map((interval) => ({
        ...interval,
        startsAt: interval.source.startsAt,
        endsAt: interval.source.endsAt,
      })),
      new Date(slice.startsAt),
      new Date(slice.endsAt),
    )
      .filter((part) => part.state === "available")
      .map(formatRange);
  });
  const gapDescription = gapRanges.length
    ? t("availabilityBar.ranges.gap", {
        ranges: formatList(gapRanges, locale),
      })
    : "";
  const description = t("availabilityBar.description", {
    period,
    states: [states, gapDescription].filter(Boolean).join(" "),
    now: nowLabel ?? "",
    hasNow: nowLabel ? "yes" : "no",
  });
  const currentSegmentIndex =
    nowTimestamp === null
      ? -1
      : slices.findIndex(({ startsAt, endsAt }) => startsAt <= nowTimestamp && nowTimestamp < endsAt);
  const currentSegment = slices[currentSegmentIndex];
  const currentlyAvailable = currentSegment?.state === "available";
  const nextAvailabilityChange = slices
    .slice(currentSegmentIndex + 1)
    .find(({ state }) => (state === "available") !== currentlyAvailable)?.startsAt;
  const showNextAvailabilityChange =
    nowTimestamp !== null &&
    nextAvailabilityChange !== undefined &&
    dateFormatter.format(nowTimestamp) === dateFormatter.format(nextAvailabilityChange);
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
  const sliceStateLabels: Record<Exclude<AvailabilityState, "available">, string> = {
    booking: t("availabilityBar.slice.states.booking"),
    blockout: t("availabilityBar.slice.states.blockout"),
    overlap: t("availabilityBar.slice.states.overlap"),
  };

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
        <div aria-hidden="true" className="mb-1 flex justify-between text-[10px] tabular-nums text-foreground">
          <time dateTime={new Date(start).toISOString()}>{timeFormatter.format(start)}</time>
          <time dateTime={new Date(end).toISOString()}>{timeFormatter.format(end)}</time>
        </div>
      )}
      <div className="relative min-h-6 w-full">
        <div
          role="img"
          aria-label={t("availabilityBar.label", { itemName: item.name })}
          aria-describedby={descriptionId}
          data-slot="availability-bar"
          className="relative min-h-6 w-full"
        >
          <div
            aria-hidden="true"
            className="absolute inset-0 flex overflow-hidden rounded-full bg-background ring-1 ring-border"
          >
            {slices.map((slice) => (
              <span
                key={`${slice.startsAt}-${slice.endsAt}-${slice.state}`}
                data-availability-state={slice.state}
                className={cn(
                  "relative",
                  stateClasses[slice.state],
                  slice.intervals.some(({ source }) => source.booking?.kind === "MAINTENANCE") &&
                    "bg-[repeating-linear-gradient(90deg,var(--color-amber-700)_0_3px,var(--color-amber-200)_3px_6px)]",
                )}
                style={{
                  flexBasis: `${((slice.endsAt - slice.startsAt) / (end - start)) * 100}%`,
                  flexGrow: 0,
                  flexShrink: 0,
                }}
              >
                {slice.state === "booking" &&
                  slice.intervals.map(({ source }) => {
                    const from = Math.max(slice.startsAt, source.startsAt.getTime());
                    const to = Math.min(slice.endsAt, source.endsAt.getTime());
                    return to > from ? (
                      <span
                        key={source.id}
                        className="absolute inset-y-0 bg-blue-600"
                        style={{
                          left: `${((from - slice.startsAt) / (slice.endsAt - slice.startsAt)) * 100}%`,
                          width: `${((to - from) / (slice.endsAt - slice.startsAt)) * 100}%`,
                        }}
                      />
                    ) : null;
                  })}
              </span>
            ))}
          </div>
          {nowTimestamp !== null && nowLabel !== null && nowPosition !== null && (
            <time
              dateTime={new Date(nowTimestamp).toISOString()}
              title={nowLabel}
              data-slot="availability-bar-now"
              className="pointer-events-none absolute inset-y-0 z-30 w-0.5 bg-red-600"
              style={{ left: `${nowPosition * 100}%` }}
            />
          )}
        </div>
        <div className="pointer-events-none absolute inset-0 z-20 overflow-hidden rounded-full">
          {slices.map((slice) => {
            if (slice.state === "available" || !slice.intervals.every(hasValidSource)) return null;
            const contributors = slice.intervals.toSorted(
              (left, right) =>
                left.source.startsAt.getTime() - right.source.startsAt.getTime() ||
                left.source.endsAt.getTime() - right.source.endsAt.getTime() ||
                left.kind.localeCompare(right.kind) ||
                left.source.id.localeCompare(right.source.id),
            );
            const stateLabel = contributors.some(({ source }) => source.booking?.kind === "MAINTENANCE")
              ? t("bookings.maintenanceLabel")
              : sliceStateLabels[slice.state];
            const slicePeriod = formatList(
              [
                ...new Set(
                  contributors.map(({ source }) =>
                    formatBookingPeriod(
                      source.startsAt.toISOString(),
                      source.endsAt.toISOString(),
                      timeZone,
                      locale,
                      "H24",
                    ),
                  ),
                ),
              ],
              locale,
            );
            const triggerLabel = t("availabilityBar.slice.trigger", {
              itemName: item.name,
              state: stateLabel,
              period: slicePeriod,
              count: contributors.length,
            });
            return (
              <AvailabilitySlicePopover
                key={`${slice.startsAt}-${slice.endsAt}-${slice.state}`}
                className={
                  slice.startsAt === start
                    ? slice.endsAt === end
                      ? "rounded-full"
                      : "rounded-l-full"
                    : slice.endsAt === end
                      ? "rounded-r-full"
                      : "rounded-none"
                }
                label={triggerLabel}
                left={`${((slice.startsAt - start) / (end - start)) * 100}%`}
                width={`${((slice.endsAt - slice.startsAt) / (end - start)) * 100}%`}
              >
                <div className="min-w-0">
                  <PopoverTitle className="text-base font-semibold leading-tight">
                    {formatTimeRange(slice)}
                  </PopoverTitle>
                  <PopoverDescription className="mt-1 text-xs">
                    {t("availabilityBar.slice.count", {
                      count: contributors.length,
                    })}
                  </PopoverDescription>
                </div>
                <ul className="space-y-1">
                  {contributors.map(({ kind, source }) => {
                    if (hasBookingDetails(source)) {
                      const maintenance = source.booking.kind === "MAINTENANCE";
                      const actor = maintenance ? source.booking.createdBy : source.booking.bookedBy;
                      return (
                        <BookingSummaryAccordion
                          key={source.id}
                          accordionName={accordionName}
                          heading={maintenance ? t("bookings.maintenanceLabel") : item.name}
                          summaryLabel={
                            maintenance
                              ? t("bookings.maintenanceLabel")
                              : actor
                                ? t("availabilityBar.slice.bookedBy", { user: actor })
                                : t("availabilityBar.slice.sources.booking")
                          }
                          period={formatTimeRange(source)}
                          periodTooltip={{
                            start: source.startsAt.toISOString(),
                            end: source.endsAt.toISOString(),
                            displayTimeZone: timeZone,
                            instrumentTimeZone,
                          }}
                          canViewConfiguration={source.booking.canViewConfiguration}
                          purpose={source.booking.purpose}
                          maintenance={maintenance}
                          item={maintenance || showBookingContextDetails ? item : undefined}
                          actor={maintenance || showBookingContextDetails ? actor : undefined}
                          detailsBookingId={source.booking.id}
                        />
                      );
                    }
                    return (
                      <BookingSourceRow
                        key={source.id}
                        truncate={false}
                        heading={
                          source.booking?.kind === "MAINTENANCE"
                            ? t("bookings.maintenanceLabel")
                            : kind === "booking"
                              ? t("availabilityBar.slice.sources.booking")
                              : t("availabilityBar.slice.sources.openingHours")
                        }
                        period={
                          <BookingInstrumentTimeTooltip
                            start={source.startsAt.toISOString()}
                            end={source.endsAt.toISOString()}
                            displayTimeZone={timeZone}
                            instrumentTimeZone={instrumentTimeZone}
                          >
                            {formatTimeRange(source)}
                          </BookingInstrumentTimeTooltip>
                        }
                        maintenance={kind !== "booking"}
                      />
                    );
                  })}
                </ul>
              </AvailabilitySlicePopover>
            );
          })}
        </div>
      </div>
      <span id={descriptionId} className="sr-only">
        {description}
      </span>
    </div>
  );
}

export const AvailabilityBar = React.memo(AvailabilityBarContent, (previous, next) => {
  const normalize = (props: AvailabilityBarProps) =>
    ({
      intervals: props.intervals,
      showBookingContextDetails: props.showBookingContextDetails,
      showCurrentAvailability: props.showCurrentAvailability,
      showPeriodLabels: props.showPeriodLabels,
      timeZone: props.timeZone,
      instrumentTimeZone: props.instrumentTimeZone,
      className: props.className,
      periodStart: props.periodStart.getTime(),
      periodEnd: props.periodEnd?.getTime(),
      now: props.now?.getTime(),
      itemName: props.item.name,
      itemId: props.item.globalId,
      locationName: props.item.location?.name,
      locationId: props.item.location?.globalId,
    }) satisfies Record<
      Exclude<keyof AvailabilityBarProps, "item"> | "itemName" | "itemId" | "locationName" | "locationId",
      unknown
    >;
  const left = normalize(previous);
  const right = normalize(next);
  return Object.entries(left).every(([key, value]) => value === right[key as keyof typeof right]);
});
