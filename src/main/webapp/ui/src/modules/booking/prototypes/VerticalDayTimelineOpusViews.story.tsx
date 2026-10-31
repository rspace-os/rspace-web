// PROTOTYPE ONLY (Opus 5.5). Throwaway shared code for the vertical day timeline stories; do not import from production.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { ChevronDownIcon, ChevronUpIcon } from "lucide-react";
import type * as React from "react";
import { DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import type { DayQueryFixture, FixtureEvent } from "./verticalDayTimelineOpusFixtures";
import {
  type AdjustMode,
  type EpochRange,
  formatInterval,
  instantMinute,
  type Placed,
  type SlotGrid,
  toDayEvent,
} from "./verticalDayTimelineOpusModel";

export const GUTTER_PX = 56;
export const MIN_LANE_PX = 96; // 6rem
export const MIN_TARGET_PX = 24;

function markClass(event: FixtureEvent) {
  if (event.kind === "MAINTENANCE") return "bg-amber-600";
  return event.privacy === "busy" ? "bg-slate-500" : "bg-blue-600";
}

export function DayCanvasView({
  setCanvas,
  elapsed,
  pxPerMinute,
  opening,
  segment,
  hours,
  query,
  laneStyle,
  placed,
  isMark,
  denseClusters,
  grid,
  item,
  browseDate,
  timezone,
  originalSegment,
  draftSegment,
  shown,
  handles,
  pointerHandlers,
  minuteLabel,
}: {
  setCanvas: React.RefCallback<HTMLDivElement>;
  elapsed: number;
  pxPerMinute: number;
  opening: EpochRange[];
  segment: (range: EpochRange) => { top: number; height: number } | undefined;
  hours: { minute: number; time: string; offset: string | undefined; nextDay: boolean }[];
  query: DayQueryFixture;
  laneStyle: (lane: number, lanes: number) => React.CSSProperties;
  placed: Placed<{ id: string; fixture: FixtureEvent }>[];
  isMark: (entry: Placed<{ id: string; fixture: FixtureEvent }>) => boolean;
  denseClusters: Set<number>;
  grid: SlotGrid;
  item: BookableItemOption;
  browseDate: string;
  timezone: string;
  originalSegment: { top: number; height: number } | undefined;
  draftSegment: { top: number; height: number } | undefined;
  shown: EpochRange | undefined;
  handles: "none" | "full" | "move";
  pointerHandlers: (
    mode: AdjustMode,
  ) => Pick<
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    "onPointerDown" | "onPointerMove" | "onPointerUp" | "onPointerCancel" | "onLostPointerCapture"
  >;
  minuteLabel: (minute: number) => string;
}) {
  return (
    <div ref={setCanvas} className="relative select-none" style={{ height: elapsed * pxPerMinute }}>
      <div aria-hidden="true" className="absolute inset-y-0 right-0 bg-muted" style={{ left: GUTTER_PX }} />
      {opening.map((range) => {
        const block = segment(range);
        return block ? (
          <div
            key={range.start}
            aria-hidden="true"
            className="absolute right-0 bg-card"
            style={{ left: GUTTER_PX, top: block.top, height: block.height }}
          />
        ) : null;
      })}
      {hours.map((hour) => (
        <div
          key={hour.minute}
          aria-hidden="true"
          className="absolute inset-x-0 border-t border-border"
          style={{ top: hour.minute * pxPerMinute }}
        >
          <span className="absolute top-0.5 left-1 bg-card text-[10px] leading-tight text-muted-foreground tabular-nums">
            {hour.time}
            {hour.nextDay ? " +1" : ""}
            {hour.offset ? <span className="block">{hour.offset}</span> : null}
          </span>
        </div>
      ))}
      {query === "loading" ? (
        <div aria-hidden="true">
          {[0.2, 0.45, 0.7].map((fraction) => (
            <Skeleton
              key={fraction}
              className="absolute h-16 bg-muted-foreground/20"
              style={{ top: fraction * elapsed * pxPerMinute, ...laneStyle(0, 1) }}
            />
          ))}
        </div>
      ) : null}
      {/* Occupancy marks are decorative; the list view carries their details. */}
      <div aria-hidden="true">
        {placed.filter(isMark).map((entry) => {
          const block = segment(entry);
          if (!block) return null;
          const dense = denseClusters.has(entry.cluster);
          const style = dense
            ? {
                left: `calc(${GUTTER_PX}px + (100% - ${GUTTER_PX}px) * ${entry.lane / entry.lanes})`,
                width: 6,
              }
            : laneStyle(entry.lane, entry.lanes);
          return (
            <div
              key={entry.item.id}
              className={cn("absolute rounded-full", markClass(entry.item.fixture))}
              style={{ top: block.top, height: Math.max(block.height, 3), ...style }}
            />
          );
        })}
      </div>
      <ol aria-label="Bookings on this day" className="m-0 list-none p-0">
        {placed
          .filter((entry) => !isMark(entry))
          .map((entry) => {
            const block = segment(entry);
            if (!block) return null;
            const event = entry.item.fixture;
            return (
              <li
                key={entry.item.id}
                data-event-id={entry.item.id}
                className="absolute [&>article]:max-w-full"
                style={{ top: block.top, height: block.height, ...laneStyle(entry.lane, entry.lanes) }}
              >
                <DayTimelineEventCard
                  event={toDayEvent(event, grid.dayStart, item)}
                  date={browseDate}
                  timezone={timezone}
                  variant="timeline"
                  compactCards={false}
                />
                {instantMinute(event.start) < grid.dayStart ? (
                  <ChevronUpIcon
                    aria-hidden="true"
                    className="pointer-events-none absolute -top-1 left-1/2 z-20 size-4 -translate-x-1/2 rounded-full bg-background"
                  />
                ) : null}
                {instantMinute(event.end) > grid.dayEnd ? (
                  <ChevronDownIcon
                    aria-hidden="true"
                    className="pointer-events-none absolute -bottom-1 left-1/2 z-20 size-4 -translate-x-1/2 rounded-full bg-background"
                  />
                ) : null}
              </li>
            );
          })}
      </ol>
      {/* Drafts span the booking area; the transparent wrapper lets other cards receive clicks. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 right-1" style={{ left: GUTTER_PX }}>
        {originalSegment ? (
          <div
            className="absolute inset-x-0 rounded-sm border-2 border-dashed border-muted-foreground"
            style={{ top: originalSegment.top, height: originalSegment.height }}
          >
            {originalSegment.height >= 16 ? <span className="px-1 text-[10px] font-medium">Original</span> : null}
          </div>
        ) : null}
        {draftSegment && shown ? (
          <div
            className="pointer-events-auto absolute inset-x-0 overflow-hidden rounded-sm border-2 border-primary bg-[color-mix(in_srgb,var(--primary)_25%,var(--background))] text-[11px] leading-tight text-foreground shadow-sm ring-3 ring-ring/40"
            style={{ top: draftSegment.top, height: Math.max(draftSegment.height, 3) }}
          >
            {handles === "full" ? (
              <>
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label="Drag draft start"
                  className="absolute inset-x-0 top-0 h-6 cursor-ns-resize touch-none border-b border-current/20"
                  {...pointerHandlers("start")}
                />
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label="Drag draft"
                  className="absolute inset-x-0 top-6 bottom-6 cursor-grab touch-none px-1 text-left font-medium"
                  {...pointerHandlers("move")}
                >
                  {minuteLabel(shown.start)}–{minuteLabel(shown.end)}
                </button>
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label="Drag draft end"
                  className="absolute inset-x-0 bottom-0 h-6 cursor-ns-resize touch-none border-t border-current/20"
                  {...pointerHandlers("end")}
                />
              </>
            ) : handles === "move" ? (
              <button
                type="button"
                tabIndex={-1}
                aria-label="Drag draft"
                className="absolute inset-0 cursor-grab touch-none px-1 text-left font-medium"
                {...pointerHandlers("move")}
              >
                {minuteLabel(shown.start)}
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

export function EventListView({
  query,
  original,
  timezone,
  locale,
  listEvents,
  grid,
  item,
  browseDate,
  loaded,
}: {
  query: DayQueryFixture;
  original: FixtureEvent | undefined;
  timezone: string;
  locale: string;
  listEvents: FixtureEvent[];
  grid: SlotGrid;
  item: BookableItemOption;
  browseDate: string;
  loaded: boolean;
}) {
  return query === "loading" ? (
    <div aria-hidden="true" className="space-y-2 p-3">
      {[0, 1, 2].map((row) => (
        <Skeleton key={row} className="h-12 w-full" />
      ))}
    </div>
  ) : (
    <ol aria-label="Bookings on this day" className="m-0 list-none space-y-2 p-3">
      {original ? (
        <li className="rounded-sm border-2 border-dashed border-muted-foreground p-2 text-xs">
          <span className="font-medium">Original booking</span> ·{" "}
          {formatInterval(original.start, original.end, timezone, locale)}
          <span className="block text-muted-foreground">Not counted as a conflict</span>
        </li>
      ) : null}
      {listEvents.map((event) => (
        <li key={event.id} data-event-id={String(event.id)}>
          <DayTimelineEventCard
            event={toDayEvent(event, grid.dayStart, item)}
            date={browseDate}
            timezone={timezone}
            variant="flow"
            compactCards={false}
          />
        </li>
      ))}
      {loaded && listEvents.length === 0 ? <li className="text-xs text-muted-foreground">No bookings.</li> : null}
    </ol>
  );
}
