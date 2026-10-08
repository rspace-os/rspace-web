import { Minus, Plus } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { dayMinuteToZonedTime, wallClockToDayMinute, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import {
  type DayTimelineEvent,
  type DayTimelineRange,
  type DayTimelineViewState,
  formatMinuteWithDayOffset,
} from "./DayTimelineEvent";
import { DayTimelineEventCard } from "./DayTimelineEventCard";
import type { useDayTimelineScrollSync } from "./useDayTimelineScrollSync";

export type { DayTimelineEvent, DayTimelineItem, DayTimelineRange, DayTimelineViewState } from "./DayTimelineEvent";
export { DayTimelineEventCard };

const MINIMUM_VISUAL_DURATION = 15;
const BASE_HOUR_WIDTH = 80;
const DENSITY_WIDTH_STEP = 12;
const EVENTS_PER_BASE_HOUR = 4;
const EVENT_CARD_HEIGHT = 72;
const EVENT_LANE_GAP = 8;
const EVENT_LANE_PITCH = EVENT_CARD_HEIGHT + EVENT_LANE_GAP;

type PositionedEvent = {
  event: DayTimelineEvent;
  startMinute: number;
  endMinute: number;
  visualEndMinute: number;
  lane: number;
};

function positionEvents(events: ReadonlyArray<DayTimelineEvent>, dayMinutes: number): Array<PositionedEvent> {
  const laneEnds: Array<number> = [];
  return events
    .filter((event) => event.endMinute > 0 && event.startMinute < dayMinutes && event.endMinute > event.startMinute)
    .toSorted(
      (left, right) =>
        left.startMinute - right.startMinute || left.endMinute - right.endMinute || left.id.localeCompare(right.id),
    )
    .map((event) => {
      const startMinute = Math.max(0, event.startMinute);
      const endMinute = Math.min(dayMinutes, event.endMinute);
      const visualEndMinute = Math.min(dayMinutes, Math.max(endMinute, startMinute + MINIMUM_VISUAL_DURATION));
      const availableLane = laneEnds.findIndex((laneEnd) => laneEnd <= startMinute);
      const lane = availableLane === -1 ? laneEnds.length : availableLane;
      laneEnds[lane] = visualEndMinute;
      return { event, startMinute, endMinute, visualEndMinute, lane };
    });
}

function peakHourlyDensity(events: ReadonlyArray<PositionedEvent>, dayMinutes: number) {
  return Math.max(
    0,
    ...Array.from(
      { length: Math.ceil(dayMinutes / 60) },
      (_, hour) =>
        events.filter(({ startMinute, endMinute }) => startMinute < (hour + 1) * 60 && endMinute > hour * 60).length,
    ),
  );
}

function NowMarker({
  minute,
  edge,
  label,
  dayMinutes,
  dateTime,
}: {
  minute: number;
  dayMinutes: number;
  dateTime: string;
  edge: "inside" | "before" | "after";
  label: string;
}) {
  const pinned = edge !== "inside";
  return (
    <div
      className={cn(
        "pointer-events-none absolute top-8 bottom-0 z-30 w-0.5 bg-red-600",
        pinned && edge === "before" && "left-0",
        pinned && edge === "after" && "right-0",
      )}
      style={pinned ? undefined : { left: `${(minute / dayMinutes) * 100}%` }}
      data-testid="day-timeline-now"
      data-edge={edge}
    >
      <time
        dateTime={dateTime}
        aria-current="time"
        className={cn(
          "absolute bottom-full left-1/2 mb-1 -translate-x-1/2 px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap text-red-700",
          edge === "before" && "left-2 translate-x-0",
          edge === "after" && "right-2 left-auto translate-x-0",
        )}
      >
        {label}
      </time>
    </div>
  );
}

export function DayTimeline({
  date,
  timezone,
  events,
  startWindow: wallStartWindow,
  endWindow: wallEndWindow,
  nowMinute,
  viewState,
  onViewStateChange,
  scrollSync,
  showZoomControls = true,
  showScrollbar = true,
  hourWidth,
  compactCards = true,
  variant = "detail",
  itemName,
  expandedCardClassName,
  renderEventActions,
  renderBlockoutActions,
  snapIncrementMinutes = 5,
  creationDisabled = false,
  onRangeSelect,
}: {
  date: string;
  timezone: string;
  events: ReadonlyArray<DayTimelineEvent>;
  startWindow: number;
  endWindow: number;
  nowMinute?: number;
  viewState?: DayTimelineViewState;
  onViewStateChange?: (viewState: DayTimelineViewState) => void;
  scrollSync?: ReturnType<typeof useDayTimelineScrollSync>;
  showZoomControls?: boolean;
  showScrollbar?: boolean;
  hourWidth?: number;
  compactCards?: boolean;
  variant?: "detail" | "table-row";
  itemName?: string;
  expandedCardClassName?: string | ((event: DayTimelineEvent) => string | undefined);
  renderEventActions?: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
  renderBlockoutActions?: (event: Extract<DayTimelineEvent, { kind: "blockout" }>, period: string) => React.ReactNode;
  snapIncrementMinutes?: number;
  creationDisabled?: boolean;
  onRangeSelect?: (range: DayTimelineRange, trigger: HTMLElement) => void;
}) {
  const dayMinutes = React.useMemo(() => zonedDayBounds(date, timezone).elapsedMinutes, [date, timezone]);
  const startWindow = React.useMemo(
    () => wallClockToDayMinute(date, timezone, wallStartWindow),
    [date, timezone, wallStartWindow],
  );
  const endWindow = React.useMemo(
    () => wallClockToDayMinute(date, timezone, wallEndWindow),
    [date, timezone, wallEndWindow],
  );
  const { t, i18n } = useTranslation("booking");
  const scrollerRef = React.useRef<HTMLElement>(null);
  const [collisionBoundary, setCollisionBoundary] = React.useState<HTMLElement | null>(null);
  const [dragRange, setDragRange] = React.useState<{ from: number; to: number } | null>(null);
  const [expandedEventId, setExpandedEventId] = React.useState<string | null>(null);
  const instanceId = React.useId();
  const headingId = `${instanceId}-heading`;
  const [internalViewState, setInternalViewState] = React.useState<DayTimelineViewState>({
    zoom: 1,
    centerMinute: (startWindow + endWindow) / 2,
  });
  const activeViewState = viewState ?? internalViewState;
  const { zoom, centerMinute } = activeViewState;
  const requestedCenter = React.useRef(centerMinute);
  const scrollCenter = React.useRef(centerMinute);
  const hourLabels = React.useMemo(
    () =>
      Array.from({ length: Math.ceil(dayMinutes / 60) }, (_, hour) =>
        formatMinuteWithDayOffset(date, timezone, hour * 60),
      ),
    [date, timezone, dayMinutes],
  );
  const positionedEvents = positionEvents(events, dayMinutes);
  const laneCount = Math.max(1, ...positionedEvents.map(({ lane }) => lane + 1));
  const density = peakHourlyDensity(positionedEvents, dayMinutes);
  const densityHourWidth =
    hourWidth ?? BASE_HOUR_WIDTH + Math.max(0, density - EVENTS_PER_BASE_HOUR) * DENSITY_WIDTH_STEP;
  const visibleMinutes = Math.max(snapIncrementMinutes, endWindow - startWindow);
  const nowEdge =
    nowMinute === undefined ? null : nowMinute < startWindow ? "before" : nowMinute > endWindow ? "after" : "inside";

  React.useLayoutEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    // Explicit navigation wins; otherwise preserve scrolling across zoom, date changes and new rows.
    const center =
      requestedCenter.current !== centerMinute ? centerMinute : (scrollSync?.centerMinute ?? scrollCenter.current);
    requestedCenter.current = centerMinute;
    scrollCenter.current = center;
    const target = (center / dayMinutes) * scroller.scrollWidth - scroller.clientWidth / 2;
    scroller.scrollLeft = Math.min(scroller.scrollWidth - scroller.clientWidth, Math.max(0, target));
    return scrollSync?.register(scroller, dayMinutes, center);
  }, [centerMinute, zoom, dayMinutes, scrollSync]);

  const updateViewState = (nextViewState: DayTimelineViewState) => {
    if (viewState === undefined) setInternalViewState(nextViewState);
    onViewStateChange?.(nextViewState);
  };

  const changeZoom = (delta: number) => {
    const scroller = scrollerRef.current;
    const nextCenterMinute = scroller
      ? ((scroller.scrollLeft + scroller.clientWidth / 2) / scroller.scrollWidth) * dayMinutes
      : centerMinute;
    updateViewState({ zoom: Math.min(4, Math.max(1, zoom + delta)), centerMinute: nextCenterMinute });
  };

  const syncScrollPosition = () => {
    const scroller = scrollerRef.current;
    if (!scroller || scroller.scrollWidth === 0) return;
    scrollCenter.current = ((scroller.scrollLeft + scroller.clientWidth / 2) / scroller.scrollWidth) * dayMinutes;
    scrollSync?.scroll(scroller);
  };

  const formattedDate = new Intl.DateTimeFormat(i18n.language, { dateStyle: "full", timeZone: "UTC" }).format(
    new Date(`${date}T12:00:00Z`),
  );
  const nowLabel =
    nowMinute === undefined || nowEdge === null
      ? null
      : nowEdge === "before"
        ? t("dayTimeline.now.current", { time: formatMinuteWithDayOffset(date, timezone, nowMinute) })
        : nowEdge === "after"
          ? t("dayTimeline.now.afterWindow", { time: formatMinuteWithDayOffset(date, timezone, nowMinute) })
          : t("dayTimeline.now.current", { time: formatMinuteWithDayOffset(date, timezone, nowMinute) });
  const tableRow = variant === "table-row";
  const eventAreaTop = tableRow ? 32 : 64;
  const setScrollerRef = React.useCallback((node: HTMLElement | null) => {
    scrollerRef.current = node;
    setCollisionBoundary(node);
  }, []);
  const snapMinute = (minute: number) =>
    Math.min(dayMinutes, Math.max(0, Math.round(minute / snapIncrementMinutes) * snapIncrementMinutes));
  const minuteAt = (clientX: number, canvas: HTMLElement) => {
    const box = canvas.getBoundingClientRect();
    return snapMinute(((clientX - box.left) / box.width) * dayMinutes);
  };
  const freeCanvas = (target: EventTarget | null) =>
    target instanceof HTMLElement && !target.closest("article, button, a");

  return (
    <section
      aria-labelledby={tableRow ? undefined : headingId}
      className={cn("flex min-w-0 flex-col text-foreground", !tableRow && "gap-4")}
    >
      <header className={cn(tableRow && "sr-only")}>
        <div>
          <h2 id={headingId} className="text-xl font-semibold">
            <time dateTime={date}>{formattedDate}</time>
          </h2>
          <p className="text-sm text-muted-foreground">{timezone}</p>
        </div>
      </header>
      {showZoomControls && !tableRow && (
        <div className="flex justify-end">
          <fieldset className="flex items-center gap-1">
            <legend className="sr-only">{t("dayTimeline.zoom.legend")}</legend>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              aria-label={t("dayTimeline.zoom.out")}
              disabled={zoom === 1}
              onClick={() => changeZoom(-0.5)}
            >
              <Minus />
            </Button>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              aria-label={t("dayTimeline.zoom.in")}
              disabled={zoom === 4}
              onClick={() => changeZoom(0.5)}
            >
              <Plus />
            </Button>
          </fieldset>
        </div>
      )}

      <div className="relative grid min-w-0 flex-1">
        <section
          ref={setScrollerRef}
          aria-label={
            itemName
              ? t("dayTimeline.itemScrollLabel", { itemName, date: formattedDate, timezone })
              : t("dayTimeline.scrollLabel", { date: formattedDate, timezone })
          }
          className={cn(
            "grid min-w-0 grid-cols-1 overflow-x-auto rounded-sm border bg-card shadow-sm",
            !showScrollbar && "[scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
          )}
          data-testid="day-timeline-scroller"
          onScroll={syncScrollPosition}
          // biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users need to focus and horizontally scroll the timeline.
          tabIndex={0}
        >
          <div
            className={cn("relative select-none touch-none", onRangeSelect && !creationDisabled && "cursor-crosshair")}
            style={{
              width: `${(dayMinutes / visibleMinutes) * 100 * zoom}%`,
              minWidth: densityHourWidth * (dayMinutes / 60) * zoom,
              minHeight: eventAreaTop + 16 + laneCount * EVENT_LANE_PITCH,
            }}
            data-testid="day-timeline-canvas"
            data-timeline-date={date}
            data-hour-width={densityHourWidth}
            data-creation-disabled={creationDisabled || undefined}
            onPointerDown={(event) => {
              if (!onRangeSelect || creationDisabled || !freeCanvas(event.target)) return;
              const from = Math.min(dayMinutes - snapIncrementMinutes, minuteAt(event.clientX, event.currentTarget));
              if (event.nativeEvent.isTrusted) event.currentTarget.setPointerCapture(event.pointerId);
              setDragRange({ from, to: from + snapIncrementMinutes });
            }}
            onPointerMove={(event) => {
              if (!dragRange || creationDisabled) return;
              setDragRange({ ...dragRange, to: minuteAt(event.clientX, event.currentTarget) });
            }}
            onPointerUp={(event) => {
              if (!dragRange || !onRangeSelect || creationDisabled) {
                setDragRange(null);
                return;
              }
              const startMinute = Math.min(dragRange.from, dragRange.to);
              const endMinute = Math.max(dragRange.from, dragRange.to);
              setDragRange(null);
              if (endMinute - startMinute < snapIncrementMinutes) return;
              onRangeSelect({ startMinute, endMinute }, scrollerRef.current ?? event.currentTarget);
            }}
            onPointerCancel={() => setDragRange(null)}
          >
            <div className="pointer-events-none absolute inset-0" aria-hidden="true">
              {hourLabels.map((label, hour) => (
                <div
                  key={hour}
                  style={{
                    left: `${((hour * 60) / dayMinutes) * 100}%`,
                    width: `${(Math.min(60, dayMinutes - hour * 60) / dayMinutes) * 100}%`,
                  }}
                  className={cn("absolute inset-y-0 border-l border-border/80", hour % 2 === 0 && "bg-muted/25")}
                >
                  <span className={cn("absolute left-2 text-[11px] font-medium", tableRow ? "top-2" : "top-10")}>
                    {label}
                  </span>
                </div>
              ))}
            </div>
            <ol className="absolute inset-0 list-none">
              {positionedEvents.map(({ event, startMinute, visualEndMinute, lane }) => {
                return (
                  <li
                    key={event.id}
                    className="absolute z-10"
                    style={{
                      left: `${(startMinute / dayMinutes) * 100}%`,
                      width: `${((visualEndMinute - startMinute) / dayMinutes) * 100}%`,
                      top: eventAreaTop + lane * EVENT_LANE_PITCH,
                      height: EVENT_CARD_HEIGHT,
                    }}
                    data-event-id={event.id}
                    data-lane={lane}
                  >
                    <DayTimelineEventCard
                      event={event}
                      date={date}
                      timezone={timezone}
                      compactCards={compactCards}
                      variant="timeline"
                      expanded={expandedEventId === event.id}
                      onExpandedChange={(expanded) =>
                        setExpandedEventId((current) => (expanded ? event.id : current === event.id ? null : current))
                      }
                      collisionBoundary={collisionBoundary}
                      expandedCardClassName={
                        typeof expandedCardClassName === "function"
                          ? expandedCardClassName(event)
                          : expandedCardClassName
                      }
                      renderEventActions={renderEventActions}
                      renderBlockoutActions={renderBlockoutActions}
                    />
                  </li>
                );
              })}
            </ol>
            {dragRange ? (
              <div
                aria-hidden="true"
                className="pointer-events-none absolute z-40 rounded-sm border-2 border-primary bg-primary/20"
                style={{
                  top: eventAreaTop,
                  bottom: 0,
                  left: `${(Math.min(dragRange.from, dragRange.to) / dayMinutes) * 100}%`,
                  width: `${(Math.abs(dragRange.to - dragRange.from) / dayMinutes) * 100}%`,
                }}
              >
                <span className="absolute top-1 left-1 rounded-sm bg-primary px-1 text-[10px] font-semibold whitespace-nowrap text-primary-foreground">
                  {`${formatMinuteWithDayOffset(date, timezone, Math.min(dragRange.from, dragRange.to))}–${formatMinuteWithDayOffset(date, timezone, Math.max(dragRange.from, dragRange.to))}`}
                </span>
              </div>
            ) : null}
            {nowMinute !== undefined && nowEdge === "inside" && nowLabel && (
              <NowMarker
                dayMinutes={dayMinutes}
                dateTime={dayMinuteToZonedTime(date, timezone, nowMinute).toInstant().toString()}
                minute={nowMinute}
                edge="inside"
                label={nowLabel}
              />
            )}
          </div>
        </section>
        {nowMinute !== undefined && nowEdge !== null && nowEdge !== "inside" && nowLabel && (
          <NowMarker
            dayMinutes={dayMinutes}
            dateTime={dayMinuteToZonedTime(date, timezone, nowMinute).toInstant().toString()}
            minute={nowMinute}
            edge={nowEdge}
            label={nowLabel}
          />
        )}
      </div>
    </section>
  );
}
