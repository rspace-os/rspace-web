import * as React from "react";
import { useTranslation } from "react-i18next";
import { DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { formatWallClockTime } from "@/modules/booking/domain/bookingTime";
import { Button } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import { BookingActions } from "./BookingEventActions";
import { formatDate, scrollCalendarWithArrowKeys } from "./calendarLayoutUtils";
import {
  collapseWeekGridLanes,
  layoutWeekGridDay,
  WEEK_GRID_DAY_MINUTES,
  WEEK_GRID_PIXELS_PER_HOUR,
  weekGridEventBox,
} from "./calendarWeekLayout";

const HEADER_HEIGHT = 36;
const HOUR_LABEL_CLEARANCE = 12;
const DAY_HEIGHT = (WEEK_GRID_DAY_MINUTES / 60) * WEEK_GRID_PIXELS_PER_HOUR;
const HOURS = Array.from({ length: WEEK_GRID_DAY_MINUTES / 60 - 1 }, (_, index) => index + 1);

function clampToDay(minute: number) {
  return Math.min(WEEK_GRID_DAY_MINUTES, Math.max(0, minute));
}

/** The Time grid week: seven day columns sharing one wall-clock hour axis. */
export function CalendarWeekTimeGrid({
  dates,
  events,
  timezone,
  today,
  availabilityStartMinute,
  availabilityEndMinute,
  onShowDay,
  isLoading = false,
}: {
  dates: readonly string[];
  events: readonly BookingListDocument[];
  timezone: string;
  today: string;
  availabilityStartMinute: number;
  availabilityEndMinute: number;
  /** Opens one day in full, for an overlap group too crowded to show every booking. */
  onShowDay: (day: string) => void;
  isLoading?: boolean;
}) {
  const { t } = useTranslation("booking");
  const scrollerRef = React.useRef<HTMLElement>(null);
  const [expandedEventKey, setExpandedEventKey] = React.useState<string | null>(null);
  const columns = React.useMemo(
    () => dates.map((day) => ({ day, ...collapseWeekGridLanes(layoutWeekGridDay(events, day, timezone)) })),
    [dates, events, timezone],
  );
  const windowStart = clampToDay(availabilityStartMinute);
  const windowEnd = Math.max(windowStart, clampToDay(availabilityEndMinute));
  const windowHeight = ((windowEnd - windowStart) / 60) * WEEK_GRID_PIXELS_PER_HOUR;
  const weekStart = dates[0];

  React.useLayoutEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller || !weekStart) return;
    // Open on the preferred availability window, as the day view does; the rest of the day is a scroll away.
    // The offset keeps the first hour label, centred on its line, clear of the sticky day header.
    scroller.scrollTop = Math.max(0, (windowStart / 60) * WEEK_GRID_PIXELS_PER_HOUR - HOUR_LABEL_CLEARANCE);
    const todayColumn = scroller.querySelector<HTMLElement>(`[data-calendar-date="${today}"]`);
    if (todayColumn) {
      scroller.scrollLeft = todayColumn.offsetLeft - (scroller.clientWidth - todayColumn.clientWidth) / 2;
    }
  }, [today, weekStart, windowStart]);

  return (
    <div className="relative">
      <section
        ref={scrollerRef}
        className="relative overflow-auto rounded-sm border bg-card"
        // The viewport shows the availability window by default, within the page's reach.
        style={{ height: `clamp(16rem, ${HEADER_HEIGHT + windowHeight + 2}px, 70vh)` }}
        // biome-ignore lint/a11y/noNoninteractiveTabindex: This named scroll region supports arrow-key navigation.
        tabIndex={0}
        aria-label={t("calendar.grid")}
        aria-hidden={isLoading || undefined}
        inert={isLoading}
        onKeyDown={scrollCalendarWithArrowKeys}
      >
        <div className="grid min-w-225" style={{ gridTemplateColumns: "4.5rem repeat(7, minmax(0, 1fr))" }}>
          <div className="sticky top-0 left-0 z-30 border-r border-b bg-card" style={{ height: HEADER_HEIGHT }} />
          {dates.map((day, index) => (
            <div
              key={day}
              className={cn(
                "sticky top-0 z-20 flex min-w-0 items-center border-b bg-card px-1.5",
                index < dates.length - 1 && "border-r",
              )}
              style={{ height: HEADER_HEIGHT }}
            >
              <h2 className="truncate text-xs font-semibold">
                <time dateTime={day}>{formatDate(day, { weekday: "short", day: "numeric" })}</time>
              </h2>
            </div>
          ))}
          {/* Every event names its own period, so the axis labels are visual only. */}
          <div aria-hidden="true" className="sticky left-0 z-20 border-r bg-card" style={{ height: DAY_HEIGHT }}>
            {HOURS.map((hour) => (
              <span
                key={hour}
                data-week-grid-hour={hour}
                className="absolute right-1.5 -translate-y-1/2 text-[11px] leading-none whitespace-nowrap text-muted-foreground tabular-nums"
                style={{ top: hour * WEEK_GRID_PIXELS_PER_HOUR }}
              >
                {formatWallClockTime(`${hour}:00`)}
              </span>
            ))}
          </div>
          {columns.map(({ day, visible: dayEvents, overflows }, index) => (
            <section
              key={day}
              data-calendar-date={day}
              aria-label={formatDate(day, { dateStyle: "full" })}
              className={cn("relative min-w-0", index < columns.length - 1 && "border-r")}
              style={{ height: DAY_HEIGHT }}
            >
              <div aria-hidden="true" className="pointer-events-none absolute inset-0">
                {HOURS.map((hour) => (
                  <div
                    key={hour}
                    className="absolute inset-x-0 border-t border-border/70"
                    style={{ top: hour * WEEK_GRID_PIXELS_PER_HOUR }}
                  />
                ))}
              </div>
              <ol className="absolute inset-y-0 right-1 left-0 m-0 list-none p-0">
                {dayEvents.map((item) => {
                  const key = `${day}|${item.event.id}`;
                  const expanded = expandedEventKey === key;
                  return (
                    <li
                      key={item.event.id}
                      data-event-id={item.event.id}
                      data-lane={item.lane}
                      data-lane-count={item.laneCount}
                      className={cn("absolute z-10 px-0.5 py-px", expanded && "z-[15]")}
                      style={weekGridEventBox(item)}
                    >
                      <DayTimelineEventCard
                        event={item.event}
                        date={day}
                        timezone={timezone}
                        compactCards={false}
                        fitHeight={weekGridEventBox(item).height}
                        variant="timeline"
                        expanded={expanded}
                        onExpandedChange={(open) =>
                          setExpandedEventKey((current) => (open ? key : current === key ? null : current))
                        }
                        renderEventActions={() => <BookingActions event={item.booking} timezone={timezone} />}
                        renderBlockoutActions={() => <BookingActions event={item.booking} timezone={timezone} />}
                      />
                      {item.continuesBefore ? (
                        <span
                          role="img"
                          aria-label={t("dayTimeline.vertical.continuesBefore")}
                          className="pointer-events-none absolute top-0 right-0 left-0 h-1 border-t-2 border-dashed border-current"
                        />
                      ) : null}
                      {item.continuesAfter ? (
                        <span
                          role="img"
                          aria-label={t("dayTimeline.vertical.continuesAfter")}
                          className="pointer-events-none absolute right-0 bottom-0 left-0 h-1 border-b-2 border-dashed border-current"
                        />
                      ) : null}
                    </li>
                  );
                })}
                {overflows.map((overflow) => (
                  <li
                    key={`overflow-${overflow.startMinute}`}
                    className="absolute z-10 px-0.5 py-px"
                    style={weekGridEventBox(overflow)}
                  >
                    <Button
                      variant="outline"
                      size="xs"
                      className="h-full w-full items-start rounded-sm px-1 pt-1"
                      aria-label={t("calendar.weekGrid.moreLabel", {
                        count: overflow.hiddenCount,
                        date: formatDate(day, { dateStyle: "full" }),
                      })}
                      onClick={() => onShowDay(day)}
                    >
                      {t("calendar.weekGrid.more", { count: overflow.hiddenCount })}
                    </Button>
                  </li>
                ))}
              </ol>
            </section>
          ))}
        </div>
      </section>
      {isLoading && <Skeleton aria-hidden="true" className="absolute inset-0 h-full w-full" />}
    </div>
  );
}
