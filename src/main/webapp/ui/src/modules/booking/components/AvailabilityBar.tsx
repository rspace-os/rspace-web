import { CalendarClockIcon, ChevronRightIcon, WrenchIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import {
  type AvailabilityInterval,
  type AvailabilitySource,
  type AvailabilityState,
  buildAvailabilitySlices,
} from "@/modules/booking/domain/availability";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle, PopoverTrigger } from "@/modules/common/ui/popover";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { cn } from "@/modules/common/utils/cn";

export type { AvailabilityInterval } from "@/modules/booking/domain/availability";

const DEFAULT_PERIOD_MILLISECONDS = 24 * 60 * 60 * 1000;

type AvailabilityBarInterval = AvailabilityInterval & {
  source?: AvailabilitySource;
};

export type AvailabilityBarProps = {
  intervals: ReadonlyArray<AvailabilityBarInterval>;
  periodStart: Date;
  periodEnd?: Date;
  now?: Date;
  showCurrentAvailability?: boolean;
  showPeriodLabels?: boolean;
  timeZone: string;
  /** @deprecated The bar domain is now always the resolved display timezone. */
  userTimeZone?: string;
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

type SourcedAvailabilityBarInterval = AvailabilityBarInterval & {
  source: AvailabilitySource;
};

type DetailedBookingSource = AvailabilitySource & {
  booking: NonNullable<AvailabilitySource["booking"]> & { privacy: "full" };
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

function hasValidSource(interval: AvailabilityBarInterval): interval is SourcedAvailabilityBarInterval {
  if (interval.source === undefined) return false;
  const startsAt = interval.source.startsAt.getTime();
  const endsAt = interval.source.endsAt.getTime();
  return Number.isFinite(startsAt) && Number.isFinite(endsAt) && endsAt > startsAt;
}

function hasBookingDetails(source: AvailabilitySource): source is DetailedBookingSource {
  return source.booking?.privacy === "full";
}

function SlicePopover({
  children,
  className,
  label,
  left,
  width,
}: {
  children: React.ReactNode;
  className: string;
  label: string;
  left: string;
  width: string;
}) {
  const [open, setOpen] = React.useState(false);
  const suppressRestoredFocusRef = React.useRef(false);
  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen, eventDetails) => {
        if (!nextOpen && eventDetails.reason === "escape-key") suppressRestoredFocusRef.current = true;
        setOpen(nextOpen);
      }}
    >
      <PopoverTrigger
        type="button"
        openOnHover
        delay={0}
        closeDelay={200}
        aria-label={label}
        onFocus={() => {
          if (suppressRestoredFocusRef.current) {
            suppressRestoredFocusRef.current = false;
            return;
          }
          setOpen(true);
        }}
        className={cn(
          "pointer-events-auto absolute inset-y-0 cursor-help border-2 border-transparent bg-transparent p-0 outline-none data-popup-open:border-primary focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50",
          className,
        )}
        style={{ left, width }}
      />
      {children}
    </Popover>
  );
}

function BookingSourceAccordion({
  accordionName,
  item,
  period,
  source,
}: {
  accordionName: string;
  item: AvailabilityBarProps["item"];
  period: string;
  source: DetailedBookingSource;
}) {
  const { t } = useTranslation("booking");
  const maintenance = source.booking.kind === "MAINTENANCE";
  const actor = maintenance ? source.booking.createdBy : source.booking.bookedBy;

  return (
    <li className="min-w-0 overflow-hidden rounded-sm border bg-background">
      <details name={accordionName} className="group">
        <summary
          aria-label={t("dayTimeline.event.showDetails", {
            title: maintenance ? t("bookings.maintenanceLabel") : item.name,
            period,
          })}
          className="flex min-w-0 cursor-pointer list-none items-center gap-2 px-2 py-1.5 outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50 [&::-webkit-details-marker]:hidden"
        >
          <span
            className={cn(
              "flex size-7 shrink-0 items-center justify-center rounded-sm",
              maintenance
                ? "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200"
                : "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200",
            )}
          >
            {maintenance ? (
              <WrenchIcon className="size-4" aria-hidden="true" />
            ) : (
              <CalendarClockIcon className="size-4" aria-hidden="true" />
            )}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-xs font-medium">
              {maintenance ? t("bookings.maintenanceLabel") : t("availabilityBar.slice.sources.booking")}
            </span>
            <span className="block text-[11px] text-muted-foreground">{period}</span>
          </span>
          <ChevronRightIcon
            className="size-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-90"
            aria-hidden="true"
          />
        </summary>
        <div className="border-border border-t">
          <dl className="divide-y divide-border px-2 text-sm">
            <div className="py-2">
              <dt className="sr-only">{t("dayTimeline.expanded.item")}</dt>
              <dd>
                <InventoryItem
                  name={item.name}
                  globalId={item.globalId}
                  href={`/globalId/${item.globalId}`}
                  idLinkLabel={t("dayTimeline.expanded.openItem", { globalId: item.globalId })}
                  idPlacement="title"
                  className="p-0"
                >
                  {item.location ? (
                    <InventoryLocationLink name={item.location.name} globalId={item.location.globalId} />
                  ) : null}
                </InventoryItem>
              </dd>
            </div>
            {actor ? (
              <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
                <dt className="text-xs text-muted-foreground">
                  {t(maintenance ? "dayTimeline.expanded.createdBy" : "dayTimeline.expanded.bookedBy")}
                </dt>
                <dd className="min-w-0">
                  <UserBadge name={actor} />
                </dd>
              </div>
            ) : null}
            <div className="grid grid-cols-[4.5rem_1fr] gap-2 py-2">
              <dt className="text-xs text-muted-foreground">
                {t(maintenance ? "dayTimeline.expanded.notes" : "dayTimeline.expanded.purpose")}
              </dt>
              <dd className="text-xs leading-4">{source.booking.purpose ?? t("bookings.details.noneProvided")}</dd>
            </div>
          </dl>
        </div>
      </details>
    </li>
  );
}

