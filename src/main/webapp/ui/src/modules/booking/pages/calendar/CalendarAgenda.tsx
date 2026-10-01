import { useTranslation } from "react-i18next";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { Badge } from "@/modules/common/ui/badge";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import { CalendarEventCard } from "./CalendarEventCard";
import { type CalendarView, eventsOn, formatDate, periodDates, useScrollToToday } from "./calendarLayoutUtils";

export function CalendarAgenda({
  date,
  view,
  events,
  timezone,
  today,
  isLoading = false,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
  today: string;
  isLoading?: boolean;
}) {
  const { t } = useTranslation("booking");
  const dates = periodDates(date, view);
  const dateRailRef = useScrollToToday(date, view, today);
  return (
    <section aria-label={t("calendar.layout.agenda")} className="p-3" aria-busy={isLoading}>
      <div className="grid gap-4 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside
          ref={dateRailRef}
          className="max-h-160 overflow-y-auto rounded-sm border bg-muted/20 p-2"
          aria-label={t("calendar.datesInRange")}
          // biome-ignore lint/a11y/noNoninteractiveTabindex: Safari needs the named scroll region in the tab order.
          tabIndex={0}
        >
          <ol className="space-y-1">
            {dates.map((day) => {
              const count = eventsOn(events, day, timezone).length;
              return (
                <li
                  key={day}
                  data-calendar-date={day}
                  className={cn(
                    "flex items-center justify-between gap-3 rounded-sm px-2 py-2",
                    count && "bg-background shadow-sm",
                  )}
                >
                  <time dateTime={day} className="text-sm font-medium">
                    {formatDate(day, { weekday: "short", month: "short", day: "numeric" })}
                  </time>
                  {isLoading ? (
                    <Skeleton aria-hidden="true" className="h-5 w-6" />
                  ) : (
                    <Badge variant={count ? "default" : "outline"}>{count}</Badge>
                  )}
                </li>
              );
            })}
          </ol>
        </aside>
        <section className="space-y-5" aria-label={t("calendar.agenda")}>
          {isLoading ? (
            <div aria-hidden="true" className="space-y-4">
              {[0, 1, 2, 3].map((row) => (
                <Skeleton key={row} className="h-24 w-full" />
              ))}
            </div>
          ) : (
            dates.flatMap((day) => {
              const dayEvents = eventsOn(events, day, timezone);
              if (dayEvents.length === 0) return [];
              return [
                <section key={day} className="space-y-2">
                  <h2 className="sticky top-0 z-10 border-b bg-background/95 py-2 font-semibold backdrop-blur">
                    <time dateTime={day}>{formatDate(day, { dateStyle: "full" })}</time>
                  </h2>
                  <ol className="space-y-2">
                    {dayEvents.map((event) => (
                      <li key={event.id}>
                        <CalendarEventCard event={event} date={day} timezone={timezone} />
                      </li>
                    ))}
                  </ol>
                </section>,
              ];
            })
          )}
        </section>
      </div>
    </section>
  );
}
