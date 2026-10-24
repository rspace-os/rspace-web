import * as React from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { formatMinuteWithDayOffset } from "@/modules/booking/components/DayTimelineEvent";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  type BookingWindowDraft,
  bookingDateTimeLocale,
  dayMinuteToZonedTime,
  instantToDayMinute,
  wallClockDraftFromInstants,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { cn } from "@/modules/common/utils/cn";
import { hiddenEdgeDescription, hiddenEdgeLabel, resizeDisabledReason } from "./timelineEditText";
import { type TimelineEdge, type TimelinePolicy, type TimelineRange, timelineAdjuster } from "./timelineRange";
import { resolveBookingWindow } from "./ZonedBookingWindowFields";

const STRIPES =
  "bg-[repeating-linear-gradient(135deg,var(--primary)_0_3px,color-mix(in_srgb,var(--primary)_15%,var(--background))_3px_7px)]";

function canvasFor(anchor: HTMLElement | null): HTMLElement | null {
  if (!anchor) return null;
  if (anchor.matches('[data-testid="day-timeline-canvas"]')) return anchor;
  return (
    anchor.closest<HTMLElement>('[data-testid="day-timeline-canvas"]') ??
    anchor.querySelector<HTMLElement>('[data-testid="day-timeline-canvas"]')
  );
}

/**
 * A draft's editable rectangle on a horizontal day timeline. Every pointer, keyboard and midnight action produces a
 * complete interval that satisfies the item's policy; an endpoint off the displayed day shows a striped continuation.
 */
