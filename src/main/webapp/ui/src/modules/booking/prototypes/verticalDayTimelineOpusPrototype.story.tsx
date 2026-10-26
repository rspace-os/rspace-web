// PROTOTYPE ONLY (Opus 5.5). Throwaway shared code for the vertical day timeline stories; do not import from production.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Temporal } from "@js-temporal/polyfill";
import {
  CheckIcon,
  ChevronDownIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ChevronUpIcon,
  CrosshairIcon,
  InfoIcon,
  TriangleAlertIcon,
} from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { formatMinuteWithDayOffset } from "@/modules/booking/components/DayTimelineEvent";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { resolveBookingWindow } from "@/modules/booking/creation/ZonedBookingWindowFields";
import type { BookingConflict } from "@/modules/booking/domain/availability";
import {
  addCalendarDays,
  type BookingWindowDraft,
  dayMinuteToZonedTime,
  isPlainDate,
  wallClockDraftFromInstants,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import { Alert, AlertAction, AlertDescription } from "@/modules/common/ui/alert";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup, ButtonGroupText } from "@/modules/common/ui/button-group";
import { cn } from "@/modules/common/utils/cn";
import {
  DayCanvasView,
  EventListView,
  GUTTER_PX,
  MIN_LANE_PX,
  MIN_TARGET_PX,
} from "./VerticalDayTimelineOpusViews.story";
import type { DayQueryFixture, FixtureEvent } from "./verticalDayTimelineOpusFixtures";
import {
  type AdjustMode,
  adjustRange,
  type EpochRange,
  formatInterval,
  instantMinute,
  minuteInstant,
  openingRanges,
  placeInLanes,
  sameRange,
  slotGrid,
} from "./verticalDayTimelineOpusModel";

// ---------------------------------------------------------------------------
// Simulated availability policy (prototype stand-in for the shared draft-availability hook)
// ---------------------------------------------------------------------------

export type AvailabilityFixture = "ok" | "pending" | "failed";
export type DraftStatus = { tone: "neutral" | "success" | "warning" | "error"; text: string };

/** Buffers pad existing bookings; maintenance always blocks; allowed double booking is a warning. */
export function draftConflicts(
  window: { start: string; end: string } | undefined,
  events: readonly FixtureEvent[],
  item: BookableItemOption,
  displayTimezone: string,
  excludedBookingId?: number,
) {
  if (!window) return { conflicts: [] as BookingConflict[], blocking: false, severity: "warning" as const };
  const start = instantMinute(window.start);
  const end = instantMinute(window.end);
  const conflicts: BookingConflict[] = [];
  let blocking = false;
  for (const event of events) {
    if (event.id === excludedBookingId) continue;
    const before = event.kind === "BOOKING" ? item.bufferBeforeMinutes : 0;
    const after = event.kind === "BOOKING" ? item.bufferAfterMinutes : 0;
    if (!(start < instantMinute(event.end) + after && end > instantMinute(event.start) - before)) continue;
    if (event.kind === "MAINTENANCE" || !item.allowDoubleBooking) blocking = true;
    conflicts.push({
      id: event.id,
      kind: event.kind,
      privacy: event.privacy,
      purpose: event.privacy === "full" ? (event.purpose ?? null) : null,
      bookedBy: event.privacy === "full" ? event.bookedBy : null,
      start: event.start,
      end: event.end,
      timezone: displayTimezone,
      instrumentTimeZone: item.timezone,
    });
  }
  return { conflicts, blocking, severity: blocking ? ("error" as const) : ("warning" as const) };
}

export function draftStatus({
  resolved,
  availability,
  conflictCount,
  blocking,
}: {
  resolved: boolean;
  availability: AvailabilityFixture;
  conflictCount: number;
  blocking: boolean;
}): DraftStatus {
  if (!resolved) return { tone: "neutral", text: "Not checked · see the form fields" };
  if (availability === "pending") return { tone: "neutral", text: "Checking availability…" };
  if (availability === "failed") return { tone: "warning", text: "Availability not verified" };
  if (conflictCount === 0) return { tone: "success", text: "No conflicts found" };
  return blocking
    ? { tone: "error", text: `Conflicts with ${conflictCount} ${conflictCount === 1 ? "event" : "events"}` }
    : { tone: "warning", text: `Overlaps ${conflictCount} · warning only` };
}

