import { useTranslation } from "react-i18next";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { Badge } from "@/modules/common/ui/badge";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import { CalendarEventCard } from "./CalendarEventCard";
import { type CalendarView, eventsOn, formatDate, periodDates, useScrollToToday } from "./calendarLayoutUtils";

const FULL_PURPOSE_LENGTH = 120;
const CONTEXT_BEFORE_MATCH = 30;
const CONTEXT_AFTER_MATCH = 90;

/**
 * The part of a visible purpose around the first match of the Calendar search, which (like the server's search) is a
 * case-insensitive substring match. Undefined when the purpose does not contain the search, e.g. a hit on the item
 * name or the booker, which the collapsed card already shows.
 */
export function purposeSearchMatch(purpose: string | null | undefined, search: string) {
  const needle = search.trim().toLowerCase();
  if (!purpose || !needle) return undefined;
  const lowered = purpose.toLowerCase();
  const index = lowered.indexOf(needle);
  if (index === -1) return undefined;
  // Lowercasing can change a string's length (e.g. "İ"); then the offsets do not map back, so show it unmarked.
  if (lowered.length !== purpose.length) {
    return { before: purpose, match: "", after: "", truncatedStart: false, truncatedEnd: false };
  }
  const start = purpose.length <= FULL_PURPOSE_LENGTH ? 0 : Math.max(0, index - CONTEXT_BEFORE_MATCH);
  const end =
    purpose.length <= FULL_PURPOSE_LENGTH
      ? purpose.length
      : Math.min(purpose.length, index + needle.length + CONTEXT_AFTER_MATCH);
  return {
    before: purpose.slice(start, index),
    match: purpose.slice(index, index + needle.length),
    after: purpose.slice(index + needle.length, end),
    truncatedStart: start > 0,
    truncatedEnd: end < purpose.length,
  };
}

function PurposeSearchMatch({ event, search }: { event: BookingListDocument; search: string }) {
  const { t } = useTranslation("booking");
  const match = event.privacy === "full" ? purposeSearchMatch(event.purpose, search) : undefined;
  if (!match) return null;
  return (
    <p
      data-calendar-search-match
      className="-mt-px flex min-w-0 gap-2 rounded-b-sm border border-t-0 bg-background px-2 py-1 text-xs leading-4"
    >
      <span className="shrink-0 font-medium">
        {t(event.kind === "MAINTENANCE" ? "dayTimeline.expanded.notes" : "dayTimeline.expanded.purpose")}
      </span>
      <span className="min-w-0 break-words">
        {match.truncatedStart ? "…" : null}
        {match.before}
        {match.match ? (
          <mark className="rounded-xs bg-yellow-200 px-0.5 text-inherit dark:bg-yellow-800">{match.match}</mark>
        ) : null}
        {match.after}
        {match.truncatedEnd ? "…" : null}
      </span>
    </p>
  );
}

export function CalendarAgenda({
  date,
  view,
  events,
  timezone,
  today,
  search = "",
  isLoading = false,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
  today: string;
  /** The applied Calendar search; a card whose purpose matches shows the match, which it otherwise hides. */
  search?: string;
  isLoading?: boolean;
}) {
  const { t } = useTranslation("booking");
  const dates = periodDates(date, view);
  const dateRailRef = useScrollToToday(date, view, today);
  return (
    <section aria-label={t("calendar.layout.agenda")} className="py-3" aria-busy={isLoading}>
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
                        <PurposeSearchMatch event={event} search={search} />
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
