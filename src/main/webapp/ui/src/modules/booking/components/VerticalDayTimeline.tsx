import { Temporal } from "@js-temporal/polyfill";
import { CalendarDays, ChevronLeft, ChevronRight, RotateCcw } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import {
  adjustVerticalTimelineRange,
  canAdjustVerticalTimelineRange,
  stepVerticalTimelineRange,
  verticalTimelineRangeToDraft,
} from "@/modules/booking/creation/verticalTimelineRange";
import { resolveBookingWindow } from "@/modules/booking/creation/ZonedBookingWindowFields";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { type OpeningSchedule, openingIntervals } from "@/modules/booking/domain/bookingOpeningHours";
import {
  addCalendarDays,
  type BookingWindowDraft,
  currentWallClock,
  dayMinuteToZonedTime,
  instantToDayMinute,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader } from "@/modules/common/ui/card";
import { cn } from "@/modules/common/utils/cn";
import { CLOSED_HOURS_CLASS_NAME, type DayTimelineEvent, formatMinuteWithDayOffset } from "./DayTimelineEvent";
import { DayTimelineEventCard } from "./DayTimelineEventCard";
import { layoutVerticalTimelineEvents } from "./verticalTimelineLayout";

const PIXELS_PER_HOUR = 56;
const TIME_GUTTER_WIDTH = 64;
const MINIMUM_LANE_WIDTH = 96;
const MINIMUM_EVENT_CARD_HEIGHT = 44;
const HANDLE_HEIGHT = 12;
const MOVE_HANDLE_MINIMUM_HEIGHT = 44;

type IsoRange = { start: string; end: string };
type AdjustmentEdge = "move" | "start" | "end";
type Direction = "previous" | "next";
export type VerticalDayTimelineScheduleWindow = OpeningSchedule & { timezone: string };

export type VerticalDayTimelineSchedule =
  | { status: "no-target" }
  | { status: "loading" }
  | { status: "success" }
  | { status: "error"; onRetry?: () => void };

export type VerticalDayTimelineEditing = {
  draft: BookingWindowDraft;
  schedulingTimeZone: string;
  slotGranularityMinutes: number;
  onChange: (draft: BookingWindowDraft) => void;
  onInteractionChange?: (active: boolean) => void;
  disabledReason?: string;
  targetKey?: string;
};

export type VerticalDayTimelineProps = {
  date: string;
  onDateChange: (date: string) => void;
  timezone: string;
  itemName?: string;
  events: readonly DayTimelineEvent[];
  schedule: VerticalDayTimelineSchedule;
  editing?: VerticalDayTimelineEditing;
  scheduleWindow?: VerticalDayTimelineScheduleWindow;
};

function resolveRange(draft: BookingWindowDraft | undefined, timezone: string): IsoRange | undefined {
  if (!draft) return undefined;
  return resolveBookingWindow(draft, timezone).window;
}

function timeRange(
  range: IsoRange,
  date: string,
  timezone: string,
  timeFormat: import("@/modules/booking/domain/bookingTime").BookingTimeFormat = "AUTOMATIC",
): string {
  return `${formatMinuteWithDayOffset(date, timezone, instantToDayMinute(range.start, date, timezone), timeFormat)}–${formatMinuteWithDayOffset(date, timezone, instantToDayMinute(range.end, date, timezone), timeFormat)}`;
}

function rangesEqual(left: IsoRange, right: IsoRange): boolean {
  return Temporal.Instant.compare(left.start, right.start) === 0 && Temporal.Instant.compare(left.end, right.end) === 0;
}

function openingSegments(
  date: string,
  displayTimezone: string,
  scheduleWindow: VerticalDayTimelineScheduleWindow | undefined,
): Array<{ startMinute: number; endMinute: number }> {
  if (!scheduleWindow) return [];
  return openingIntervals(scheduleWindow, scheduleWindow.timezone, zonedDayBounds(date, displayTimezone)).map(
    (opening) => ({
      startMinute: instantToDayMinute(opening.start, date, displayTimezone),
      endMinute: instantToDayMinute(opening.end, date, displayTimezone),
    }),
  );
}