/** Simulates the browsed-day query. Retry resolves to "ready" after a short delay. */
export function useSimulatedDayQuery(initial: DayQueryFixture) {
  const [state, setState] = React.useState(initial);
  const timer = React.useRef<number | undefined>(undefined);
  React.useEffect(() => () => window.clearTimeout(timer.current), []);
  const retry = React.useCallback(() => {
    setState("loading");
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setState("ready"), 800);
  }, []);
  return [state, setState, retry] as const;
}

// ---------------------------------------------------------------------------
// Prototype-only fixture controls (visually distinct from proposed production UI)
// ---------------------------------------------------------------------------

export function PrototypeControls({ children }: { children: React.ReactNode }) {
  return (
    <fieldset className="min-w-0 rounded-sm border-2 border-dashed border-border bg-muted/25 p-3 text-xs text-foreground">
      <legend className="px-1 font-semibold">Prototype fixtures · simulated, not proposed UI</legend>
      <div className="flex flex-wrap items-end gap-3">{children}</div>
    </fieldset>
  );
}

export function FixtureSelect<T extends string | number>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: readonly { value: T; label: string }[];
  onChange: (value: T) => void;
}) {
  const id = React.useId();
  return (
    <div className="flex min-w-0 max-w-full flex-col gap-1">
      <label htmlFor={id} className="font-medium">
        {label}
      </label>
      <select
        id={id}
        className="h-8 max-w-full rounded-sm border bg-background px-2 text-sm"
        value={String(value)}
        onChange={(event) => {
          const next = options.find((option) => String(option.value) === event.currentTarget.value);
          if (next) onChange(next.value);
        }}
      >
        {options.map((option) => (
          <option key={String(option.value)} value={String(option.value)}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}

export function DraftReadout({ draft }: { draft: BookingWindowDraft }) {
  const endpoint = (date: string, time: string, occurrence?: string) =>
    `${date || "—"} ${time || "—"}${occurrence ? ` (${occurrence})` : ""}`;
  return (
    <span className="font-mono tabular-nums">
      {endpoint(draft.startDate, draft.startTime, draft.startOccurrence)} →{" "}
      {endpoint(draft.endDate, draft.endTime, draft.endOccurrence)}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Vertical day timeline (proposed production UI)
// ---------------------------------------------------------------------------

const MODE_LABEL: Record<AdjustMode, string> = { move: "Move", start: "Start", end: "End" };

function StatusLine({ status }: { status: DraftStatus }) {
  const Icon = status.tone === "success" ? CheckIcon : status.tone === "neutral" ? InfoIcon : TriangleAlertIcon;
  return (
    <p
      className={cn(
        "flex items-center gap-1.5 text-xs font-medium",
        status.tone === "neutral" && "text-muted-foreground",
        status.tone === "success" && "text-emerald-700 dark:text-emerald-300",
        status.tone === "warning" && "text-amber-800 dark:text-amber-300",
        status.tone === "error" && "text-destructive",
      )}
    >
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      {status.text}
    </p>
  );
}

type Gesture = { mode: AdjustMode; pointerId: number; grabOffset: number; base: EpochRange; element: HTMLElement };

export function VerticalDayTimeline({
  item,
  displayTimezone,
  events,
  fallbackDate,
  originalBookingId,
  draft,
  awaitingAcknowledgement = false,
  disabled = false,
  query,
  onRetry,
  status,
  hourHeight,
  layout,
  onCommit,
}: {
  item: BookableItemOption;
  displayTimezone: string;
  events: readonly FixtureEvent[];
  fallbackDate: string;
  originalBookingId?: number;
  /** The committed draft: a pending adjustment if one awaits acknowledgement, else the form's draft. */
  draft: BookingWindowDraft;
  awaitingAcknowledgement?: boolean;
  disabled?: boolean;
  query: DayQueryFixture;
  onRetry: () => void;
  status: DraftStatus;
  hourHeight: number;
  layout: "sticky" | "inline";
  onCommit: (draft: BookingWindowDraft) => void;
}) {
  const { i18n } = useTranslation("booking");
  const locale = i18n.language;
  const headingId = React.useId();
  const hintId = React.useId();
  const timezone = displayTimezone;
  const draftDay = isPlainDate(draft.startDate) ? draft.startDate : undefined;
  const [view, setView] = React.useState<"day" | "list">("day");
  const [browseDate, setBrowseDate] = React.useState(draftDay ?? fallbackDate);
  // An explicit form date change follows the new draft start day; browsing never alters the draft.
  const [followedDay, setFollowedDay] = React.useState(draftDay);
  if (draftDay !== followedDay) {
    setFollowedDay(draftDay);
    if (draftDay) setBrowseDate(draftDay);
  }
  const [announcement, setAnnouncement] = React.useState("");
  const [canvas, setCanvas] = React.useState<HTMLDivElement | null>(null);
  const [canvasWidth, setCanvasWidth] = React.useState(0);
  const scrollRef = React.useRef<HTMLElement>(null);
  const [preview, setPreview] = React.useState<EpochRange>();
  const previewRef = React.useRef<EpochRange | undefined>(undefined);
  const gesture = React.useRef<Gesture | null>(null);

  const grid = React.useMemo(
    () => slotGrid(browseDate, timezone, item.timezone, item.slotGranularityMinutes),
    [browseDate, timezone, item.timezone, item.slotGranularityMinutes],
  );
  const elapsed = grid.dayEnd - grid.dayStart;
  const pxPerMinute = hourHeight / 60;
  // Resolve instants separately from policy so an out-of-hours draft stays visible.
  const resolved = React.useMemo(() => {
    const window = resolveBookingWindow(draft, timezone).window;
    return window ? { start: instantMinute(window.start), end: instantMinute(window.end) } : undefined;
  }, [draft, timezone]);

  const blocker = disabled
    ? "Saving…"
    : !resolved
      ? "Complete the start and end in the form to place the draft."
      : browseDate !== draftDay
        ? "Viewing another day."
        : resolved.end > grid.dayEnd
          ? "Spans midnight. Adjust it in the date and time fields."
          : !grid.valid.has(resolved.start) || !grid.valid.has(resolved.end)
            ? `Off the ${item.slotGranularityMinutes}-minute grid. Adjust it in the time fields.`
            : undefined;
  const adjustable = blocker === undefined && resolved !== undefined;

  const cancelGesture = React.useCallback(() => {
    const active = gesture.current;
    if (!active) return;
    gesture.current = null;
    if (active.element.hasPointerCapture(active.pointerId)) active.element.releasePointerCapture(active.pointerId);
    previewRef.current = undefined;
    setPreview(undefined);
  }, []);

  // Day, timezone, view, submission or an external draft change invalidates any preview.
  const contextKey = `${browseDate}|${timezone}|${view}|${disabled}|${JSON.stringify(draft)}`;
  const lastContextKey = React.useRef(contextKey);
  React.useEffect(() => {
    if (lastContextKey.current === contextKey) return;
    lastContextKey.current = contextKey;
    cancelGesture();
  }, [contextKey, cancelGesture]);

  React.useEffect(() => {
    if (!preview) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      cancelGesture();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [preview, cancelGesture]);

  // Measure lanes, and cancel rather than commit against an obsolete geometry.
  React.useEffect(() => {
    if (!canvas) return;
    let last = { width: canvas.clientWidth, height: canvas.clientHeight };
    setCanvasWidth(last.width);
    const observer = new ResizeObserver(() => {
      const next = { width: canvas.clientWidth, height: canvas.clientHeight };
      if (next.width === last.width && next.height === last.height) return;
      last = next;
      setCanvasWidth(next.width);
      cancelGesture();
    });
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [canvas, cancelGesture]);

  const opening = React.useMemo(() => openingRanges(grid, item), [grid, item]);
  const scrollAnchor =
    resolved && resolved.end > grid.dayStart && resolved.start < grid.dayEnd
      ? Math.max(resolved.start, grid.dayStart)
      : (opening.find((range) => range.end > grid.dayStart)?.start ?? grid.dayStart);
  // Scroll once per browsed day and view, never on refresh or draft changes.
  const scrolledFor = React.useRef("");
  React.useLayoutEffect(() => {
    const key = `${browseDate}|${timezone}|${view}`;
    if (!scrollRef.current || scrolledFor.current === key) return;
    scrolledFor.current = key;
    scrollRef.current.scrollTop = Math.max(0, (scrollAnchor - grid.dayStart) * pxPerMinute - 32);
  });

  const commit = (range: EpochRange) => {
    if (!resolved || sameRange(range, resolved)) return;
    const startInstant = minuteInstant(range.start);
    const endInstant = minuteInstant(range.end);
    onCommit(wallClockDraftFromInstants(startInstant, endInstant, timezone));
    setAnnouncement(`Draft ${formatInterval(startInstant, endInstant, timezone, locale)}`);
  };
  const step = (mode: AdjustMode, direction: -1 | 1) => {
    if (!adjustable || !resolved) return;
    commit(adjustRange(resolved, mode, grid, { kind: "step", direction }));
  };
  const canStep = (mode: AdjustMode, direction: -1 | 1) =>
    adjustable &&
    resolved !== undefined &&
    !sameRange(adjustRange(resolved, mode, grid, { kind: "step", direction }), resolved);

  const pointerMinute = (clientY: number) =>
    grid.dayStart + (clientY - (canvas?.getBoundingClientRect().top ?? 0)) / pxPerMinute;
  const beginGesture = (mode: AdjustMode) => (event: React.PointerEvent<HTMLButtonElement>) => {
    // Only the active primary pointer and the left mouse button start a gesture.
    if (!adjustable || !resolved || gesture.current || !event.isPrimary || event.button !== 0 || !canvas) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    const anchor = mode === "end" ? resolved.end : resolved.start;
    gesture.current = {
      mode,
      pointerId: event.pointerId,
      grabOffset: pointerMinute(event.clientY) - anchor,
      base: resolved,
      element: event.currentTarget,
    };
    previewRef.current = resolved;
    setPreview(resolved);
  };
  const trackGesture = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = gesture.current;
    if (!active || active.pointerId !== event.pointerId) return;
    const target = pointerMinute(event.clientY) - active.grabOffset;
    const next = adjustRange(active.base, active.mode, grid, { kind: "pointer", target });
    if (sameRange(next, previewRef.current)) return;
    previewRef.current = next;
    setPreview(next);
  };
  const finishGesture = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = gesture.current;
    if (!active || active.pointerId !== event.pointerId) return;
    const final = previewRef.current;
    gesture.current = null;
    previewRef.current = undefined;
    setPreview(undefined);
    if (active.element.hasPointerCapture(event.pointerId)) active.element.releasePointerCapture(event.pointerId);
    if (final) commit(final); // Commit once per gesture; no-ops are ignored.
  };
  const abortGesture = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (gesture.current?.pointerId === event.pointerId) cancelGesture();
  };
  const pointerHandlers = (mode: AdjustMode) => ({
    onPointerDown: beginGesture(mode),
    onPointerMove: trackGesture,
    onPointerUp: finishGesture,
    onPointerCancel: abortGesture,
    onLostPointerCapture: abortGesture,
  });

  const segment = (range: EpochRange) => {
    const top = Math.max(range.start, grid.dayStart) - grid.dayStart;
    const bottom = Math.min(range.end, grid.dayEnd) - grid.dayStart;
    return bottom > top ? { top: top * pxPerMinute, height: (bottom - top) * pxPerMinute } : undefined;
  };

  const loaded = query === "ready";
  const original = events.find((event) => event.id === originalBookingId);
  const dayEvents = loaded
    ? events.filter(
        (event) =>
          event.id !== originalBookingId &&
          instantMinute(event.start) < grid.dayEnd &&
          instantMinute(event.end) > grid.dayStart,
      )
    : [];
  const placed = placeInLanes(
    dayEvents.map((event) => ({
      item: { id: String(event.id), fixture: event },
      start: Math.max(instantMinute(event.start), grid.dayStart),
      end: Math.min(instantMinute(event.end), grid.dayEnd),
    })),
  );
  const areaWidth = canvasWidth - GUTTER_PX;
  const denseClusters = new Set(
    placed.filter((entry) => canvasWidth > 0 && areaWidth / entry.lanes < MIN_LANE_PX).map((entry) => entry.cluster),
  );
  const isMark = (entry: (typeof placed)[number]) =>
    denseClusters.has(entry.cluster) || (entry.end - entry.start) * pxPerMinute < MIN_TARGET_PX;
  const markedCount = placed.filter(isMark).length;
  const laneStyle = (lane: number, lanes: number): React.CSSProperties => ({
    left: `calc(${GUTTER_PX}px + (100% - ${GUTTER_PX}px) * ${lane / lanes})`,
    width: `calc((100% - ${GUTTER_PX}px) / ${lanes} - 4px)`,
  });

  const shown = preview ?? resolved;
  const draftSegment = shown ? segment(shown) : undefined;
  const originalSegment = original
    ? segment({ start: instantMinute(original.start), end: instantMinute(original.end) })
    : undefined;
  const committedHeight = resolved ? (resolved.end - resolved.start) * pxPerMinute : 0;
  const handles = !adjustable
    ? "none"
    : committedHeight >= 3 * MIN_TARGET_PX
      ? "full"
      : committedHeight >= MIN_TARGET_PX
        ? "move"
        : "none";
  const shownStatus: DraftStatus = preview
    ? { tone: "neutral", text: "Adjusting · release to check" }
    : awaitingAcknowledgement
      ? { tone: "neutral", text: "Updating form…" }
      : status;
  const minuteLabel = (minute: number) => formatMinuteWithDayOffset(browseDate, timezone, minute - grid.dayStart);

  const bounds = zonedDayBounds(browseDate, timezone);
  const startOffset = Temporal.Instant.from(bounds.start).toZonedDateTimeISO(timezone).offset;
  const endOffset = Temporal.Instant.from(bounds.end).toZonedDateTimeISO(timezone).offset;
  const clockChange = elapsed !== 1440;
  const hours = Array.from({ length: Math.floor(elapsed / 60) + 1 }, (_, index) => {
    const zoned = dayMinuteToZonedTime(browseDate, timezone, index * 60);
    return {
      minute: index * 60,
      time: zoned.toPlainTime().toString({ smallestUnit: "minute" }),
      offset: clockChange ? zoned.offset : undefined,
      nextDay: zoned.toPlainDate().toString() !== browseDate,
    };
  });
  const dayHeading = new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${browseDate}T12:00:00Z`));
  const listEvents = [...dayEvents].sort((a, b) => a.start.localeCompare(b.start) || a.id - b.id);

  const queryNotice =
    query === "loading" ? (
      <p role="status" className="text-xs text-muted-foreground">
        Loading bookings…
      </p>
    ) : query === "empty" ? (
      <p role="status" className="text-xs text-muted-foreground">
        No bookings on this day.
      </p>
    ) : query === "error" || query === "truncated" ? (
      <Alert variant="destructive" className="py-2 pr-20">
        <TriangleAlertIcon aria-hidden="true" />
        <AlertDescription className="text-xs">
          {query === "error"
            ? "Couldn't load this day's bookings. The form is unchanged."
            : "Over 1,000 bookings. This schedule would be incomplete, so none are shown."}
        </AlertDescription>
        <AlertAction>
          <Button type="button" size="xs" variant="outline" onClick={onRetry}>
            Retry
          </Button>
        </AlertAction>
      </Alert>
    ) : null;

  const draftText = resolved
    ? formatInterval(
        minuteInstant(preview?.start ?? resolved.start),
        minuteInstant(preview?.end ?? resolved.end),
        timezone,
        locale,
      )
    : "Not set";
  const moveBlocked = adjustable && !canStep("move", -1) && !canStep("move", 1);

  return (
    <section
      aria-labelledby={headingId}
      className={cn(
        "flex min-w-0 flex-col rounded-sm border bg-card text-card-foreground",
        layout === "sticky" && "sticky top-4 max-h-[calc(100dvh-2rem)]",
      )}
    >
      <header className="space-y-2 border-b p-3">
        <div className="flex items-center justify-between gap-2">
          <h2 id={headingId} className="text-sm font-semibold">
            Day schedule
          </h2>
          <ButtonGroup aria-label="Schedule view">
            {(["day", "list"] as const).map((option) => (
              <Button
                key={option}
                type="button"
                size="xs"
                variant={view === option ? "secondary" : "outline"}
                aria-pressed={view === option}
                onClick={() => setView(option)}
              >
                {option === "day" ? "Day" : "List"}
              </Button>
            ))}
          </ButtonGroup>
        </div>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            aria-label="Previous day"
            onClick={() => setBrowseDate(addCalendarDays(browseDate, -1))}
          >
            <ChevronLeftIcon />
          </Button>
          <p className="min-w-0 flex-1 text-center text-sm font-medium" aria-live="polite">
            {dayHeading}
          </p>
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            aria-label="Next day"
            onClick={() => setBrowseDate(addCalendarDays(browseDate, 1))}
          >
            <ChevronRightIcon />
          </Button>
        </div>
        <p className="text-center text-xs text-muted-foreground">
          {timezone} · UTC{startOffset}
          {startOffset !== endOffset ? ` → ${endOffset}` : ""}
          {clockChange ? ` · ${elapsed / 60}-hour day` : ""}
          {item.timezone !== timezone ? ` · slots follow ${item.timezone}` : ""}
        </p>
        {draftDay && browseDate !== draftDay ? (
          <Button type="button" size="xs" variant="outline" className="w-full" onClick={() => setBrowseDate(draftDay)}>
            <CrosshairIcon aria-hidden="true" />
            Back to draft day
          </Button>
        ) : null}
        {queryNotice}
        {view === "day" && markedCount > 0 ? (
          <p className="flex flex-wrap items-center gap-1 text-xs">
            <InfoIcon aria-hidden="true" className="size-3.5" />
            {markedCount} short or overlapping {markedCount === 1 ? "booking" : "bookings"} shown as marks.
            <Button type="button" variant="link" size="xs" className="h-auto p-0" onClick={() => setView("list")}>
              View list
            </Button>
          </p>
        ) : null}
      </header>
      <section
        ref={scrollRef}
        aria-label={view === "day" ? "Day timeline" : "Day list"}
        // biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users need to focus and scroll the day schedule.
        tabIndex={0}
        className={cn(
          "overflow-y-auto overscroll-contain outline-none focus-visible:ring-3 focus-visible:ring-ring/30",
          layout === "sticky" ? "min-h-48 flex-1" : "max-h-[28rem]",
        )}
      >
        {view === "day" ? (
          <DayCanvasView
            setCanvas={setCanvas}
            elapsed={elapsed}
            pxPerMinute={pxPerMinute}
            opening={opening}
            segment={segment}
            hours={hours}
            query={query}
            laneStyle={laneStyle}
            placed={placed}
            isMark={isMark}
            denseClusters={denseClusters}
            grid={grid}
            item={item}
            browseDate={browseDate}
            timezone={timezone}
            originalSegment={originalSegment}
            draftSegment={draftSegment}
            shown={shown}
            handles={handles}
            pointerHandlers={pointerHandlers}
            minuteLabel={minuteLabel}
          />
        ) : (
          <EventListView
            query={query}
            original={original}
            timezone={timezone}
            locale={locale}
            listEvents={listEvents}
            grid={grid}
            item={item}
            browseDate={browseDate}
            loaded={loaded}
          />
        )}
      </section>
      <div className="space-y-2 border-t p-3">
        <div className="flex flex-wrap items-baseline justify-between gap-x-2 text-sm">
          <span className="font-medium">Draft</span>
          <span className="tabular-nums">{draftText}</span>
        </div>
        <StatusLine status={shownStatus} />
        {adjustable ? (
          <div className="flex flex-wrap gap-2">
            {(["move", "start", "end"] as const).map((mode) => (
              <ButtonGroup key={mode} aria-label={MODE_LABEL[mode]}>
                <ButtonGroupText className="px-2 text-xs">{MODE_LABEL[mode]}</ButtonGroupText>
                {([-1, 1] as const).map((direction) => {
                  const available = canStep(mode, direction);
                  return (
                    <Button
                      key={direction}
                      type="button"
                      size="icon-sm"
                      variant="outline"
                      aria-label={`${MODE_LABEL[mode]} ${direction < 0 ? "earlier" : "later"}`}
                      aria-describedby={hintId}
                      aria-disabled={available ? undefined : true}
                      className="aria-disabled:opacity-50"
                      onClick={() => {
                        if (available) step(mode, direction);
                      }}
                      onKeyDown={(event) => {
                        if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
                        event.preventDefault();
                        step(mode, event.key === "ArrowUp" ? -1 : 1);
                      }}
                    >
                      {direction < 0 ? <ChevronUpIcon /> : <ChevronDownIcon />}
                    </Button>
                  );
                })}
              </ButtonGroup>
            ))}
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">{blocker}</p>
        )}
        {moveBlocked ? (
          <p className="text-xs text-muted-foreground">No slot keeps this duration. Use the time fields.</p>
        ) : null}
        <p id={hintId} className="text-xs text-muted-foreground">
          ↑/↓ adjusts by one slot · Esc cancels a drag
        </p>
        <p aria-live="polite" className="sr-only">
          {announcement}
        </p>
      </div>
    </section>
  );
}