export function TimelineWindowEditor({
  anchor,
  verticalAnchor,
  date,
  timezone,
  draft,
  policy,
  onChange,
  tone = "booking",
  testId = "timeline-window-editor",
  abovePopovers = onChange !== undefined,
}: {
  anchor: HTMLElement | null;
  verticalAnchor?: HTMLElement | null;
  date: string;
  timezone: string;
  draft: BookingWindowDraft;
  policy: TimelinePolicy;
  onChange?: (draft: BookingWindowDraft) => void;
  tone?: "booking" | "maintenance";
  testId?: string;
  abovePopovers?: boolean;
}) {
  const { t } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const descriptionId = React.useId();
  const canvas = canvasFor(anchor);
  const dayMinutes = zonedDayBounds(date, timezone).elapsedMinutes;
  const resolved = resolveBookingWindow(draft, timezone).window;
  const policyKey = JSON.stringify(policy);
  const adjuster = React.useMemo(
    () => (resolved ? timelineAdjuster(resolved, { date, displayTimezone: timezone, policy }) : undefined),
    [resolved?.start, resolved?.end, date, timezone, policyKey],
  );
  const [preview, setPreview] = React.useState<TimelineRange | null>(null);
  const interaction = React.useRef<{
    edge: TimelineEdge;
    pointerId: number;
    originMinute: number;
    startMinute: number;
  } | null>(null);
  const activeRange = preview ?? resolved;

  if (!canvas || !activeRange || !resolved || !adjuster) return null;

  const minuteOf = (instant: string) => instantToDayMinute(instant, date, timezone);
  const instantAt = (minute: number) => dayMinuteToZonedTime(date, timezone, minute).toInstant().toString();
  const pointerMinute = (clientX: number) => {
    const bounds = canvas.getBoundingClientRect();
    return Math.min(dayMinutes, Math.max(0, ((clientX - bounds.left) / bounds.width) * dayMinutes));
  };
  const adjustedAt = (clientX: number): TimelineRange => {
    const active = interaction.current;
    if (!active) return activeRange;
    const minute = pointerMinute(clientX);
    // A move follows the pointer's delta, so a start before the displayed day is not pulled into it.
    const target =
      active.edge === "move" ? instantAt(active.startMinute + minute - active.originMinute) : instantAt(minute);
    return adjuster.adjust(active.edge, target) ?? resolved;
  };
  const commit = (next: TimelineRange) => {
    if (next.start !== resolved.start || next.end !== resolved.end)
      onChange?.(wallClockDraftFromInstants(next.start, next.end, timezone));
  };
  const resizeReason = (edge: "start" | "end") => resizeDisabledReason(edge, adjuster, policy, t);
  const begin = (edge: TimelineEdge, event: React.PointerEvent<HTMLButtonElement>) => {
    if (!onChange) return;
    event.preventDefault();
    event.stopPropagation();
    if (edge !== "move" && resizeReason(edge)) return;
    if (event.nativeEvent.isTrusted) event.currentTarget.setPointerCapture(event.pointerId);
    interaction.current = {
      edge,
      pointerId: event.pointerId,
      originMinute: pointerMinute(event.clientX),
      startMinute: minuteOf(resolved.start),
    };
    setPreview(resolved);
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
    commit(next);
  };
  const cancel = () => {
    interaction.current = null;
    setPreview(null);
  };
  const changeByKeyboard = (edge: TimelineEdge, event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (!onChange || (event.key !== "ArrowLeft" && event.key !== "ArrowRight")) return;
    event.preventDefault();
    event.stopPropagation();
    const next = adjuster.step(edge, event.key === "ArrowLeft" ? "previous" : "next");
    if (next) commit(next);
  };
  const locale = bookingDateTimeLocale(timeFormat);
  // Double-click or Enter/Space applies; a single click only focuses, so a stray click never changes the draft.
  const hiddenEdgeHandle = (edge: "start" | "end") => {
    const action = adjuster.hiddenEdge(edge);
    if (!action) return null;
    const apply = () => {
      if (action.kind === "enabled") commit(action.range);
    };
    const id = `${descriptionId}-${edge}-continuation`;
    const description = hiddenEdgeDescription(edge, action, date, timezone, t, locale);
    return (
      <>
        <button
          type="button"
          data-testid={`${testId}-${edge}-continuation`}
          aria-label={hiddenEdgeLabel(edge, date, t, locale)}
          aria-describedby={id}
          aria-disabled={action.kind === "disabled" || undefined}
          title={description}
          className={cn(
            "absolute inset-y-0 w-4 touch-none focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring",
            STRIPES,
            edge === "start" ? "left-0" : "right-0",
            action.kind === "enabled" ? "cursor-pointer" : "cursor-not-allowed opacity-70",
          )}
          onPointerDown={(event) => event.stopPropagation()}
          onDoubleClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            apply();
          }}
          onKeyDown={(event) => {
            if (event.key !== "Enter" && event.key !== " ") return;
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) apply();
          }}
          onKeyUp={(event) => {
            if (event.key === " ") event.preventDefault();
          }}
        />
        <span id={id} className="sr-only">
          {description}
        </span>
      </>
    );
  };
  const resizeHandle = (edge: "start" | "end") => {
    const reason = resizeReason(edge);
    const id = `${descriptionId}-${edge}-resize`;
    return (
      <>
        <button
          type="button"
          aria-label={t(edge === "start" ? "calendar.windowEditor.start" : "calendar.windowEditor.end")}
          aria-disabled={reason ? true : undefined}
          aria-describedby={reason ? id : undefined}
          title={reason}
          className={cn(
            "absolute inset-y-0 w-3 touch-none border-primary bg-background/80",
            edge === "start" ? "left-0 border-r" : "right-0 border-l",
            reason ? "cursor-not-allowed" : "cursor-ew-resize",
          )}
          onPointerDown={(event) => begin(edge, event)}
          onPointerMove={move}
          onPointerUp={finish}
          onPointerCancel={cancel}
          onKeyDown={(event) => changeByKeyboard(edge, event)}
        />
        {reason ? (
          <span id={id} className="sr-only">
            {reason}
          </span>
        ) : null}
      </>
    );
  };

  const startMinute = minuteOf(activeRange.start);
  const endMinute = minuteOf(activeRange.end);
  const visibleStart = Math.max(0, startMinute);
  const visibleEnd = Math.min(dayMinutes, endMinute);
  if (visibleEnd <= visibleStart) return null;
  const verticalStyle = verticalAnchor
    ? { top: verticalAnchor.offsetTop, height: verticalAnchor.offsetHeight }
    : { top: 32, bottom: 0 };
  const period = `${formatMinuteWithDayOffset(date, timezone, startMinute, timeFormat)}–${formatMinuteWithDayOffset(date, timezone, endMinute, timeFormat)}`;

  return createPortal(
    <div
      data-timeline-window-editor
      data-testid={testId}
      className={cn(
        "absolute rounded-sm border-2 ring-3",
        abovePopovers ? "z-[60]" : "z-40",
        onChange ? "pointer-events-auto" : "pointer-events-none",
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
      <span className="pointer-events-none absolute top-1 left-5 z-10 rounded-sm bg-primary px-1 text-[10px] font-semibold whitespace-nowrap text-primary-foreground">
        {period}
      </span>
      {onChange ? (
        <>
          <button
            type="button"
            aria-label={t("calendar.windowEditor.move")}
            className="absolute inset-y-0 right-4 left-4 cursor-grab touch-none"
            onPointerDown={(event) => begin("move", event)}
            onPointerMove={move}
            onPointerUp={finish}
            onPointerCancel={cancel}
            onKeyDown={(event) => changeByKeyboard("move", event)}
          />
          {adjuster.visible.start ? resizeHandle("start") : hiddenEdgeHandle("start")}
          {adjuster.visible.end ? resizeHandle("end") : hiddenEdgeHandle("end")}
        </>
      ) : null}
    </div>,
    canvas,
  );
}
