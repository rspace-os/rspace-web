import { Temporal } from "@js-temporal/polyfill";
import { Minus, Plus } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { dayMinuteToZonedTime, wallClockToDayMinute, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import {
  CLOSED_HOURS_CLASS_NAME,
  type DayTimelineActionRenderers,
  type DayTimelineEvent,
  type DayTimelineRange,
  type DayTimelineViewState,
  formatMinuteWithDayOffset,
  period,
} from "./DayTimelineEvent";
import { DayTimelineEventCard } from "./DayTimelineEventCard";
import { expandedEventIdAfterChange } from "./expandedEvent";
import { centerMinuteForScrollPosition, type useDayTimelineScrollSync } from "./useDayTimelineScrollSync";

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

function positionEvents(
  events: ReadonlyArray<DayTimelineEvent>,
  dayMinutes: number,
  positions?: ReadonlyMap<string, DayTimelineRange>,
): Array<PositionedEvent> {
  const laneEnds: Array<number> = [];
  return events
    .filter((event) => {
      const range = positions?.get(event.id) ?? event;
      return range.endMinute > 0 && range.startMinute < dayMinutes && range.endMinute > range.startMinute;
    })
    .toSorted((leftEvent, rightEvent) => {
      const left = positions?.get(leftEvent.id) ?? leftEvent;
      const right = positions?.get(rightEvent.id) ?? rightEvent;
      return (
        left.startMinute - right.startMinute ||
        left.endMinute - right.endMinute ||
        leftEvent.id.localeCompare(rightEvent.id)
      );
    })
    .map((event) => {
      const range = positions?.get(event.id) ?? event;
      const startMinute = Math.max(0, range.startMinute);
      const endMinute = Math.min(dayMinutes, range.endMinute);
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
        edge === "before" && "left-0",
        edge === "after" && "right-0",
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
  eventPositions,
  closedPeriods = [],
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
  proposeSelection,
  creationDisabled = false,
  onRangeSelect,
}: {
  date: string;
  timezone: string;
  events: ReadonlyArray<DayTimelineEvent>;
  /** Keep open cards positioned during reconciliation while displaying their live event times. */
  eventPositions?: ReadonlyMap<string, DayTimelineRange>;
  /** Periods outside the item's opening hours, in elapsed day minutes; shaded behind the events. */
  closedPeriods?: ReadonlyArray<DayTimelineRange>;
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
  /**
   * Turns a dragged range into a valid proposal for the item, which may extend past the day; without it the drag
   * selects whole minutes. Returning undefined offers nothing for that range.
   */
  proposeSelection?: (range: DayTimelineRange) => DayTimelineRange | undefined;
  creationDisabled?: boolean;
  onRangeSelect?: (range: DayTimelineRange, trigger: HTMLElement) => void;
} & DayTimelineActionRenderers) {
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
  const timeFormat = useBookingTimeFormat();
  const scrollerRef = React.useRef<HTMLElement>(null);
  const [collisionBoundary, setCollisionBoundary] = React.useState<HTMLElement | null>(null);
  const [dragRange, setDragRange] = React.useState<{ from: number; to: number; pointerId: number } | null>(null);
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
        formatMinuteWithDayOffset(date, timezone, hour * 60, timeFormat),
      ),
    [date, timeFormat, timezone, dayMinutes],
  );
  const positionedEvents = positionEvents(events, dayMinutes, eventPositions);
  const laneCount = Math.max(1, ...positionedEvents.map(({ lane }) => lane + 1));
  const density = peakHourlyDensity(positionedEvents, dayMinutes);
  const densityHourWidth =
    hourWidth ?? BASE_HOUR_WIDTH + Math.max(0, density - EVENTS_PER_BASE_HOUR) * DENSITY_WIDTH_STEP;
  const visibleMinutes = Math.max(5, endWindow - startWindow);
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
    const nextCenterMinute = scroller ? centerMinuteForScrollPosition(scroller, dayMinutes) : centerMinute;
    updateViewState({ zoom: Math.min(4, Math.max(1, zoom + delta)), centerMinute: nextCenterMinute });
  };

  const syncScrollPosition = () => {
    const scroller = scrollerRef.current;
    if (!scroller || scroller.scrollWidth === 0) return;
    scrollCenter.current = centerMinuteForScrollPosition(scroller, dayMinutes);
    scrollSync?.scroll(scroller);
  };

  const formattedDate = Temporal.PlainDate.from(date).toLocaleString(i18n.language, { dateStyle: "full" });
  const nowTime =
    nowMinute === undefined || nowEdge === null
      ? null
      : formatMinuteWithDayOffset(date, timezone, nowMinute, timeFormat);
  const nowLabel =
    nowTime === null || nowEdge === null
      ? null
      : nowEdge === "after"
        ? t("dayTimeline.now.afterWindow", { time: nowTime })
        : t("dayTimeline.now.current", { time: nowTime });
  const tableRow = variant === "table-row";
  const eventAreaTop = tableRow ? 32 : 64;
  const setScrollerRef = React.useCallback((node: HTMLElement | null) => {
    scrollerRef.current = node;
    setCollisionBoundary(node);
  }, []);
  const minuteAt = (clientX: number, canvas: HTMLElement) => {
    const box = canvas.getBoundingClientRect();
    return Math.min(dayMinutes, Math.max(0, Math.round(((clientX - box.left) / box.width) * dayMinutes)));
  };
  // The proposal a drag would create, so the preview shows exactly what releasing selects.
  const selection = (drag: { from: number; to: number }): DayTimelineRange | undefined => {
    const raw = { startMinute: Math.min(drag.from, drag.to), endMinute: Math.max(drag.from, drag.to) };
    if (proposeSelection) return proposeSelection(raw);
    return raw.endMinute > raw.startMinute ? raw : undefined;
  };
  const dragSelection = dragRange ? selection(dragRange) : undefined;
  // React bubbles events out of portals, so a click inside an event's popover must not start a range.
  const freeCanvas = (target: EventTarget | null, canvas: HTMLElement) =>
    target instanceof HTMLElement && canvas.contains(target) && !target.closest("article, button, a");

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
            className={cn("relative select-none", onRangeSelect && !creationDisabled && "touch-none cursor-crosshair")}
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
              if (
                !onRangeSelect ||
                creationDisabled ||
                !event.isPrimary ||
                event.button !== 0 ||
                !freeCanvas(event.target, event.currentTarget)
              )
                return;
              const from = Math.min(dayMinutes - 1, minuteAt(event.clientX, event.currentTarget));
              if (event.nativeEvent.isTrusted) event.currentTarget.setPointerCapture(event.pointerId);
              // A click without a drag proposes the shortest valid booking from that minute.
              setDragRange({ from, to: from + 1, pointerId: event.pointerId });
            }}
            onPointerMove={(event) => {
              if (!dragRange || dragRange.pointerId !== event.pointerId || creationDisabled) return;
              setDragRange({ ...dragRange, to: minuteAt(event.clientX, event.currentTarget) });
            }}
            onPointerUp={(event) => {
              if (!dragRange || dragRange.pointerId !== event.pointerId) return;
              if (!onRangeSelect || creationDisabled) {
                setDragRange(null);
                return;
              }
              const proposal = selection(dragRange);
              setDragRange(null);
              if (!proposal) return;
              onRangeSelect(proposal, scrollerRef.current ?? event.currentTarget);
            }}
            onPointerCancel={(event) => {
              if (dragRange?.pointerId === event.pointerId) setDragRange(null);
            }}
          >
            <div className="pointer-events-none absolute inset-0" aria-hidden="true">
              {closedPeriods.map(({ startMinute, endMinute }) => {
                const from = Math.max(0, startMinute);
                const to = Math.min(dayMinutes, endMinute);
                return to > from ? (
                  <div
                    key={`closed-${from}-${to}`}
                    data-testid="day-timeline-closed-hours"
                    className={`absolute inset-y-0 ${CLOSED_HOURS_CLASS_NAME}`}
                    style={{
                      left: `${(from / dayMinutes) * 100}%`,
                      width: `${((to - from) / dayMinutes) * 100}%`,
                    }}
                  />
                ) : null;
              })}
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
                    className="absolute z-10 data-[calendar-event-focus-highlight=true]:ring-4 data-[calendar-event-focus-highlight=true]:ring-ring data-[calendar-event-focus-highlight=true]:ring-offset-2"
                    style={{
                      left: `${(startMinute / dayMinutes) * 100}%`,
                      width: `${((visualEndMinute - startMinute) / dayMinutes) * 100}%`,
                      top: eventAreaTop + lane * EVENT_LANE_PITCH,
                      height: EVENT_CARD_HEIGHT,
                    }}
                    data-event-id={event.id}
                    data-calendar-event-focus={`${date}:${event.id}`}
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
                        setExpandedEventId((current) => expandedEventIdAfterChange(current, event.id, expanded))
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
            {dragSelection ? (
              // Clipped to the day; a proposal that continues onto later days keeps its true end in the label.
              <div
                aria-hidden="true"
                data-testid="day-timeline-selection"
                className="pointer-events-none absolute z-40 rounded-sm border-2 border-primary bg-primary/20"
                style={{
                  top: eventAreaTop,
                  bottom: 0,
                  left: `${(Math.max(0, dragSelection.startMinute) / dayMinutes) * 100}%`,
                  width: `${((Math.min(dayMinutes, dragSelection.endMinute) - Math.max(0, dragSelection.startMinute)) / dayMinutes) * 100}%`,
                }}
              >
                <span className="absolute top-1 left-1 rounded-sm bg-primary px-1 text-[10px] font-semibold whitespace-nowrap text-primary-foreground">
                  {period(dragSelection, date, timezone, "–", timeFormat)}
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
