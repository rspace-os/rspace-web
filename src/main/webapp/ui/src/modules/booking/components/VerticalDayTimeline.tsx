import { Temporal } from "@js-temporal/polyfill";
import { CalendarDays, ChevronLeft, ChevronRight, RotateCcw } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { resizeDisabledReason } from "@/modules/booking/creation/timelineEditText";
import type { TimelinePolicy } from "@/modules/booking/creation/timelineRange";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { OpeningSchedule } from "@/modules/booking/domain/bookingOpeningHours";
import {
  addCalendarDays,
  type BookingWindowDraft,
  currentWallClock,
  instantToDayMinute,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader } from "@/modules/common/ui/card";
import { type DayTimelineEvent, openingRangesOnDate } from "./DayTimelineEvent";
import { DayTimelineEventCard } from "./DayTimelineEventCard";
import { expandedEventIdAfterChange } from "./expandedEvent";
import { timeRange, useVerticalDayTimelineInteraction } from "./useVerticalDayTimelineInteraction";
import {
  clipRangeToDay,
  MINIMUM_EVENT_CARD_HEIGHT,
  MINIMUM_LANE_WIDTH,
  PIXELS_PER_HOUR,
  PIXELS_PER_MINUTE,
  VerticalDayTimelineCanvas,
} from "./VerticalDayTimelineCanvas";
import { layoutVerticalTimelineEvents } from "./verticalTimelineLayout";

export type VerticalDayTimelineScheduleWindow = OpeningSchedule & { timezone: string };

export type VerticalDayTimelineSchedule =
  | { status: "no-target" }
  | { status: "loading" }
  | { status: "success" }
  | { status: "error"; onRetry?: () => void };

