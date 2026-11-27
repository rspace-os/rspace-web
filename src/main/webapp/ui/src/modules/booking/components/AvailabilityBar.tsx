import { Temporal } from "@js-temporal/polyfill";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingSummaryAccordion } from "@/modules/booking/components/BookingSummaryAccordion";
import {
  type AvailabilitySource,
  type AvailabilityState,
  buildAvailabilitySlices,
  type SourcedAvailabilityInterval,
} from "@/modules/booking/domain/availability";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  bookingDateTimeFormatter,
  bookingDisplayLocale,
  bookingHourCycle,
  bookingLocale,
  formatBookingPeriod,
  formatInstantRange,
} from "@/modules/booking/domain/bookingTime";
import { formatList } from "@/modules/common/i18n/listFormat";
import { instantToWire } from "@/modules/common/time/instant";
import { PopoverDescription, PopoverTitle } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";
import { AvailabilitySlicePopover } from "./AvailabilitySlicePopover";
import { BookingSourceRow } from "./BookingSourceRow";

export type AvailabilityBarProps = {
  intervals: ReadonlyArray<SourcedAvailabilityInterval>;
  periodStart: Temporal.Instant;
  /** Defaults to 24 hours after `periodStart`. */
  periodEnd?: Temporal.Instant;
  now?: Temporal.Instant;
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
  startsAt: number | Temporal.Instant;
  endsAt: number | Temporal.Instant;
};

const epochMilliseconds = (value: number | Temporal.Instant) =>
  typeof value === "number" ? value : value.epochMilliseconds;
const wireInstant = (value: number | Temporal.Instant) =>
  instantToWire(typeof value === "number" ? Temporal.Instant.fromEpochMilliseconds(value) : value);

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
  return Temporal.Instant.compare(interval.source.endsAt, interval.source.startsAt) > 0;
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
  const timeFormat = useBookingTimeFormat();
  const descriptionId = `${React.useId()}-description`;
  const accordionName = `${React.useId()}-bookings`;
  const periodEndInstant = periodEnd ?? periodStart.add({ hours: 24 });
  const start = periodStart.epochMilliseconds;
  const end = periodEndInstant.epochMilliseconds;
  if (end < start) throw new RangeError("periodEnd must be after periodStart");
  if (end === start)
    return <p className={cn("text-xs text-muted-foreground", className)}>{t("availabilityBar.emptyWindow")}</p>;
  const nowTimestamp = now === undefined ? null : now.epochMilliseconds;
  const nowPosition = nowTimestamp === null ? null : Math.max(0, Math.min(1, (nowTimestamp - start) / (end - start)));

  const slices = buildAvailabilitySlices(intervals, periodStart, periodEndInstant);
  const locale = bookingDisplayLocale(timeFormat);
  const listLocale = bookingLocale();
  const hourCycle = bookingHourCycle(timeFormat);
  const itemDateTimeFormatter = bookingDateTimeFormatter(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle,
    timeZone,
  });
  const timeFormatter = bookingDateTimeFormatter(locale, {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle,
    timeZone,
  });
  const dateFormatter = bookingDateTimeFormatter(locale, {
    year: "numeric",
    month: "numeric",
    day: "numeric",
    timeZone,
  });
  const formatRange = ({ startsAt, endsAt }: TimestampInterval) =>
    formatBookingPeriod(wireInstant(startsAt), wireInstant(endsAt), timeZone, locale, timeFormat);
  const formatTimeRange = ({ startsAt, endsAt }: TimestampInterval) =>
    formatInstantRange(timeFormatter, epochMilliseconds(startsAt), epochMilliseconds(endsAt));
  const period = formatRange({ startsAt: start, endsAt: end });
  const nowLabel =
    nowTimestamp === null
      ? null
      : nowTimestamp < start
        ? t("availabilityBar.nowBeforeWindow", { time: itemDateTimeFormatter.format(nowTimestamp) })
        : nowTimestamp >= end
          ? t("availabilityBar.nowAfterWindow", { time: itemDateTimeFormatter.format(nowTimestamp) })
          : t("availabilityBar.now", { time: itemDateTimeFormatter.format(nowTimestamp) });
  const stateRanges = (state: AvailabilityState) => {
    const ranges = [
      ...new Set(
        slices
          .filter((slice) => slice.state === state)
          .flatMap((slice) =>
            state === "available" || state === "overlap" || !slice.intervals.every(hasValidSource)
              ? [formatRange(slice)]
              : slice.intervals
                  .toSorted((left, right) => Temporal.Instant.compare(left.source.startsAt, right.source.startsAt))
                  .map(({ source }) => formatRange(source)),
          ),
      ),
    ];
    // "none" selects no sentence for this state in the ICU description.
    return ranges.length === 0 ? "none" : formatList(ranges, listLocale);
  };
  const fullyAvailable = slices.length === 1 && slices[0].state === "available";
  const gapRanges = slices.flatMap((slice) => {
    if (slice.state !== "booking" || !slice.intervals.every(hasValidSource)) return [];
    return buildAvailabilitySlices(
      slice.intervals.map((interval) => ({
        ...interval,
        startsAt: interval.source.startsAt,
        endsAt: interval.source.endsAt,
      })),
      Temporal.Instant.fromEpochMilliseconds(slice.startsAt),
      Temporal.Instant.fromEpochMilliseconds(slice.endsAt),
    )
      .filter((part) => part.state === "available")
      .map(formatRange);
  });
  const description = t("availabilityBar.description", {
    period,
    full: fullyAvailable ? "yes" : "no",
    available: fullyAvailable ? "none" : stateRanges("available"),
    booking: fullyAvailable ? "none" : stateRanges("booking"),
    blockout: fullyAvailable ? "none" : stateRanges("blockout"),
    overlap: fullyAvailable ? "none" : stateRanges("overlap"),
    gap: gapRanges.length ? formatList(gapRanges, listLocale) : "none",
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
          <time dateTime={wireInstant(start)}>{timeFormatter.format(start)}</time>
          <time dateTime={wireInstant(end)}>{timeFormatter.format(end)}</time>
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
                    const from = Math.max(slice.startsAt, source.startsAt.epochMilliseconds);
                    const to = Math.min(slice.endsAt, source.endsAt.epochMilliseconds);
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
              dateTime={wireInstant(nowTimestamp)}
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
                Temporal.Instant.compare(left.source.startsAt, right.source.startsAt) ||
                Temporal.Instant.compare(left.source.endsAt, right.source.endsAt) ||
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
                      instantToWire(source.startsAt),
                      instantToWire(source.endsAt),
                      timeZone,
                      locale,
                      timeFormat,
                    ),
                  ),
                ),
              ],
              listLocale,
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
                            start: instantToWire(source.startsAt),
                            end: instantToWire(source.endsAt),
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
                            start={instantToWire(source.startsAt)}
                            end={instantToWire(source.endsAt)}
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
      periodStart: props.periodStart.epochMilliseconds,
      periodEnd: props.periodEnd?.epochMilliseconds,
      now: props.now?.epochMilliseconds,
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
