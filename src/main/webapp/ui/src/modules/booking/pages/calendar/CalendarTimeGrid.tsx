import { useTranslation } from "react-i18next";
import { DayTimeline } from "@/modules/booking/components/DayTimeline";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import { CalendarEventCard } from "./CalendarEventCard";
import {
  actionsFor,
  blockoutActionsFor,
  type CalendarView,
  calendarDates,
  eventsOn,
  firstOfMonth,
  formatDate,
  scrollCalendarWithArrowKeys,
  toTimelineEvent,
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
  isLoading = false,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
  today: string;
  availabilityStartMinute: number;
  availabilityEndMinute: number;
  isLoading?: boolean;
}) {
  const { t } = useTranslation("booking");
  const dates = calendarDates(date, view);
  const month = firstOfMonth(date).slice(0, 7);
  const calendarRef = useScrollToToday(date, view, today);
  return (
    <section aria-label={t("calendar.layout.time-grid")} className="p-3" aria-busy={isLoading}>
      {view === "day" ? (
        <div className="relative grid min-h-56">
          <div className="grid min-w-0" aria-hidden={isLoading || undefined} inert={isLoading}>
            <DayTimeline
              date={date}
              timezone={timezone}
              events={eventsOn(events, date, timezone).map((event) => toTimelineEvent(event, date, timezone))}
              startWindow={availabilityStartMinute}
              endWindow={availabilityEndMinute}
              showZoomControls={false}
              renderEventActions={actionsFor(events, timezone, date)}
              renderBlockoutActions={blockoutActionsFor(events, timezone, date)}
            />
          </div>
          {isLoading && <Skeleton aria-hidden="true" className="absolute inset-0 h-full w-full" />}
        </div>
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
