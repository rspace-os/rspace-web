import * as React from "react";
import { useTranslation } from "react-i18next";
import { instantToDayMinute } from "@/modules/booking/domain/bookingTime";
import { cn } from "@/modules/common/utils/cn";
import { formatMinuteWithDayOffset } from "./DayTimelineEvent";
import { DayTimelineEventCard } from "./DayTimelineEventCard";
import type { AdjustmentEdge, IsoRange } from "./useVerticalDayTimelineInteraction";
import type { layoutVerticalTimelineEvents } from "./verticalTimelineLayout";

export const PIXELS_PER_HOUR = 56;
export const PIXELS_PER_MINUTE = PIXELS_PER_HOUR / 60;
const TIME_GUTTER_WIDTH = 64;
export const MINIMUM_LANE_WIDTH = 96;
export const MINIMUM_EVENT_CARD_HEIGHT = 44;
const HANDLE_HEIGHT = 12;
const MOVE_HANDLE_MINIMUM_HEIGHT = 44;

export function clipRangeToDay(range: IsoRange, date: string, timezone: string, dayMinutes: number) {
  return {
    startMinute: Math.max(0, instantToDayMinute(range.start, date, timezone)),
    endMinute: Math.min(dayMinutes, instantToDayMinute(range.end, date, timezone)),
  };
}

type VerticalDayTimelineCanvasProps = {
  date: string;
  timezone: string;
  dayMinutes: number;
  dateHeadingId: string;
  instructionsId: string;
  canvasRef: React.Ref<HTMLDivElement>;
  setScrollerRef: (node: HTMLDivElement | null) => void;
  closedHours: readonly { startMinute: number; endMinute: number }[];
  dayEvents: ReturnType<typeof layoutVerticalTimelineEvents>;
  denseLanes: boolean;
  expandedEventId: string | null;
  openDetails: (eventId: string, expanded: boolean) => void;
  collisionBoundary: HTMLElement | null;
  portalContainer: HTMLElement | null;
  draftSegment: ReturnType<typeof clipRangeToDay> | undefined;
  draftVisible: boolean;
  activeRange: IsoRange | undefined;
  activePeriod: string | undefined;
  editingRange: IsoRange | undefined;
  directAdjustmentAllowed: boolean;
  startInteraction: (edge: AdjustmentEdge, event: React.PointerEvent<HTMLButtonElement>) => void;
  updateInteraction: React.PointerEventHandler<HTMLButtonElement>;
  finishInteraction: React.PointerEventHandler<HTMLButtonElement>;
  cancelCapturedPointer: React.PointerEventHandler<HTMLButtonElement>;
  onDraftHandleKeyDown: (edge: AdjustmentEdge, event: React.KeyboardEvent<HTMLButtonElement>) => void;
};

export function VerticalDayTimelineCanvas({
  date,
  timezone,
  dayMinutes,
  dateHeadingId,
  instructionsId,
  canvasRef,
  setScrollerRef,
  closedHours,
  dayEvents,
  denseLanes,
  expandedEventId,
  openDetails,
  collisionBoundary,
  portalContainer,
  draftSegment,
  draftVisible,
  activeRange,
  activePeriod,
  editingRange,
  directAdjustmentAllowed,
  startInteraction,
  updateInteraction,
  finishInteraction,
  cancelCapturedPointer,
  onDraftHandleKeyDown,
}: VerticalDayTimelineCanvasProps) {
  const { t } = useTranslation("booking");
  const dayHeight = (dayMinutes / 60) * PIXELS_PER_HOUR;
  const hourTicks = React.useMemo(
    () => Array.from({ length: Math.ceil(dayMinutes / 60) }, (_, index) => index),
    [dayMinutes],
  );
  const draftHeight =
    draftVisible && draftSegment ? (draftSegment.endMinute - draftSegment.startMinute) * PIXELS_PER_MINUTE : 0;
  const committedDraftSegment = editingRange ? clipRangeToDay(editingRange, date, timezone, dayMinutes) : undefined;
  const committedDraftHeight = committedDraftSegment
    ? Math.max(0, committedDraftSegment.endMinute - committedDraftSegment.startMinute) * PIXELS_PER_MINUTE
    : 0;
  const canvasMoveVisible = committedDraftHeight >= MOVE_HANDLE_MINIMUM_HEIGHT && directAdjustmentAllowed;
  // A one-hour draft at 56px/hour leaves a usable center between the 12px resize strips.
  const canvasResizeVisible = committedDraftHeight >= HANDLE_HEIGHT * 2 + 24 && directAdjustmentAllowed;

  // biome-ignore format: Preserve original JSX call sites for descendant stack replay.
  return (
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
                      {formatMinuteWithDayOffset(date, timezone, hour * 60)}
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
                        className="absolute inset-x-0 bg-muted/35"
                        style={{
                          top: segment.startMinute * PIXELS_PER_MINUTE,
                          height: (segment.endMinute - segment.startMinute) * PIXELS_PER_MINUTE,
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
                        const top = startMinute * PIXELS_PER_MINUTE;
                        const height = (endMinute - startMinute) * PIXELS_PER_MINUTE;
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
                  {draftVisible && draftSegment && activeRange && activePeriod ? (
                    <div
                      data-testid="vertical-day-timeline-draft"
                      data-timeline-window-editor
                      className="pointer-events-none absolute z-[60] overflow-hidden rounded-md border-2 border-primary bg-[color-mix(in_srgb,var(--primary)_25%,var(--background))] text-foreground shadow-lg ring-3 ring-ring/40"
                      style={{
                        top: draftSegment.startMinute * PIXELS_PER_MINUTE,
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
                        <span className="block whitespace-nowrap tabular-nums">{activePeriod}</span>
                      </span>
                      {canvasMoveVisible ? (
                        <>
                          <button
                            type="button"
                            aria-describedby={instructionsId}
                            aria-label={t("dayTimeline.vertical.moveDraft", {
                              period: activePeriod,
                            })}
                            className="pointer-events-auto absolute inset-x-0 z-10 cursor-grab touch-none bg-transparent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
                            style={canvasResizeVisible ? { top: HANDLE_HEIGHT, bottom: HANDLE_HEIGHT } : { inset: 0 }}
                            onPointerDown={(event) => startInteraction("move", event)}
                            onPointerMove={updateInteraction}
                            onPointerUp={finishInteraction}
                            onPointerCancel={cancelCapturedPointer}
                            onLostPointerCapture={cancelCapturedPointer}
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
                                          period: activePeriod,
                                        })
                                      : t("dayTimeline.vertical.resizeEnd", {
                                          period: activePeriod,
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
                                  onPointerCancel={cancelCapturedPointer}
                                  onLostPointerCapture={cancelCapturedPointer}
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
  );
}