function closedSegments(open: readonly { startMinute: number; endMinute: number }[], dayMinutes: number) {
  const result: Array<{ startMinute: number; endMinute: number }> = [];
  let cursor = 0;
  for (const interval of open) {
    if (interval.startMinute > cursor) result.push({ startMinute: cursor, endMinute: interval.startMinute });
    cursor = Math.max(cursor, interval.endMinute);
  }
  if (cursor < dayMinutes) result.push({ startMinute: cursor, endMinute: dayMinutes });
  return result;
}

export function VerticalDayTimeline({
  date,
  onDateChange,
  timezone,
  itemName,
  events,
  schedule,
  editing,
  scheduleWindow,
}: VerticalDayTimelineProps) {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const instanceId = React.useId();
  const instructionsId = `${instanceId}-keyboard-instructions`;
  const dateHeadingId = `${instanceId}-date-heading`;
  const dayMinutes = React.useMemo(() => zonedDayBounds(date, timezone).elapsedMinutes, [date, timezone]);
  const dayHeight = (dayMinutes / 60) * PIXELS_PER_HOUR;
  const canvasRef = React.useRef<HTMLDivElement>(null);
  const scrollerRef = React.useRef<HTMLDivElement>(null);
  const [collisionBoundary, setCollisionBoundary] = React.useState<HTMLElement | null>(null);
  const [portalContainer, setPortalContainer] = React.useState<HTMLElement | null>(null);
  const [canvasWidth, setCanvasWidth] = React.useState(0);
  const [expandedEventId, setExpandedEventId] = React.useState<string | null>(null);
  const [preview, setPreview] = React.useState<IsoRange | null>(null);
  const [gestureActive, setGestureActive] = React.useState(false);
  const [announcement, setAnnouncement] = React.useState("");
  const interactionCallbackRef = React.useRef(editing?.onInteractionChange);
  React.useLayoutEffect(() => {
    interactionCallbackRef.current = editing?.onInteractionChange;
  }, [editing?.onInteractionChange]);
  const interactionRef = React.useRef<{
    edge: AdjustmentEdge;
    pointerId: number;
    handle: HTMLButtonElement;
    originalRange: IsoRange;
    pointerOffsetMinutes: number;
    contextKey: string;
    width: number;
    height: number;
  } | null>(null);
  const lastAutoScrollContext = React.useRef("");
  const openSegments = React.useMemo(
    () => openingSegments(date, timezone, scheduleWindow),
    [date, scheduleWindow, timezone],
  );
  const closedHours = React.useMemo(() => closedSegments(openSegments, dayMinutes), [dayMinutes, openSegments]);
  const editingRange = React.useMemo(() => resolveRange(editing?.draft, timezone), [editing?.draft, timezone]);
  const activeRange = preview ?? editingRange;
  const context = React.useMemo(
    () =>
      editing
        ? {
            date,
            displayTimezone: timezone,
            schedulingTimezone: editing.schedulingTimeZone,
            slotGranularityMinutes: editing.slotGranularityMinutes,
          }
        : undefined,
    [date, timezone, editing?.schedulingTimeZone, editing?.slotGranularityMinutes],
  );
  const directAdjustmentAllowed = Boolean(
    editing &&
      editingRange &&
      context &&
      !editing.disabledReason &&
      canAdjustVerticalTimelineRange(editingRange, context),
  );
  const positionedEvents = React.useMemo(() => layoutVerticalTimelineEvents(events, dayMinutes), [events, dayMinutes]);
  const dayEvents = schedule.status === "success" ? positionedEvents : [];
  const denseLanes = dayEvents.some(({ laneCount }) => canvasWidth > 0 && canvasWidth / laneCount < MINIMUM_LANE_WIDTH);
  const dateHeading = React.useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, { dateStyle: "full", timeZone: "UTC" }).format(
        new Date(`${date}T12:00:00Z`),
      ),
    [date, i18n.language],
  );
  const compactDateHeading = React.useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeZone: "UTC" }).format(
        new Date(`${date}T12:00:00Z`),
      ),
    [date, i18n.language],
  );
  const hourTicks = React.useMemo(
    () => Array.from({ length: Math.ceil(dayMinutes / 60) }, (_, index) => index),
    [dayMinutes],
  );
  const resolvedDraftDay = editingRange ? currentWallClock(editingRange.start, timezone).date : undefined;
  const isDraftDay = !resolvedDraftDay || resolvedDraftDay === date;
  const draftStartMinute = editingRange ? instantToDayMinute(editingRange.start, date, timezone) : 0;
  const gestureContextKey = [
    date,
    timezone,
    editing?.targetKey ?? "",
    editingRange?.start ?? "",
    editingRange?.end ?? "",
    editing?.schedulingTimeZone ?? "",
    editing?.slotGranularityMinutes ?? "",
    editing?.disabledReason ?? "",
  ].join("|");

  const minuteAt = React.useCallback(
    (clientY: number) => {
      const rect = canvasRef.current?.getBoundingClientRect();
      if (!rect || rect.height === 0) return 0;
      return Math.min(dayMinutes, Math.max(0, ((clientY - rect.top) / rect.height) * dayMinutes));
    },
    [dayMinutes],
  );
  const rangeAtMinute = React.useCallback(
    (minute: number): string => dayMinuteToZonedTime(date, timezone, minute).toInstant().toString(),
    [date, timezone],
  );

  const cancelInteraction = React.useCallback(() => {
    const active = interactionRef.current;
    interactionRef.current = null;
    setGestureActive(false);
    setPreview(null);
    if (active) {
      interactionCallbackRef.current?.(false);
      if (active.handle.hasPointerCapture(active.pointerId)) active.handle.releasePointerCapture(active.pointerId);
    }
  }, []);

  const commitRange = React.useCallback(
    (next: IsoRange) => {
      if (!editing || !editingRange || rangesEqual(next, editingRange)) return;
      editing.onChange(verticalTimelineRangeToDraft(next, timezone));
      setAnnouncement(
        t("dayTimeline.vertical.intervalChanged", {
          period: timeRange(next, date, timezone, timeFormat),
        }),
      );
    },
    [date, editing, editingRange, t, timezone],
  );

  const changeByStep = React.useCallback(
    (edge: AdjustmentEdge, direction: Direction) => {
      if (!editing || !context || !activeRange || !directAdjustmentAllowed || interactionRef.current) return;
      const next = stepVerticalTimelineRange(activeRange, edge, direction, context);
      if (next) commitRange(next);
    },
    [activeRange, commitRange, context, directAdjustmentAllowed, editing],
  );

  const onDraftHandleKeyDown = (edge: AdjustmentEdge, event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === "Escape" && interactionRef.current) {
      event.preventDefault();
      cancelInteraction();
      return;
    }
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    event.preventDefault();
    changeByStep(edge, event.key === "ArrowUp" ? "previous" : "next");
  };

  const startInteraction = (edge: AdjustmentEdge, event: React.PointerEvent<HTMLButtonElement>) => {
    if (interactionRef.current || !directAdjustmentAllowed || !editingRange || !context || !event.isPrimary) return;
    if (event.pointerType === "mouse" && event.button !== 0) return;
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect || rect.height === 0) return;
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.setPointerCapture(event.pointerId);
    const startMinute = instantToDayMinute(editingRange.start, date, timezone);
    const anchorMinute = edge === "end" ? instantToDayMinute(editingRange.end, date, timezone) : startMinute;
    interactionRef.current = {
      edge,
      pointerId: event.pointerId,
      handle: event.currentTarget,
      originalRange: editingRange,
      pointerOffsetMinutes: minuteAt(event.clientY) - anchorMinute,
      contextKey: gestureContextKey,
      width: rect.width,
      height: rect.height,
    };
    setExpandedEventId(null);
    setPreview(editingRange);
    setGestureActive(true);
    interactionCallbackRef.current?.(true);
  };

  const updateInteraction = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = interactionRef.current;
    if (!active || active.pointerId !== event.pointerId || !context) return;
    event.preventDefault();
    event.stopPropagation();
    const minute = minuteAt(event.clientY) - active.pointerOffsetMinutes;
    const target = rangeAtMinute(minute);
    const next = adjustVerticalTimelineRange(active.originalRange, active.edge, target, context);
    setPreview(next ?? active.originalRange);
  };

  const finishInteraction = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = interactionRef.current;
    if (!active || active.pointerId !== event.pointerId || !context) return;
    event.preventDefault();
    event.stopPropagation();
    const minute = minuteAt(event.clientY) - active.pointerOffsetMinutes;
    const target = rangeAtMinute(minute);
    const next = adjustVerticalTimelineRange(active.originalRange, active.edge, target, context);
    interactionRef.current = null;
    setGestureActive(false);
    setPreview(null);
    interactionCallbackRef.current?.(false);
    if (active.handle.hasPointerCapture(active.pointerId)) active.handle.releasePointerCapture(active.pointerId);
    if (next) commitRange(next);
  };

  const handlePointerCancel = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (interactionRef.current?.pointerId === event.pointerId) cancelInteraction();
  };
  const handleLostPointerCapture = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (interactionRef.current?.pointerId === event.pointerId) cancelInteraction();
  };

  React.useEffect(() => {
    if (!gestureActive) return;
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      cancelInteraction();
    };
    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, [cancelInteraction, gestureActive]);

  React.useEffect(
    () => () => {
      const active = interactionRef.current;
      interactionRef.current = null;
      if (!active) return;
      interactionCallbackRef.current?.(false);
      if (active.handle.hasPointerCapture(active.pointerId)) active.handle.releasePointerCapture(active.pointerId);
    },
    [],
  );

  React.useEffect(() => {
    if (schedule.status === "no-target") cancelInteraction();
  }, [cancelInteraction, schedule.status]);

  React.useEffect(() => {
    const active = interactionRef.current;
    if (active && active.contextKey !== gestureContextKey) cancelInteraction();
  }, [cancelInteraction, gestureContextKey]);

  React.useEffect(() => {
    setExpandedEventId(null);
  }, [date, timezone]);

  React.useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const observer = new ResizeObserver(() => {
      const active = interactionRef.current;
      if (!active) {
        setCanvasWidth(canvas.clientWidth);
        return;
      }
      if (canvas.clientWidth !== active.width || canvas.clientHeight !== active.height) cancelInteraction();
    });
    observer.observe(canvas);
    setCanvasWidth(canvas.clientWidth);
    return () => observer.disconnect();
  }, [cancelInteraction, schedule.status]);

  React.useLayoutEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller || schedule.status === "no-target") return;
    const canvas = canvasRef.current;
    if (!canvas || canvas.clientWidth === 0 || scroller.clientHeight === 0) {
      // A collapsed aside loses its scroll position, so retry the initial position when it is shown again.
      lastAutoScrollContext.current = "";
      return;
    }
    const contextKey = [
      date,
      timezone,
      editing ? "editing" : "read-only",
      editing?.targetKey ?? "",
      scheduleWindow?.timezone ?? "",
      scheduleWindow?.openingStart ?? "",
      scheduleWindow?.openingEnd ?? "",
      scheduleWindow?.openDays.join(",") ?? "",
      JSON.stringify(scheduleWindow?.openingExceptions ?? []),
    ].join("|");
    const draftKey = `${contextKey}|draft`;
    if (editingRange && resolvedDraftDay === date && lastAutoScrollContext.current !== draftKey) {
      const start = Math.max(0, Math.min(dayMinutes, draftStartMinute));
      scroller.scrollTop = Math.max(0, start * (PIXELS_PER_HOUR / 60) - PIXELS_PER_HOUR * 2);
      lastAutoScrollContext.current = draftKey;
      return;
    }
    if (lastAutoScrollContext.current === contextKey || lastAutoScrollContext.current === draftKey) return;
    if (editing && !editingRange && !scheduleWindow) return;
    const start = openSegments[0]?.startMinute ?? 0;
    scroller.scrollTop = Math.max(0, start * (PIXELS_PER_HOUR / 60) - PIXELS_PER_HOUR * 2);
    lastAutoScrollContext.current = contextKey;
  }, [
    date,
    dayMinutes,
    draftStartMinute,
    editing,
    editingRange,
    openSegments,
    resolvedDraftDay,
    schedule.status,
    scheduleWindow,
    timezone,
    canvasWidth,
  ]);

  const draftSegment = activeRange
    ? {
        startMinute: Math.max(0, instantToDayMinute(activeRange.start, date, timezone)),
        endMinute: Math.min(dayMinutes, instantToDayMinute(activeRange.end, date, timezone)),
      }
    : undefined;
  const draftVisible = Boolean(draftSegment && draftSegment.endMinute > draftSegment.startMinute);
  const draftHeight =
    draftVisible && draftSegment ? (draftSegment.endMinute - draftSegment.startMinute) * (PIXELS_PER_HOUR / 60) : 0;
  const committedDraftHeight = editingRange
    ? Math.max(
        0,
        Math.min(dayMinutes, instantToDayMinute(editingRange.end, date, timezone)) -
          Math.max(0, instantToDayMinute(editingRange.start, date, timezone)),
      ) *
      (PIXELS_PER_HOUR / 60)
    : 0;
  const canvasMoveVisible = committedDraftHeight >= MOVE_HANDLE_MINIMUM_HEIGHT && directAdjustmentAllowed;
  // A one-hour draft at 56px/hour leaves a usable center between the 12px resize strips.
  const canvasResizeVisible = committedDraftHeight >= HANDLE_HEIGHT * 2 + 24 && directAdjustmentAllowed;

  const openDetails = (eventId: string, expanded: boolean) => {
    setExpandedEventId((current) => (expanded ? eventId : current === eventId ? null : current));
  };

  const renderScheduleStatus = () => {
    if (schedule.status === "no-target") {
      return (
        <p role="status" className="rounded-sm bg-muted px-3 py-2 text-sm text-muted-foreground">
          {t("dayTimeline.vertical.noTarget")}
        </p>
      );
    }
    if (schedule.status === "loading") {
      return (
        <p role="status" className="rounded-sm bg-muted px-3 py-2 text-sm text-muted-foreground">
          {t("dayTimeline.vertical.loading")}
        </p>
      );
    }
    if (schedule.status === "error") {
      return (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-2 rounded-sm border border-destructive/50 bg-destructive/5 px-3 py-2 text-sm"
        >
          <span>{t("dayTimeline.vertical.loadError")}</span>
          {schedule.onRetry ? (
            <Button type="button" variant="outline" size="sm" onClick={schedule.onRetry}>
              <RotateCcw aria-hidden="true" />
              {t("dayTimeline.vertical.retry")}
            </Button>
          ) : null}
        </div>
      );
    }
    if (events.length === 0) {
      return (
        <p role="status" className="rounded-sm bg-muted px-3 py-2 text-sm text-muted-foreground">
          {t("dayTimeline.vertical.empty")}
        </p>
      );
    }
    return null;
  };

  const compactEvents = dayEvents
    .filter(
      ({ startMinute, endMinute }) =>
        denseLanes || ((endMinute - startMinute) / 60) * PIXELS_PER_HOUR < MINIMUM_EVENT_CARD_HEIGHT,
    )
    .sort(
      (left, right) =>
        left.startMinute - right.startMinute ||
        left.endMinute - right.endMinute ||
        left.event.id.localeCompare(right.event.id),
    );
  const setScrollerRef = React.useCallback((node: HTMLDivElement | null) => {
    scrollerRef.current = node;
    setCollisionBoundary(node);
  }, []);
  const setPortalContainerRef = React.useCallback((node: HTMLElement | null) => setPortalContainer(node), []);

  return (
    <section
      ref={setPortalContainerRef}
      className="min-w-0 text-foreground"
      aria-label={t("dayTimeline.vertical.title")}
    >
      <Card size="sm" className="gap-0 py-0">
        <CardHeader className="border-b px-3 py-3">
          <nav
            aria-label={t("dayTimeline.vertical.dateNavigation")}
            className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2"
          >
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label={t("dayTimeline.vertical.previousDay")}
              onClick={() => onDateChange(addCalendarDays(date, -1))}
            >
              <ChevronLeft aria-hidden="true" />
            </Button>
            <div className="min-w-0 text-center">
              <h2 className="truncate text-base font-semibold leading-tight">
                <time id={dateHeadingId} dateTime={date} title={dateHeading}>
                  <span className="sm:hidden">{compactDateHeading}</span>
                  <span className="hidden sm:inline">{dateHeading}</span>
                </time>
              </h2>
              <p className="truncate text-xs text-muted-foreground">
                {itemName ? `${timezone} · ${itemName}` : timezone}
              </p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label={t("dayTimeline.vertical.nextDay")}
              onClick={() => onDateChange(addCalendarDays(date, 1))}
            >
              <ChevronRight aria-hidden="true" />
            </Button>
          </nav>
          {editing && !isDraftDay ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="justify-self-center"
              aria-label={t("dayTimeline.vertical.returnToDraftDay")}
              onClick={() => resolvedDraftDay && onDateChange(resolvedDraftDay)}
            >
              <CalendarDays aria-hidden="true" />
              {t("dayTimeline.vertical.returnToDraftDay")}
            </Button>
          ) : null}
        </CardHeader>
        <CardContent className="p-3">
          {renderScheduleStatus()}

          {schedule.status !== "no-target" ? (
            <section
              ref={setScrollerRef}
              data-testid="vertical-day-timeline-scroller"
              className="max-h-[min(64vh,40rem)] min-h-56 overflow-auto rounded-sm border bg-card"
              aria-labelledby={dateHeadingId}
              // biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users need to focus and scroll the timeline.
              tabIndex={0}
            >
              <div
                className="relative grid min-w-0 grid-cols-[4rem_minmax(0,1fr)]"
                style={{ height: dayHeight, minWidth: TIME_GUTTER_WIDTH + MINIMUM_LANE_WIDTH }}
              >
                <div
                  className="pointer-events-none sticky left-0 z-20 col-start-1 row-start-1 h-full"
                  aria-hidden="true"
                >
                  {hourTicks.map((hour) => (
                    <span
                      key={hour}
                      className="absolute left-0 -translate-y-1/2 bg-card px-1 text-[11px] leading-none text-muted-foreground tabular-nums"
                      style={{ top: hour * PIXELS_PER_HOUR }}
                    >
                      {formatMinuteWithDayOffset(date, timezone, hour * 60, timeFormat)}
                    </span>
                  ))}
                </div>
                <div
                  ref={canvasRef}
                  data-testid="vertical-day-timeline-canvas"
                  data-timeline-date={date}
                  className="relative col-start-2 row-start-1 min-w-0"
                >
                  <div className="pointer-events-none absolute inset-0" aria-hidden="true">
                    {closedHours.map((segment, index) => (
                      <div
                        key={`closed-${index}`}
                        data-testid="vertical-day-timeline-closed-hours"
                        className={`absolute inset-x-0 ${CLOSED_HOURS_CLASS_NAME}`}
                        style={{
                          top: segment.startMinute * (PIXELS_PER_HOUR / 60),
                          height: (segment.endMinute - segment.startMinute) * (PIXELS_PER_HOUR / 60),
                        }}
                      />
                    ))}
                    {hourTicks.map((hour) => (
                      <div
                        key={`hour-${hour}`}
                        className="absolute border-t border-border/70"
                        style={{ top: hour * PIXELS_PER_HOUR, left: -TIME_GUTTER_WIDTH, right: 0 }}
                      />
                    ))}
                  </div>
                  <ol className="absolute inset-y-0 right-2 left-0 m-0 list-none p-0">
                    {dayEvents.map(
                      ({ event, startMinute, endMinute, lane, laneCount, continuesBefore, continuesAfter }) => {
                        const top = startMinute * (PIXELS_PER_HOUR / 60);
                        const height = (endMinute - startMinute) * (PIXELS_PER_HOUR / 60);
                        const showCard = !denseLanes && height >= MINIMUM_EVENT_CARD_HEIGHT;
                        return (
                          <li
                            key={event.id}
                            data-event-id={event.id}
                            data-lane={lane}
                            data-lane-count={laneCount}
                            className="absolute z-10 overflow-visible px-0.5 py-px"
                            style={{ left: `${(lane / laneCount) * 100}%`, width: `${100 / laneCount}%`, top, height }}
                          >
                            {showCard ? (
                              <>
                                <DayTimelineEventCard
                                  event={event}
                                  date={date}
                                  timezone={timezone}
                                  compactCards={false}
                                  fitHeight={height}
                                  variant="timeline"
                                  expanded={expandedEventId === event.id}
                                  onExpandedChange={(expanded) => openDetails(event.id, expanded)}
                                  collisionBoundary={collisionBoundary}
                                  portalContainer={portalContainer}
                                />
                                {continuesBefore ? (
                                  <span
                                    role="img"
                                    aria-label={t("dayTimeline.vertical.continuesBefore")}
                                    className="pointer-events-none absolute top-0 right-0 left-0 h-1 border-t-2 border-dashed border-current"
                                  />
                                ) : null}
                                {continuesAfter ? (
                                  <span
                                    role="img"
                                    aria-label={t("dayTimeline.vertical.continuesAfter")}
                                    className="pointer-events-none absolute right-0 bottom-0 left-0 h-1 border-b-2 border-dashed border-current"
                                  />
                                ) : null}
                              </>
                            ) : (
                              <span
                                aria-hidden="true"
                                data-testid="vertical-day-timeline-occupancy"
                                className={cn(
                                  "absolute inset-x-1 top-0.5 bottom-0.5 rounded-sm border",
                                  event.kind === "blockout"
                                    ? "border-amber-700 bg-amber-300"
                                    : event.privacy === "busy"
                                      ? "border-slate-500 bg-slate-400"
                                      : "border-blue-700 bg-blue-500",
                                )}
                              />
                            )}
                          </li>
                        );
                      },
                    )}
                  </ol>
                  {draftVisible && draftSegment && activeRange ? (
                    <div
                      data-testid="vertical-day-timeline-draft"
                      data-timeline-window-editor
                      className="pointer-events-none absolute z-[60] overflow-hidden rounded-md border-2 border-primary bg-[color-mix(in_srgb,var(--primary)_25%,var(--background))] text-foreground shadow-lg ring-3 ring-ring/40"
                      style={{
                        top: draftSegment.startMinute * (PIXELS_PER_HOUR / 60),
                        height: draftHeight,
                        left: 0,
                        right: 8,
                      }}
                    >
                      <span
                        className={cn(
                          "absolute left-1 rounded-sm bg-primary px-1 py-0.5 text-[10px] font-semibold leading-tight text-primary-foreground",
                          canvasResizeVisible ? "top-3" : "top-1",
                        )}
                      >
                        <span className="block">{t("dayTimeline.vertical.draftLabel")}</span>
                        <span className="block whitespace-nowrap tabular-nums">
                          {timeRange(activeRange, date, timezone, timeFormat)}
                        </span>
                      </span>
                      {canvasMoveVisible ? (
                        <>
                          <button
                            type="button"
                            aria-describedby={instructionsId}
                            aria-label={t("dayTimeline.vertical.moveDraft", {
                              period: timeRange(activeRange, date, timezone, timeFormat),
                            })}
                            className="pointer-events-auto absolute inset-x-0 z-10 cursor-grab touch-none bg-transparent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
                            style={canvasResizeVisible ? { top: HANDLE_HEIGHT, bottom: HANDLE_HEIGHT } : { inset: 0 }}
                            onPointerDown={(event) => startInteraction("move", event)}
                            onPointerMove={updateInteraction}
                            onPointerUp={finishInteraction}
                            onPointerCancel={handlePointerCancel}
                            onLostPointerCapture={handleLostPointerCapture}
                            onKeyDown={(event) => onDraftHandleKeyDown("move", event)}
                          />
                          {canvasResizeVisible
                            ? (["start", "end"] as const).map((edge) => (
                                <button
                                  key={edge}
                                  type="button"
                                  aria-describedby={instructionsId}
                                  aria-label={
                                    edge === "start"
                                      ? t("dayTimeline.vertical.resizeStart", {
                                          period: timeRange(activeRange, date, timezone, timeFormat),
                                        })
                                      : t("dayTimeline.vertical.resizeEnd", {
                                          period: timeRange(activeRange, date, timezone, timeFormat),
                                        })
                                  }
                                  className={cn(
                                    "pointer-events-auto absolute inset-x-0 z-20 h-3 cursor-ns-resize touch-none bg-background/80 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring",
                                    edge === "start"
                                      ? "rounded-t-md border-t border-primary/60"
                                      : "rounded-b-md border-b border-primary/60",
                                  )}
                                  style={edge === "start" ? { top: 0 } : { bottom: 0 }}
                                  onPointerDown={(event) => startInteraction(edge, event)}
                                  onPointerMove={updateInteraction}
                                  onPointerUp={finishInteraction}
                                  onPointerCancel={handlePointerCancel}
                                  onLostPointerCapture={handleLostPointerCapture}
                                  onKeyDown={(event) => onDraftHandleKeyDown(edge, event)}
                                />
                              ))
                            : null}
                        </>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </div>
            </section>
          ) : null}

          {schedule.status === "success" && compactEvents.length > 0 ? (
            <details className="mt-3 border-t pt-3">
              <summary className="cursor-pointer text-sm font-medium">{t("dayTimeline.vertical.eventDetails")}</summary>
              <div className="mt-2 space-y-2">
                {compactEvents.map(({ event }) => (
                  <DayTimelineEventCard
                    key={event.id}
                    event={event}
                    date={date}
                    timezone={timezone}
                    compactCards={false}
                    expanded={expandedEventId === event.id}
                    onExpandedChange={(expanded) => openDetails(event.id, expanded)}
                    portalContainer={portalContainer}
                  />
                ))}
              </div>
            </details>
          ) : null}

          {editing && !draftVisible && editingRange ? (
            <p className="text-xs text-muted-foreground">
              {t("dayTimeline.vertical.draftOutsideDay", {
                period: timeRange(editingRange, date, timezone, timeFormat),
              })}
            </p>
          ) : null}
          {gestureActive ? (
            <p role="status" className="mt-2 text-xs text-muted-foreground">
              {t("dayTimeline.vertical.adjusting")}
            </p>
          ) : null}
        </CardContent>
      </Card>
      <p aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </p>
      <p id={instructionsId} className="sr-only">
        {t("dayTimeline.vertical.keyboardInstructions")}
      </p>
    </section>
  );
}

export default VerticalDayTimeline;
