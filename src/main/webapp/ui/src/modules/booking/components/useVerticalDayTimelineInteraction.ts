import { Temporal } from "@js-temporal/polyfill";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { timelineAdjuster } from "@/modules/booking/creation/timelineRange";
import { resolveBookingWindow } from "@/modules/booking/creation/ZonedBookingWindowFields";
import {
  type BookingTimeFormat,
  type BookingWindowDraft,
  dayMinuteToZonedTime,
  instantToDayMinute,
  wallClockDraftFromInstants,
} from "@/modules/booking/domain/bookingTime";
import { period } from "./DayTimelineEvent";
import type { VerticalDayTimelineEditing, VerticalDayTimelineSchedule } from "./VerticalDayTimeline";

export type IsoRange = { start: string; end: string };
export type AdjustmentEdge = "move" | "start" | "end";
type Direction = "previous" | "next";
function resolveRange(draft: BookingWindowDraft | undefined, timezone: string): IsoRange | undefined {
  if (!draft) return undefined;
  return resolveBookingWindow(draft, timezone).window;
}

export function timeRange(
  range: IsoRange,
  date: string,
  timezone: string,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): string {
  return period(
    {
      startMinute: instantToDayMinute(range.start, date, timezone),
      endMinute: instantToDayMinute(range.end, date, timezone),
    },
    date,
    timezone,
    "–",
    timeFormat,
  );
}

function rangesEqual(left: IsoRange, right: IsoRange): boolean {
  return Temporal.Instant.compare(left.start, right.start) === 0 && Temporal.Instant.compare(left.end, right.end) === 0;
}

export function useVerticalDayTimelineInteraction({
  date,
  timezone,
  dayMinutes,
  timeFormat,
  editing,
  schedule,
  setExpandedEventId,
}: {
  date: string;
  timezone: string;
  dayMinutes: number;
  timeFormat: BookingTimeFormat;
  editing: VerticalDayTimelineEditing | undefined;
  schedule: Pick<VerticalDayTimelineSchedule, "status">;
  setExpandedEventId: React.Dispatch<React.SetStateAction<string | null>>;
}) {
  const { t } = useTranslation("booking");
  const canvasRef = React.useRef<HTMLDivElement>(null);
  const [canvasWidth, setCanvasWidth] = React.useState(0);
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
  const editingRange = React.useMemo(() => resolveRange(editing?.draft, timezone), [editing?.draft, timezone]);
  const activeRange = preview ?? editingRange;
  const activePeriod = activeRange ? timeRange(activeRange, date, timezone, timeFormat) : undefined;
  const policyKey = editing ? JSON.stringify(editing.policy) : "";
  const adjuster = React.useMemo(
    () =>
      editing && editingRange && !editing.disabledReason
        ? timelineAdjuster(editingRange, { date, displayTimezone: timezone, policy: editing.policy })
        : undefined,
    [editingRange, date, timezone, policyKey, editing?.disabledReason],
  );
  const directAdjustmentAllowed = Boolean(adjuster);
  const gestureContextKey = [
    date,
    timezone,
    editing?.targetKey ?? "",
    editingRange?.start ?? "",
    editingRange?.end ?? "",
    policyKey,
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
  const adjustInteractionRange = React.useCallback(
    (
      active: NonNullable<typeof interactionRef.current>,
      clientY: number,
      activeAdjuster: NonNullable<typeof adjuster>,
    ) => {
      const minute = minuteAt(clientY) - active.pointerOffsetMinutes;
      const target = rangeAtMinute(minute);
      return activeAdjuster.adjust(active.edge, target);
    },
    [minuteAt, rangeAtMinute],
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
  const abortInteraction = React.useCallback(
    (event: React.PointerEvent<HTMLButtonElement>) => {
      if (interactionRef.current?.pointerId === event.pointerId) cancelInteraction();
    },
    [cancelInteraction],
  );

  const commitRange = React.useCallback(
    (next: IsoRange) => {
      if (!editing || !editingRange || rangesEqual(next, editingRange)) return;
      editing.onChange(wallClockDraftFromInstants(next.start, next.end, timezone));
      setAnnouncement(
        t("dayTimeline.vertical.intervalChanged", {
          period: timeRange(next, date, timezone, timeFormat),
        }),
      );
    },
    [date, editing, editingRange, t, timeFormat, timezone],
  );

  const changeByStep = React.useCallback(
    (edge: AdjustmentEdge, direction: Direction) => {
      if (!editing || !adjuster || interactionRef.current) return;
      const next = adjuster.step(edge, direction);
      if (next) commitRange(next);
    },
    [adjuster, commitRange, editing],
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
    if (interactionRef.current || !adjuster || !editingRange || !event.isPrimary) return;
    if (edge !== "move" && !adjuster.canResize(edge)) return;
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
    if (!active || active.pointerId !== event.pointerId || !adjuster) return;
    event.preventDefault();
    event.stopPropagation();
    const next = adjustInteractionRange(active, event.clientY, adjuster);
    setPreview(next ?? active.originalRange);
  };

  const finishInteraction = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = interactionRef.current;
    if (!active || active.pointerId !== event.pointerId || !adjuster) return;
    event.preventDefault();
    event.stopPropagation();
    const next = adjustInteractionRange(active, event.clientY, adjuster);
    interactionRef.current = null;
    setGestureActive(false);
    setPreview(null);
    interactionCallbackRef.current?.(false);
    if (active.handle.hasPointerCapture(active.pointerId)) active.handle.releasePointerCapture(active.pointerId);
    if (next) commitRange(next);
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

  return {
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
  };
}