export function AvailabilityBar({
  intervals,
  periodStart,
  periodEnd,
  now,
  showCurrentAvailability = false,
  showPeriodLabels = false,
  timeZone,
  item,
  className,
}: AvailabilityBarProps) {
  const { t, i18n } = useTranslation("booking");
  const descriptionId = `${React.useId()}-description`;
  const accordionName = `${React.useId()}-bookings`;
  const start = timestamp(periodStart, "periodStart");
  const end = periodEnd === undefined ? start + DEFAULT_PERIOD_MILLISECONDS : timestamp(periodEnd, "periodEnd");
  if (end < start) throw new RangeError("periodEnd must be after periodStart");
  if (end === start)
    return <p className={cn("text-xs text-muted-foreground", className)}>{t("availabilityBar.emptyWindow")}</p>;
  const nowTimestamp = now === undefined ? null : timestamp(now, "now");
  const visibleNow = nowTimestamp;
  const nowPosition = visibleNow === null ? null : Math.max(0, Math.min(1, (visibleNow - start) / (end - start)));

  const slices = buildAvailabilitySlices(intervals, new Date(start), new Date(end));
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
  const formatTimeRange = ({ startsAt, endsAt }: TimestampInterval) =>
    `${timeFormatter.format(startsAt)}–${timeFormatter.format(endsAt)}`;
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
    slices.length === 1 && slices[0].state === "available"
      ? t("availabilityBar.fullAvailable")
      : stateOrder
          .flatMap((state) => {
            const ranges = slices.filter((slice) => slice.state === state).map(formatRange);
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
      : slices.findIndex(({ startsAt, endsAt }) => startsAt <= visibleNow && visibleNow < endsAt);
  const currentSegment = slices[currentSegmentIndex];
  const currentlyAvailable = currentSegment?.state === "available";
  const nextAvailabilityChange = slices
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
                className={stateClasses[slice.state]}
                style={{
                  flexBasis: `${((slice.endsAt - slice.startsAt) / (end - start)) * 100}%`,
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
            const stateLabel = sliceStateLabels[slice.state];
            const slicePeriod = formatRange(slice);
            const triggerLabel = t("availabilityBar.slice.trigger", {
              itemName: item.name,
              state: stateLabel,
              period: slicePeriod,
              count: contributors.length,
            });
            return (
              <SlicePopover
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
                <PopoverContent
                  side="bottom"
                  align="start"
                  sideOffset={8}
                  collisionPadding={8}
                  sticky
                  showArrow
                  className="max-h-[min(32rem,var(--available-height))] w-80 gap-3 overflow-y-auto overscroll-contain rounded-sm p-3"
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
                    {contributors.map(({ kind, source }) =>
                      kind === "booking" && hasBookingDetails(source) ? (
                        <BookingSourceAccordion
                          key={source.id}
                          accordionName={accordionName}
                          item={item}
                          period={formatTimeRange(source)}
                          source={source}
                        />
                      ) : (
                        <li
                          key={source.id}
                          className="flex min-w-0 items-center gap-2 rounded-sm border bg-background px-2 py-1.5"
                        >
                          <span
                            className={cn(
                              "flex size-7 shrink-0 items-center justify-center rounded-sm",
                              kind === "booking"
                                ? "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200"
                                : "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200",
                            )}
                          >
                            {kind === "booking" ? (
                              <CalendarClockIcon className="size-4" aria-hidden="true" />
                            ) : (
                              <WrenchIcon className="size-4" aria-hidden="true" />
                            )}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block text-xs font-medium">
                              {kind === "booking"
                                ? t("availabilityBar.slice.sources.booking")
                                : t("availabilityBar.slice.sources.openingHours")}
                            </span>
                            <span className="block text-[11px] text-muted-foreground">{formatTimeRange(source)}</span>
                          </span>
                        </li>
                      ),
                    )}
                  </ul>
                </PopoverContent>
              </SlicePopover>
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