export type VerticalDayTimelineEditing = {
  draft: BookingWindowDraft;
  /** The item's rules for this event kind; every adjustment is a complete valid interval. */
  policy: TimelinePolicy;
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

function scrollTopForMinute(minute: number): number {
  return Math.max(0, minute * PIXELS_PER_MINUTE - PIXELS_PER_HOUR * 2);
}

function openingSegments(
  date: string,
  displayTimezone: string,
  scheduleWindow: VerticalDayTimelineScheduleWindow | undefined,
): Array<{ startMinute: number; endMinute: number }> {
  if (!scheduleWindow) return [];
  return openingRangesOnDate(date, displayTimezone, scheduleWindow);
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
  const scrollerRef = React.useRef<HTMLDivElement>(null);
  const [collisionBoundary, setCollisionBoundary] = React.useState<HTMLElement | null>(null);
  const [portalContainer, setPortalContainer] = React.useState<HTMLElement | null>(null);
  const [expandedEventId, setExpandedEventId] = React.useState<string | null>(null);
  const {
    canvasRef,
    canvasWidth,
    editingRange,
    activeRange,
    activePeriod,
    directAdjustmentAllowed,
    gestureActive,
    announcement,
    startInteraction,
    updateInteraction,
    finishInteraction,
    onDraftHandleKeyDown,
    abortInteraction,
    adjuster,
    commitRange,
  } = useVerticalDayTimelineInteraction({
    date,
    timezone,
    dayMinutes,
    timeFormat,
    editing,
    schedule,
    setExpandedEventId,
  });
  const lastAutoScrollContext = React.useRef("");
  const openSegments = React.useMemo(
    () => openingSegments(date, timezone, scheduleWindow),
    [date, scheduleWindow, timezone],
  );
  const closedHours = React.useMemo(() => closedSegments(openSegments, dayMinutes), [dayMinutes, openSegments]);
  const positionedEvents = React.useMemo(() => layoutVerticalTimelineEvents(events, dayMinutes), [events, dayMinutes]);
  const dayEvents = schedule.status === "success" ? positionedEvents : [];
  const denseLanes = dayEvents.some(({ laneCount }) => canvasWidth > 0 && canvasWidth / laneCount < MINIMUM_LANE_WIDTH);
  const dateHeading = React.useMemo(
    () => Temporal.PlainDate.from(date).toLocaleString(i18n.language, { dateStyle: "full" }),
    [date, i18n.language],
  );
  const compactDateHeading = React.useMemo(
    () => Temporal.PlainDate.from(date).toLocaleString(i18n.language, { dateStyle: "medium" }),
    [date, i18n.language],
  );
  const resolvedDraftDay = editingRange ? currentWallClock(editingRange.start, timezone).date : undefined;
  const isDraftDay = !resolvedDraftDay || resolvedDraftDay === date;
  const draftStartMinute = editingRange ? instantToDayMinute(editingRange.start, date, timezone) : 0;
  React.useEffect(() => {
    setExpandedEventId(null);
  }, [date, timezone]);

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
      JSON.stringify(scheduleWindow?.openingHours ?? []),
    ].join("|");
    const draftKey = `${contextKey}|draft`;
    if (editingRange && resolvedDraftDay === date && lastAutoScrollContext.current !== draftKey) {
      const start = Math.max(0, Math.min(dayMinutes, draftStartMinute));
      scroller.scrollTop = scrollTopForMinute(start);
      lastAutoScrollContext.current = draftKey;
      return;
    }
    if (lastAutoScrollContext.current === contextKey || lastAutoScrollContext.current === draftKey) return;
    if (editing && !editingRange && !scheduleWindow) return;
    const start = openSegments[0]?.startMinute ?? 0;
    scroller.scrollTop = scrollTopForMinute(start);
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

  const draftSegment = activeRange ? clipRangeToDay(activeRange, date, timezone, dayMinutes) : undefined;
  const draftVisible = Boolean(draftSegment && draftSegment.endMinute > draftSegment.startMinute);
  const openDetails = (eventId: string, expanded: boolean) => {
    setExpandedEventId((current) => expandedEventIdAfterChange(current, eventId, expanded));
  };

  const renderScheduleStatus = () => {
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
    const messageKey =
      schedule.status === "no-target"
        ? "dayTimeline.vertical.noTarget"
        : schedule.status === "loading"
          ? "dayTimeline.vertical.loading"
          : events.length === 0
            ? "dayTimeline.vertical.empty"
            : null;
    return messageKey ? (
      <p role="status" className="rounded-sm bg-muted px-3 py-2 text-sm text-muted-foreground">
        {t(messageKey)}
      </p>
    ) : null;
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
  return (
    <section ref={setPortalContainer} className="min-w-0 text-foreground" aria-label={t("dayTimeline.vertical.title")}>
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
            <VerticalDayTimelineCanvas
              date={date}
              timezone={timezone}
              dayMinutes={dayMinutes}
              timeFormat={timeFormat}
              dateHeadingId={dateHeadingId}
              instructionsId={instructionsId}
              canvasRef={canvasRef}
              setScrollerRef={setScrollerRef}
              closedHours={closedHours}
              dayEvents={dayEvents}
              denseLanes={denseLanes}
              expandedEventId={expandedEventId}
              openDetails={openDetails}
              collisionBoundary={collisionBoundary}
              portalContainer={portalContainer}
              draftSegment={draftSegment}
              draftVisible={draftVisible}
              activeRange={activeRange}
              activePeriod={activePeriod}
              editingRange={editingRange}
              directAdjustmentAllowed={directAdjustmentAllowed}
              startInteraction={startInteraction}
              updateInteraction={updateInteraction}
              finishInteraction={finishInteraction}
              abortInteraction={abortInteraction}
              editing={editing}
              adjuster={adjuster}
              commitRange={commitRange}
              onDraftHandleKeyDown={onDraftHandleKeyDown}
            />
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
                    truncateTitle
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
      {adjuster && editing
        ? (["start", "end"] as const).map((edge) => {
            const reason = adjuster.visible[edge] ? resizeDisabledReason(edge, adjuster, editing.policy, t) : undefined;
            return reason ? (
              <p key={edge} id={`${instructionsId}-${edge}-reason`} className="sr-only">
                {reason}
              </p>
            ) : null;
          })
        : null}
    </section>
  );
}
