import * as React from "react";
import { useTranslation } from "react-i18next";
import { DayTimeline, type DayTimelineRange } from "@/modules/booking/components/DayTimeline";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import { CalendarEventCard } from "./CalendarEventCard";
import { CalendarWeekTimeGrid } from "./CalendarWeekTimeGrid";
import { closedDayRanges } from "./calendarAvailability";
import {
  actionsFor,
  blockoutActionsFor,
  type CalendarView,
  calendarDates,
  eventsOn,
  firstOfMonth,
  formatDate,
  scrollCalendarWithArrowKeys,
  useScrollToToday,
} from "./calendarLayoutUtils";

export function CalendarTimeGrid({
  date,
  view,
  events,
  timezone,
  today,
  availabilityStartMinute,
  availabilityEndMinute,
  onShowDay,
  schedule,
  creationDisabled = false,
  onRangeSelect,
  isLoading = false,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
  today: string;
  availabilityStartMinute: number;
  availabilityEndMinute: number;
  onShowDay: (day: string) => void;
  /**
   * The one item in scope; the day view shades its closures and, when the viewer may book it, creates a booking for
   * a clicked or dragged range. Several items have no single schedule, so neither happens.
   */
  schedule?: BookableItemOption;
  creationDisabled?: boolean;
  onRangeSelect?: (item: BookableItemOption, range: DayTimelineRange, trigger: HTMLElement) => void;
  isLoading?: boolean;
}) {
  const { t } = useTranslation("booking");
  const dates = calendarDates(date, view);
  const month = firstOfMonth(date).slice(0, 7);
  const calendarRef = useScrollToToday(date, view, today);
  const closedPeriods = React.useMemo(
    () => (schedule && view === "day" ? closedDayRanges(schedule, date, timezone) : undefined),
    [schedule, date, timezone, view],
  );
  const canCreateBooking = schedule?.capabilities?.canCreateBooking === true;
  return (
    <section aria-label={t("calendar.layout.time-grid")} className="py-3" aria-busy={isLoading}>
      {view === "day" ? (
        <div className="relative grid min-h-56">
          <div className="grid min-w-0" aria-hidden={isLoading || undefined} inert={isLoading}>
            <DayTimeline
              date={date}
              timezone={timezone}
              events={eventsOn(events, date, timezone).map((event) => toTimelineEvent(event, date, timezone))}
              closedPeriods={closedPeriods}
              startWindow={availabilityStartMinute}
              endWindow={availabilityEndMinute}
              showZoomControls={false}
              renderEventActions={actionsFor(events, timezone, date)}
              renderBlockoutActions={blockoutActionsFor(events, timezone, date)}
              snapIncrementMinutes={schedule?.slotGranularityMinutes}
              creationDisabled={isLoading || creationDisabled || !canCreateBooking}
              onRangeSelect={
                canCreateBooking && schedule && onRangeSelect
                  ? (range, trigger) => onRangeSelect(schedule, range, trigger)
                  : undefined
              }
            />
          </div>
          {isLoading && <Skeleton aria-hidden="true" className="absolute inset-0 h-full w-full" />}
        </div>
      ) : view === "week" ? (
        <CalendarWeekTimeGrid
          onShowDay={onShowDay}
          dates={dates}
          events={events}
          timezone={timezone}
          today={today}
          availabilityStartMinute={availabilityStartMinute}
          availabilityEndMinute={availabilityEndMinute}
          isLoading={isLoading}
        />
      ) : (
        <section
          ref={calendarRef}
          className="overflow-x-auto rounded-sm border bg-card"
          // biome-ignore lint/a11y/noNoninteractiveTabindex: This named scroll region supports arrow-key navigation.
          tabIndex={0}
          aria-label={t("calendar.grid")}
          onKeyDown={scrollCalendarWithArrowKeys}
        >
          <div className="grid min-w-225 grid-cols-7">
            {dates.map((day) => (
              <section
                key={day}
                data-calendar-date={day}
                aria-label={formatDate(day, { dateStyle: "full" })}
                className={cn(
                  "min-h-44 border-r border-b p-1.5 last:border-r-0",
                  view === "month" && !day.startsWith(month) && "bg-muted/40 text-foreground",
                )}
              >
                <h2 className="mb-2 text-xs font-semibold">
                  <time dateTime={day}>{formatDate(day, { weekday: "short", day: "numeric" })}</time>
                </h2>
                <div className="space-y-1">
                  {isLoading ? (
                    <Skeleton aria-hidden="true" className="h-20 w-full" />
                  ) : (
                    eventsOn(events, day, timezone).map((event) => (
                      <CalendarEventCard
                        key={`${day}-${event.id}`}
                        event={event}
                        date={day}
                        timezone={timezone}
                        compact={view === "month"}
                        overlay
                      />
                    ))
                  )}
                </div>
              </section>
            ))}
          </div>
        </section>
      )}
    </section>
  );
}
