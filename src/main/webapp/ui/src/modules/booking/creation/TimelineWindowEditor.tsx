import * as React from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import {
  type BookingWindowDraft,
  dayMinuteToZonedTime,
  instantToDayMinute,
  wallClockDraftFromInstants,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { cn } from "@/modules/common/utils/cn";
import { resolveBookingWindow } from "./ZonedBookingWindowFields";

type TimelineRange = { startMinute: number; endMinute: number };
type AdjustmentMode = "move" | "start" | "end";

function canvasFor(anchor: HTMLElement | null): HTMLElement | null {
  if (!anchor) return null;
  if (anchor.matches('[data-testid="day-timeline-canvas"]')) return anchor;
  return (
    anchor.closest<HTMLElement>('[data-testid="day-timeline-canvas"]') ??
    anchor.querySelector<HTMLElement>('[data-testid="day-timeline-canvas"]')
  );
}

export function adjustTimelineRange(
  range: TimelineRange,
  mode: AdjustmentMode,
  deltaOrMinute: number,
  dayMinutes: number,
  increment: number,
): TimelineRange {
  if (mode === "move") {
    const duration = Math.min(dayMinutes, Math.max(increment, range.endMinute - range.startMinute));
    const startMinute = Math.min(dayMinutes - duration, Math.max(0, range.startMinute + deltaOrMinute));
    return { startMinute, endMinute: startMinute + duration };
  }
  if (mode === "start") {
    return {
      startMinute: Math.min(Math.max(0, deltaOrMinute), range.endMinute - increment),
      endMinute: range.endMinute,
    };
  }
  return {
    startMinute: range.startMinute,
    endMinute: Math.max(range.startMinute + increment, Math.min(dayMinutes, deltaOrMinute)),
  };
}

export function TimelineWindowEditor({
  anchor,
  verticalAnchor,
  date,
  timezone,
  draft,
  snapIncrementMinutes,
  onChange,
  tone = "booking",
  testId = "timeline-window-editor",
}: {
  anchor: HTMLElement | null;
  verticalAnchor?: HTMLElement | null;
  date: string;
  timezone: string;
  draft: BookingWindowDraft;
  snapIncrementMinutes: number;
  onChange?: (draft: BookingWindowDraft) => void;
  tone?: "booking" | "maintenance";
  testId?: string;
}) {
  const { t } = useTranslation("booking");
  const canvas = canvasFor(anchor);
  const dayMinutes = zonedDayBounds(date, timezone).elapsedMinutes;
  const resolved = resolveBookingWindow(draft, timezone).window;
  const range = resolved
    ? {
        startMinute: instantToDayMinute(resolved.start, date, timezone),
        endMinute: instantToDayMinute(resolved.end, date, timezone),
      }
    : undefined;
  const [preview, setPreview] = React.useState<TimelineRange | null>(null);
  const interaction = React.useRef<{
    mode: AdjustmentMode;
    pointerId: number;
    originMinute: number;
    range: TimelineRange;
  } | null>(null);
  const activeRange = preview ?? range;

  if (!canvas || !activeRange || activeRange.endMinute <= activeRange.startMinute) return null;

  const snapMinute = (minute: number) =>
    Math.min(dayMinutes, Math.max(0, Math.round(minute / snapIncrementMinutes) * snapIncrementMinutes));
  const minuteAt = (clientX: number) => {
    const bounds = canvas.getBoundingClientRect();
    return snapMinute(((clientX - bounds.left) / bounds.width) * dayMinutes);
  };
  const changedDraft = (next: TimelineRange) =>
    wallClockDraftFromInstants(
      dayMinuteToZonedTime(date, timezone, next.startMinute).toInstant().toString(),
      dayMinuteToZonedTime(date, timezone, next.endMinute).toInstant().toString(),
      timezone,
    );
  const adjustedAt = (clientX: number) => {
    const active = interaction.current;
    if (!active) return activeRange;
    const minute = minuteAt(clientX);
    return adjustTimelineRange(
      active.range,
      active.mode,
      active.mode === "move" ? minute - active.originMinute : minute,
      dayMinutes,
      snapIncrementMinutes,
    );
  };
  const begin = (mode: AdjustmentMode, event: React.PointerEvent<HTMLButtonElement>) => {
    if (!onChange) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.nativeEvent.isTrusted) event.currentTarget.setPointerCapture(event.pointerId);
    interaction.current = {
      mode,
      pointerId: event.pointerId,
      originMinute: minuteAt(event.clientX),
      range: activeRange,
    };
    setPreview(activeRange);
  };
  const move = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (interaction.current?.pointerId !== event.pointerId) return;
    event.preventDefault();
    event.stopPropagation();
    setPreview(adjustedAt(event.clientX));
  };
  const finish = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (interaction.current?.pointerId !== event.pointerId) return;
    event.preventDefault();
    event.stopPropagation();
    const next = adjustedAt(event.clientX);
    interaction.current = null;
    setPreview(null);
    onChange?.(changedDraft(next));
  };
  const changeByKeyboard = (mode: AdjustmentMode, event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (!onChange || (event.key !== "ArrowLeft" && event.key !== "ArrowRight")) return;
    event.preventDefault();
    event.stopPropagation();
    const delta = event.key === "ArrowLeft" ? -snapIncrementMinutes : snapIncrementMinutes;
    const next = adjustTimelineRange(
      activeRange,
      mode,
      mode === "move" ? delta : mode === "start" ? activeRange.startMinute + delta : activeRange.endMinute + delta,
      dayMinutes,
      snapIncrementMinutes,
    );
    onChange(changedDraft(next));
  };
  const visibleStart = Math.max(0, activeRange.startMinute);
  const visibleEnd = Math.min(dayMinutes, activeRange.endMinute);
  if (visibleEnd <= visibleStart) return null;
  const verticalStyle = verticalAnchor
    ? { top: verticalAnchor.offsetTop, height: verticalAnchor.offsetHeight }
    : { top: 32, bottom: 0 };
  const period = `${dayMinuteToZonedTime(date, timezone, activeRange.startMinute)
    .toPlainTime()
    .toString({ smallestUnit: "minute" })}–${dayMinuteToZonedTime(date, timezone, activeRange.endMinute)
    .toPlainTime()
    .toString({ smallestUnit: "minute" })}`;

  return createPortal(
    <div
      data-timeline-window-editor
      data-testid={testId}
      className={cn(
        "absolute rounded-sm border-2 ring-3",
        onChange ? "pointer-events-auto z-[60]" : "pointer-events-none z-40",
        tone === "maintenance"
          ? "border-amber-600 bg-amber-200/60 ring-amber-500/20"
          : "border-primary bg-primary/25 ring-ring/40",
      )}
      style={{
        ...verticalStyle,
        left: `${(visibleStart / dayMinutes) * 100}%`,
        width: `${((visibleEnd - visibleStart) / dayMinutes) * 100}%`,
      }}
    >
      <span className="pointer-events-none absolute top-1 left-1 z-10 rounded-sm bg-primary px-1 text-[10px] font-semibold whitespace-nowrap text-primary-foreground">
        {period}
      </span>
      {onChange ? (
        <>
          <button
            type="button"
            aria-label={t("calendar.windowEditor.move")}
            className="absolute inset-y-0 right-3 left-3 cursor-grab touch-none"
            onPointerDown={(event) => begin("move", event)}
            onPointerMove={move}
            onPointerUp={finish}
            onPointerCancel={() => {
              interaction.current = null;
              setPreview(null);
            }}
            onKeyDown={(event) => changeByKeyboard("move", event)}
          />
          <button
            type="button"
            aria-label={t("calendar.windowEditor.start")}
            className="absolute inset-y-0 left-0 w-3 cursor-ew-resize touch-none border-primary border-r bg-background/80"
            onPointerDown={(event) => begin("start", event)}
            onPointerMove={move}
            onPointerUp={finish}
            onPointerCancel={() => {
              interaction.current = null;
              setPreview(null);
            }}
            onKeyDown={(event) => changeByKeyboard("start", event)}
          />
          <button
            type="button"
            aria-label={t("calendar.windowEditor.end")}
            className="absolute inset-y-0 right-0 w-3 cursor-ew-resize touch-none border-primary border-l bg-background/80"
            onPointerDown={(event) => begin("end", event)}
            onPointerMove={move}
            onPointerUp={finish}
            onPointerCancel={() => {
              interaction.current = null;
              setPreview(null);
            }}
            onKeyDown={(event) => changeByKeyboard("end", event)}
          />
        </>
      ) : null}
    </div>,
    canvas,
  );
}
